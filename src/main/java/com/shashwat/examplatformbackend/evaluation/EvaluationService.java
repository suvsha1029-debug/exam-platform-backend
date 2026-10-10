package com.shashwat.examplatformbackend.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.common.JsonMapperFactory;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.response.ExamResponseSubmission;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.response.ExamSubmissionResponse;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvaluationService {
    private static final DateTimeFormatter EXAM_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));
    private static final String NAT_NUMBER_TOKEN = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
    private static final Pattern NAT_NUMBER_PATTERN = Pattern.compile(NAT_NUMBER_TOKEN);
    private static final Pattern NAT_SAFE_RANGE_PATTERN = Pattern.compile(
            "^\\s*(" + NAT_NUMBER_TOKEN + ")\\s*(?:to|\\.\\.|-|\\u2012|\\u2013|\\u2014|\\u2212)\\s*(" + NAT_NUMBER_TOKEN + ")\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAT_TO_RANGE_PATTERN = Pattern.compile(
            "^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(?:to|\\.\\.|–|—)\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final ObjectMapper EVALUATION_MAPPER = JsonMapperFactory.createLargeJsonMapper();
    private static final TypeReference<List<EvaluatedQuestionResponse>> EVALUATED_QUESTION_LIST = new TypeReference<>() {
    };
    private static final String STANDARD_EVALUATION = "STANDARD";
    private static final String MANUAL_EVALUATION = "MANUAL";
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final ExamResponseSubmissionRepository submissionRepository;
    private final AnswerKeyStorageService answerKeyStorageService;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final EvaluationOverrideRepository evaluationOverrideRepository;
    private final Path uploadRoot;

    public EvaluationService(StudentRepository studentRepository, ExamRepository examRepository, ExamResponseSubmissionRepository submissionRepository, AnswerKeyStorageService answerKeyStorageService, QuestionPaperStorageService questionPaperStorageService, EvaluationOverrideRepository evaluationOverrideRepository, @Value("${app.upload.dir}") String uploadDir) {
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.answerKeyStorageService = answerKeyStorageService;
        this.questionPaperStorageService = questionPaperStorageService;
        this.evaluationOverrideRepository = evaluationOverrideRepository;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public EvaluationCandidateResponse candidate(String applicationNumber) {
        String normalized = applicationNumber == null ? "" : applicationNumber.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Application number is required.");
        }
        Student student = studentRepository.findByRegistrationId(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found for application number: " + normalized));
        String examName = examRepository.findByExamCode(student.getExamCode())
                .map(exam -> exam.getName())
                .orElse("");
        return EvaluationCandidateResponse.from(student, examName);
    }

    @Transactional(readOnly = true)
    public AnswerKeySummaryResponse answerKey(String examCode) {
        Exam exam = exam(examCode);
        if (exam.getAnswerKeyJsonPath() == null) {
            return new AnswerKeySummaryResponse(exam.getExamCode(), exam.getName(), null, 0, 0, false);
        }
        JsonNode key = answerKeyStorageService.read(exam.getAnswerKeyJsonPath());
        JsonNode answers = key.path("answers");
        double maxMarks = maxMarks(exam, key, answers);
        return new AnswerKeySummaryResponse(
                exam.getExamCode(),
                exam.getName(),
                exam.getAnswerKeyOriginalName(),
                answers.isArray() ? answers.size() : 0,
                round(maxMarks),
                true
        );
    }

    @Transactional
    public List<EvaluationResultResponse> results(String examCode) {
        Exam exam = exam(examCode);
        return currentResults(exam);
    }

    @Transactional
    public List<EvaluationResultResponse> manualResults(String examCode, JsonNode uploadedKey) {
        Exam exam = exam(examCode);
        validateManualKeyAgainstActivePaper(exam, uploadedKey);
        return evaluateAll(exam, parseAnswerKey(exam, uploadedKey), MANUAL_EVALUATION);
    }

    private List<EvaluationResultResponse> currentResults(Exam exam) {
        try {
            return evaluateAll(exam, answerKey(exam), STANDARD_EVALUATION);
        } catch (IllegalArgumentException | IllegalStateException standardFailure) {
            List<EvaluationResultResponse> manual = storedManualResults(exam);
            if (manual != null) return manual;
            throw standardFailure;
        }
    }

    private List<EvaluationResultResponse> evaluateAll(Exam exam, AnswerKey key, String evaluationSource) {
        List<EvaluationResultResponse> rows = submissionRepository.findAllByExamCodeOrderBySubmittedAtDesc(exam.getExamCode())
                .stream()
                .map(submission -> evaluate(submission, exam, key, evaluationSource))
                .sorted(Comparator.comparing(EvaluationResultResponse::score).reversed())
                .toList();
        submissionRepository.flush();
        return rows;
    }

    private List<EvaluationResultResponse> storedManualResults(Exam exam) {
        List<ExamResponseSubmission> submissions = submissionRepository
                .findAllByExamCodeOrderBySubmittedAtDesc(exam.getExamCode());
        if (submissions.isEmpty() || submissions.stream().anyMatch(row ->
                !MANUAL_EVALUATION.equals(row.getEvaluationSource())
                        || row.getEvaluatedAt() == null
                        || row.getEvaluatedScore() == null
                        || row.getEvaluatedQuestionsJson() == null)) {
            return null;
        }
        return submissions.stream()
                .map(row -> storedResult(row, exam))
                .sorted(Comparator.comparing(EvaluationResultResponse::score).reversed())
                .toList();
    }

    private static EvaluationResultResponse storedResult(ExamResponseSubmission submission, Exam exam) {
        try {
            List<EvaluatedQuestionResponse> questions = EVALUATION_MAPPER.readValue(
                    submission.getEvaluatedQuestionsJson(), EVALUATED_QUESTION_LIST);
            return new EvaluationResultResponse(
                    submission.getId(), exam.getExamCode(), exam.getName(), submission.getApplicationNumber(),
                    submission.getRegistrationId(), submission.getCandidateName(), submission.getDateOfBirth(),
                    submission.getSubmittedAt(), submission.getQuestionCount(),
                    value(submission.getEvaluatedMatchedQuestionCount()), value(submission.getEvaluatedCorrectCount()),
                    value(submission.getEvaluatedWrongCount()), value(submission.getEvaluatedBlankCount()),
                    decimal(submission.getEvaluatedScore()), decimal(submission.getEvaluatedMaxMarks()),
                    decimal(submission.getEvaluatedPercentage()), questions
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Stored manual evaluation ledger is unreadable.", exception);
        }
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static double decimal(Double value) {
        return value == null ? 0 : value;
    }

    @Transactional
    public EvaluationResultResponse responseResult(String examCode, Long responseId) {
        Exam exam = exam(examCode);
        return currentResults(exam).stream()
                .filter(row -> responseId.equals(row.responseId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Response submission not found for this exam."));
    }

    @Transactional
    public ReportCardPreviewResponse reportCardPreview(String examCode, String applicationNumber) {
        Exam exam = exam(examCode);
        String normalizedApplicationNumber = text(applicationNumber, "");
        if (normalizedApplicationNumber.isBlank()) {
            throw new IllegalArgumentException("Application number is required.");
        }
        Student student = studentRepository.findByRegistrationId(normalizedApplicationNumber)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found for application number: " + normalizedApplicationNumber));
        if (!exam.getExamCode().equals(student.getExamCode())) {
            throw new IllegalArgumentException("Candidate does not belong to this exam.");
        }
        List<EvaluationResultResponse> ordered = currentResults(exam).stream()
                .sorted(Comparator.comparing(EvaluationResultResponse::score).reversed()
                        .thenComparing(EvaluationResultResponse::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        submissionRepository.flush();
        EvaluationResultResponse selected = ordered.stream()
                .filter(row -> normalizedApplicationNumber.equals(row.applicationNumber()) || normalizedApplicationNumber.equals(row.registrationId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No evaluated response found for this candidate."));
        int rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            EvaluationResultResponse row = ordered.get(index);
            if (selected.responseId().equals(row.responseId())) {
                rank = index + 1;
                break;
            }
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("ScoreCardAppearance", text(exam.getReportCardAppearanceJson(), ""));
        putPlaceholder(placeholders, "ExamCode", exam.getExamCode());
        putPlaceholder(placeholders, "ExamName", exam.getName());
        putPlaceholder(placeholders, "ApplicationNumber", selected.applicationNumber());
        putPlaceholder(placeholders, "RegistrationId", selected.registrationId());
        putPlaceholder(placeholders, "CandidateName", selected.candidateName());
        putPlaceholder(placeholders, "DateOfBirth", selected.dateOfBirth() == null ? "" : selected.dateOfBirth().toString());
        putPlaceholder(placeholders, "StreamCode", student.getStreamCode());
        putPlaceholder(placeholders, "Score", formatNumber(selected.score()));
        putPlaceholder(placeholders, "MaxMarks", formatNumber(selected.maxMarks()));
        putPlaceholder(placeholders, "Percentage", formatNumber(selected.percentage()));
        putPlaceholder(placeholders, "CorrectCount", String.valueOf(selected.correctCount()));
        putPlaceholder(placeholders, "WrongCount", String.valueOf(selected.wrongCount()));
        putPlaceholder(placeholders, "BlankCount", String.valueOf(selected.blankCount()));
        putPlaceholder(placeholders, "Rank", rank > 0 ? String.valueOf(rank) : "");
        putPlaceholder(placeholders, "CandidateCount", String.valueOf(ordered.size()));
        putPlaceholder(placeholders, "SubmittedAt", selected.submittedAt() == null ? "" : selected.submittedAt().toString());
        putPlaceholder(placeholders, "ExamConductedOn", exam.getExamStartAt() == null ? "" : EXAM_DATE_FORMAT.format(exam.getExamStartAt()));
        putPlaceholder(placeholders, "ReportCardValidity", text(exam.getReportCardValidityText(), ""));
        putPlaceholder(placeholders, "AuthorityName", text(exam.getReportCardAuthorityName(), ""));
        putPlaceholder(placeholders, "AuthorityDesignation", text(exam.getReportCardAuthorityDesignation(), ""));
        putPlaceholder(placeholders, "InstituteLogoDataUrl", text(exam.getReportCardInstituteLogoDataUrl(), ""));
        putPlaceholder(placeholders, "WatermarkLogoDataUrl", text(exam.getReportCardWatermarkLogoDataUrl(), ""));
        putPlaceholder(placeholders, "AuthoritySignatureDataUrl", text(exam.getReportCardAuthoritySignatureDataUrl(), ""));
        String photoDataUrl = imageDataUrl(student.getPhotoPath());
        String signatureDataUrl = imageDataUrl(student.getSignaturePath());
        putPlaceholder(placeholders, "PhotoDataUrl", photoDataUrl);
        putPlaceholder(placeholders, "SignatureDataUrl", signatureDataUrl);
        return new ReportCardPreviewResponse(
                exam.getExamCode(),
                exam.getName(),
                selected.applicationNumber(),
                selected.registrationId(),
                selected.candidateName(),
                selected.dateOfBirth() == null ? "" : selected.dateOfBirth().toString(),
                student.getStreamCode(),
                selected.score(),
                selected.maxMarks(),
                selected.percentage(),
                selected.correctCount(),
                selected.wrongCount(),
                selected.blankCount(),
                rank > 0 ? rank : null,
                ordered.size(),
                selected.submittedAt() == null ? "" : selected.submittedAt().toString(),
                exam.getExamStartAt() == null ? "" : EXAM_DATE_FORMAT.format(exam.getExamStartAt()),
                photoDataUrl,
                signatureDataUrl,
                text(exam.getReportCardInstituteLogoDataUrl(), ""),
                text(exam.getReportCardWatermarkLogoDataUrl(), ""),
                text(exam.getReportCardAuthoritySignatureDataUrl(), ""),
                text(exam.getReportCardAuthorityName(), ""),
                text(exam.getReportCardAuthorityDesignation(), ""),
                text(exam.getReportCardValidityText(), ""),
                placeholders
        );
    }

    private EvaluationResultResponse evaluate(ExamResponseSubmission submission, Exam exam, AnswerKey key, String evaluationSource) {
        List<Map<String, Object>> answers = sortAnswersForDisplay(ExamSubmissionResponse.parseAnswers(submission.getAnswersJson()));
        Map<String, EvaluationOverride> overrides = activeOverrides(exam.getExamCode());
        List<EvaluatedQuestionResponse> rows = new ArrayList<>();
        int correct = 0;
        int wrong = 0;
        int blank = 0;
        int matched = 0;
        double score = 0;
        double maxMarks = 0;
        for (int index = 0; index < answers.size(); index++) {
            Map<String, Object> answer = answers.get(index);
            KeyQuestion question = key.find(answer, index);
            if (question == null) {
                blank++;
                rows.add(unmatched(answer));
                continue;
            }
            matched++;
            EvaluationOverride override = overrides.get(question.questionId());
            if (override == null || override.getType() != EvaluationOverrideType.DROP_QUESTION) {
                maxMarks += question.marks();
            }
            if (override != null && override.getType() == EvaluationOverrideType.DROP_QUESTION) {
                rows.add(new EvaluatedQuestionResponse(
                        text(answer.get("section"), question.section()),
                        question.questionId(),
                        text(answer.get("displayQuestionId"), question.questionNumber()),
                        question.type(),
                        marked(answer),
                        question.correctOptions(),
                        text(answer.get("natAnswer"), ""),
                        question.correctNatAnswer(),
                        text(answer.get("status"), ""),
                        false,
                        false,
                        question.marks(),
                        question.negativeMarks(),
                        0,
                        "DROP_QUESTION"
                ));
                continue;
            }
            Evaluation evaluation = override != null && override.getType() == EvaluationOverrideType.MARK_TO_ALL
                    ? markToAll(answer, question)
                    : score(answer, question);
            if (override != null && override.getType() == EvaluationOverrideType.MARK_TO_ALL) {
                correct++;
            } else if (!evaluation.attempted()) {
                blank++;
            } else if (evaluation.correct()) {
                correct++;
            } else {
                wrong++;
            }
            score += evaluation.awarded();
            rows.add(new EvaluatedQuestionResponse(
                    text(answer.get("section"), question.section()),
                    question.questionId(),
                    text(answer.get("displayQuestionId"), question.questionNumber()),
                    question.type(),
                    evaluation.marked(),
                    question.correctOptions(),
                    text(answer.get("natAnswer"), ""),
                    question.correctNatAnswer(),
                    text(answer.get("status"), ""),
                    evaluation.attempted(),
                    evaluation.correct(),
                    question.marks(),
                    question.negativeMarks(),
                    round(evaluation.awarded()),
                    override == null ? "" : override.getType().name()
            ));
        }
        double roundedScore = round(score);
        double roundedMaxMarks = round(maxMarks);
        double roundedPercentage = maxMarks <= 0 ? 0 : round(score * 100 / maxMarks);
        submission.recordEvaluation(
                matched,
                correct,
                wrong,
                blank,
                roundedScore,
                roundedMaxMarks,
                roundedPercentage,
                evaluationSource,
                evaluationRowsJson(rows)
        );
        return new EvaluationResultResponse(
                submission.getId(),
                exam.getExamCode(),
                exam.getName(),
                submission.getApplicationNumber(),
                submission.getRegistrationId(),
                submission.getCandidateName(),
                submission.getDateOfBirth(),
                submission.getSubmittedAt(),
                submission.getQuestionCount(),
                matched,
                correct,
                wrong,
                blank,
                roundedScore,
                roundedMaxMarks,
                roundedPercentage,
                rows
        );
    }

    private static String evaluationRowsJson(List<EvaluatedQuestionResponse> rows) {
        try {
            return EVALUATION_MAPPER.writeValueAsString(rows);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist the evaluation question ledger.", exception);
        }
    }

    private static EvaluatedQuestionResponse unmatched(Map<String, Object> answer) {
        return new EvaluatedQuestionResponse(
                text(answer.get("section"), ""),
                text(answer.get("questionId"), ""),
                text(answer.get("displayQuestionId"), ""),
                text(answer.get("type"), ""),
                marked(answer),
                List.of(),
                text(answer.get("natAnswer"), ""),
                "",
                text(answer.get("status"), ""),
                !marked(answer).isEmpty() || !text(answer.get("natAnswer"), "").isBlank(),
                false,
                0,
                0,
                0,
                ""
        );
    }

    private Map<String, EvaluationOverride> activeOverrides(String examCode) {
        if (evaluationOverrideRepository == null) {
            return Map.of();
        }
        Map<String, EvaluationOverride> out = new LinkedHashMap<>();
        for (EvaluationOverride row : evaluationOverrideRepository.findAllByExamCodeAndActiveTrueOrderByCreatedAtDesc(examCode)) {
            out.putIfAbsent(row.getQuestionId(), row);
        }
        return out;
    }

    private static Evaluation markToAll(Map<String, Object> answer, KeyQuestion question) {
        String type = question.type().toUpperCase();
        if ("NAT".equals(type)) {
            String submitted = text(answer.get("natAnswer"), "");
            return new Evaluation(!submitted.isBlank(), true, question.marks(), submitted.isBlank() ? List.of("MTA") : List.of(submitted));
        }
        List<String> marked = marked(answer);
        List<String> shown = marked.isEmpty() ? List.of("MTA") : new ArrayList<>(normalizeMarkedLetters(marked, question.optionAliases()));
        return new Evaluation(!marked.isEmpty(), true, question.marks(), shown);
    }

    private Evaluation score(Map<String, Object> answer, KeyQuestion question) {
        String type = question.type().toUpperCase();
        if ("NAT".equals(type)) {
            String submitted = text(answer.get("natAnswer"), "");
            if (submitted.isBlank()) {
                return new Evaluation(false, false, 0, List.of());
            }
            boolean correct = natMatches(submitted, question.correctNatAnswer());
            return new Evaluation(true, correct, correct ? question.marks() : question.negativeMarks(), List.of(submitted));
        }
        List<String> marked = marked(answer);
        if (marked.isEmpty()) {
            return new Evaluation(false, false, 0, marked);
        }
        Set<String> markedLetters = normalizeMarkedLetters(marked, question.optionAliases());
        Set<String> correctLetters = new LinkedHashSet<>(question.correctOptions());
        boolean correct = markedLetters.equals(correctLetters);
        double awarded = correct ? question.marks() : ("MSQ".equals(type) ? 0 : question.negativeMarks());
        return new Evaluation(true, correct, awarded, new ArrayList<>(markedLetters));
    }

    private static Set<String> normalizeMarkedLetters(List<String> marked, Map<String, String> optionAliases) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : marked) {
            String clean = value == null ? "" : value.trim();
            if (clean.isBlank()) continue;
            if (clean.length() == 1 && Character.isLetter(clean.charAt(0))) {
                out.add(clean.toUpperCase());
                continue;
            }
            String letter = optionAliases.get(clean);
            if (letter != null) out.add(letter);
        }
        return out;
    }

    private static boolean natMatches(String submitted, String correct) {
        BigDecimal value = number(submitted);
        String key = canonicalNatText(correct);
        if (value == null) {
            return canonicalNatText(submitted).equalsIgnoreCase(key);
        }
        Range range = natRange(key);
        if (range != null) {
            if (value.compareTo(range.left()) >= 0 && value.compareTo(range.right()) <= 0) {
                return true;
            }
            Range magnitudeRange = magnitudeRange(range);
            BigDecimal magnitude = value.abs();
            return magnitude.compareTo(magnitudeRange.left()) >= 0 && magnitude.compareTo(magnitudeRange.right()) <= 0;
        }
        BigDecimal expected = number(key);
        return expected == null ? canonicalNatText(submitted).equalsIgnoreCase(key) : value.compareTo(expected) == 0;
    }

    private static Range natRange(String key) {
        Matcher toMatcher = NAT_SAFE_RANGE_PATTERN.matcher(key);
        if (toMatcher.matches()) {
            return range(toMatcher.group(1), toMatcher.group(2));
        }
        Matcher legacyMatcher = NAT_TO_RANGE_PATTERN.matcher(key);
        if (legacyMatcher.matches()) {
            return range(legacyMatcher.group(1), legacyMatcher.group(2));
        }
        return null;
    }

    private static Range range(String left, String right) {
        BigDecimal low = number(left);
        BigDecimal high = number(right);
        if (low == null || high == null) {
            return null;
        }
        return low.compareTo(high) <= 0 ? new Range(low, high) : new Range(high, low);
    }

    private static Range magnitudeRange(Range range) {
        BigDecimal left = range.left().abs();
        BigDecimal right = range.right().abs();
        return left.compareTo(right) <= 0 ? new Range(left, right) : new Range(right, left);
    }

    private AnswerKey answerKey(Exam exam) {
        JsonNode key = answerKeyStorageService.read(exam.getAnswerKeyJsonPath());
        ensurePaperKeyAuthMatches(exam, key);
        return parseAnswerKey(exam, key);
    }

    private AnswerKey parseAnswerKey(Exam exam, JsonNode key) {
        JsonNode answers = key.path("answers");
        Map<String, JsonNode> paperByAlias = Map.of();
        JsonNode paperQuestions = null;
        if (exam.getQuestionPaperJsonPath() != null && !exam.getQuestionPaperJsonPath().isBlank()) {
            JsonNode paper = questionPaperStorageService.sanitizedPaper(exam);
            if (paper.path("questions").isArray()) {
                paperQuestions = paper.path("questions");
                paperByAlias = uniqueNodesByQuestionAlias(paperQuestions, "question paper");
            }
        }
        Map<String, KeyQuestion> byQuestionId = new LinkedHashMap<>();
        Map<String, KeyQuestion> byNumber = new LinkedHashMap<>();
        List<KeyQuestion> ordered = new ArrayList<>();
        if (answers.isArray() && paperQuestions != null) {
            Map<String, JsonNode> keyByAlias = uniqueNodesByQuestionAlias(answers, "answer key");
            for (JsonNode paperQuestion : paperQuestions) {
                JsonNode keyNode = findByAlias(keyByAlias, questionAliases(paperQuestion));
                if (keyNode == null) continue;
                KeyQuestion question = keyQuestion(keyNode, paperQuestion);
                ordered.add(question);
                addQuestionKeyAliases(byQuestionId, keyNode, question);
                addQuestionKeyAliases(byQuestionId, paperQuestion, question);
                if (!question.questionNumber().isBlank()) byNumber.put(question.questionNumber(), question);
            }
        } else if (answers.isArray()) {
            for (JsonNode node : answers) {
                JsonNode paperQuestion = findByAlias(paperByAlias, questionAliases(node));
                KeyQuestion question = keyQuestion(node, paperQuestion);
                ordered.add(question);
                addQuestionKeyAliases(byQuestionId, node, question);
                if (paperQuestion != null) addQuestionKeyAliases(byQuestionId, paperQuestion, question);
                if (!question.questionNumber().isBlank()) byNumber.put(question.questionNumber(), question);
            }
        }
        double effectiveMaxMarks = paperQuestions == null
                ? maxMarks(exam, key, answers)
                : ordered.stream().mapToDouble(KeyQuestion::marks).sum();
        return new AnswerKey(effectiveMaxMarks, byQuestionId, byNumber, ordered);
    }

    private static void addQuestionKeyAliases(Map<String, KeyQuestion> byQuestionId, JsonNode node, KeyQuestion question) {
        for (String value : List.of(
                node.path("question_id").asText(""),
                node.path("questionId").asText(""),
                node.path("bank_question_id").asText(""),
                node.path("bankQuestionId").asText(""),
                node.path("source_question_id").asText(""),
                node.path("sourceQuestionId").asText(""),
                node.path("legacy_question_id").asText(""),
                node.path("legacyQuestionId").asText(""),
                node.path("export_question_alias").asText(""),
                node.path("exportQuestionAlias").asText(""),
                question.questionId()
        )) {
            String clean = text(value, "");
            if (!clean.isBlank()) {
                byQuestionId.putIfAbsent(clean, question);
            }
        }
    }

    private static KeyQuestion keyQuestion(JsonNode node, JsonNode paperQuestion) {
        Map<String, String> optionIds = new LinkedHashMap<>();
        JsonNode activeQuestion = paperQuestion == null ? node : paperQuestion;
        addOptionIdMap(optionIds, firstNode(activeQuestion, "option_ids", "optionIds"));

        Map<String, Set<String>> keyAliasesByLetter = optionAliasesByLetter(node);
        Map<String, Set<String>> activeAliasesByLetter = optionAliasesByLetter(activeQuestion);
        Set<String> correctAliases = correctOptionAliases(node, keyAliasesByLetter);
        List<String> correct = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : activeAliasesByLetter.entrySet()) {
            if (intersects(entry.getValue(), correctAliases)) correct.add(entry.getKey());
        }
        if (correct.isEmpty()) correct.addAll(correctOptionLetters(node, keyAliasesByLetter));

        Map<String, String> optionAliases = optionAliasToLetter(node);
        optionAliases.putAll(optionAliasToLetter(activeQuestion));
        return new KeyQuestion(
                firstNonBlank(firstText(activeQuestion, "section", "sectionName"), firstText(node, "section", "sectionName")),
                stableQuestionId(activeQuestion),
                firstNonBlank(
                        firstText(activeQuestion, "question_number", "questionNumber", "display_question_id", "displayQuestionId"),
                        firstText(node, "question_number", "questionNumber", "display_question_id", "displayQuestionId")
                ),
                firstNonBlank(firstText(activeQuestion, "question_type", "questionType", "type"),
                        firstNonBlank(firstText(node, "question_type", "questionType", "type"), "MCQ")),
                firstNode(activeQuestion, "marks", "positive_marks", "positiveMarks")
                        .asDouble(firstNode(node, "marks", "positive_marks", "positiveMarks").asDouble(0)),
                firstNode(activeQuestion, "negative_marks", "negativeMarks")
                        .asDouble(firstNode(node, "negative_marks", "negativeMarks").asDouble(0)),
                correct,
                firstText(node, "correct_answer", "correctAnswer", "answer"),
                optionIds,
                optionAliases
        );
    }

    private void validateManualKeyAgainstActivePaper(Exam exam, JsonNode key) {
        if (exam.getQuestionPaperJsonPath() == null || exam.getQuestionPaperJsonPath().isBlank()) {
            throw new IllegalStateException("Question paper is not uploaded for this exam.");
        }
        if (key == null || !key.isObject() || !key.path("answers").isArray()) {
            throw new IllegalArgumentException("Manual answer key must contain an answers array.");
        }
        JsonNode paper = questionPaperStorageService.sanitizedPaper(exam);
        JsonNode questions = paper.path("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            throw new IllegalStateException("The active question paper has no questions.");
        }

        Map<String, JsonNode> keyByAlias = uniqueNodesByQuestionAlias(key.path("answers"), "answer key");

        for (JsonNode paperQuestion : questions) {
            String questionId = stableQuestionId(paperQuestion);
            JsonNode keyQuestion = findByAlias(keyByAlias, questionAliases(paperQuestion));
            if (keyQuestion == null) {
                throw new IllegalArgumentException("Manual key is missing active paper question " + questionId + ".");
            }
            String paperType = firstNonBlank(firstText(paperQuestion, "question_type", "questionType", "type"), "MCQ").toUpperCase();
            String keyType = firstNonBlank(firstText(keyQuestion, "question_type", "questionType", "type"), paperType).toUpperCase();
            if (!paperType.equals(keyType)) {
                throw new IllegalArgumentException("Question type mismatch for " + questionId + ": paper=" + paperType + ", key=" + keyType + ".");
            }
            if ("NAT".equals(paperType)) {
                String expected = firstText(keyQuestion, "correct_answer", "correctAnswer", "answer");
                if (expected.isBlank() || (number(expected) == null && natRange(expected) == null)) {
                    throw new IllegalArgumentException("NAT answer for " + questionId + " must be an exact signed number or an inclusive range.");
                }
                continue;
            }
            Map<String, Set<String>> paperOptions = optionAliasesByLetter(paperQuestion);
            Map<String, Set<String>> keyOptions = optionAliasesByLetter(keyQuestion);
            if (paperOptions.isEmpty()) {
                throw new IllegalArgumentException("Question " + questionId + " has no stable option IDs in the active paper.");
            }
            Set<String> correctAliases = correctOptionAliases(keyQuestion, keyOptions);
            boolean aliasesMatch = !correctAliases.isEmpty()
                    && paperOptions.values().stream().anyMatch(aliases -> intersects(aliases, correctAliases));
            if (!aliasesMatch) {
                throw new IllegalArgumentException("Correct option IDs are missing or invalid for " + questionId + ".");
            }
        }
    }

    private static Map<String, JsonNode> uniqueNodesByQuestionAlias(JsonNode nodes, String source) {
        Map<String, JsonNode> byPrimaryId = new LinkedHashMap<>();
        Map<String, LinkedHashSet<JsonNode>> secondaryCandidates = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String primaryId = primaryQuestionId(node);
            if (primaryId.isBlank()) {
                primaryId = stableQuestionId(node);
            }
            if (primaryId.isBlank()) {
                throw new IllegalArgumentException("Every " + source + " row must have a stable global question ID.");
            }
            if (byPrimaryId.putIfAbsent(primaryId, node) != null) {
                throw new IllegalArgumentException("Duplicate question ID in " + source + ": " + primaryId + ".");
            }
            Set<String> aliases = questionAliases(node);
            for (String alias : aliases) {
                if (!alias.equals(primaryId)) {
                    secondaryCandidates.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(node);
                }
            }
        }
        Map<String, JsonNode> byAlias = new LinkedHashMap<>(byPrimaryId);
        secondaryCandidates.forEach((alias, candidates) -> {
            if (!byPrimaryId.containsKey(alias) && candidates.size() == 1) {
                byAlias.put(alias, candidates.iterator().next());
            }
        });
        return byAlias;
    }

    private static JsonNode findByAlias(Map<String, JsonNode> byAlias, Set<String> aliases) {
        JsonNode found = null;
        for (String alias : aliases) {
            JsonNode candidate = byAlias.get(alias);
            if (candidate == null) continue;
            if (found != null && found != candidate) throw new IllegalArgumentException("Ambiguous question aliases in uploaded answer key.");
            found = candidate;
        }
        return found;
    }

    private static Set<String> questionAliases(JsonNode node) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String field : List.of("question_id", "questionId", "bank_question_id", "bankQuestionId",
                "source_question_id", "sourceQuestionId", "legacy_question_id", "legacyQuestionId",
                "export_question_alias", "exportQuestionAlias", "global_question_id", "globalQuestionId", "id")) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) aliases.add(value);
        }
        return aliases;
    }

    private static Map<String, JsonNode> uniqueNodesByQuestionId(JsonNode nodes, String source) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = stableQuestionId(node);
            if (id.isBlank()) {
                throw new IllegalArgumentException("Every " + source + " row must have a stable global question ID.");
            }
            if (byId.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("Duplicate question ID in " + source + ": " + id + ".");
            }
        }
        return byId;
    }

    private static void addOptionIdMap(Map<String, String> ids, JsonNode mapped) {
        if (mapped == null || !mapped.isObject()) return;
        mapped.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText("").trim();
            if (!value.isBlank()) ids.putIfAbsent(entry.getKey().toUpperCase(), value);
        });
    }

    private static Map<String, Set<String>> optionAliasesByLetter(JsonNode question) {
        Map<String, Set<String>> aliases = new LinkedHashMap<>();
        if (question == null || question.isMissingNode() || question.isNull()) return aliases;
        for (String field : List.of("option_ids", "optionIds", "source_option_ids", "sourceOptionIds",
                "bank_option_ids", "bankOptionIds", "legacy_option_ids", "legacyOptionIds")) {
            JsonNode mapped = question.get(field);
            if (mapped == null || !mapped.isObject()) continue;
            mapped.fields().forEachRemaining(entry -> {
                String id = entry.getValue().asText("").trim();
                if (!id.isBlank()) aliases.computeIfAbsent(entry.getKey().toUpperCase(), ignored -> new LinkedHashSet<>()).add(id);
            });
        }
        JsonNode options = question.path("options");
        if (options.isArray()) {
            int index = 0;
            for (JsonNode option : options) {
                String letter = firstNonBlank(firstText(option, "letter", "label", "key"), String.valueOf((char) ('A' + index))).toUpperCase();
                for (String idField : List.of("option_id", "optionId", "source_option_id", "sourceOptionId",
                        "bank_option_id", "bankOptionId", "legacy_option_id", "legacyOptionId", "id")) {
                    String id = option.path(idField).asText("").trim();
                    if (!id.isBlank()) aliases.computeIfAbsent(letter, ignored -> new LinkedHashSet<>()).add(id);
                }
                index++;
            }
        }
        return aliases;
    }

    private static Map<String, String> optionAliasToLetter(JsonNode question) {
        Map<String, String> aliases = new LinkedHashMap<>();
        optionAliasesByLetter(question).forEach((letter, ids) -> ids.forEach(id -> aliases.put(id, letter)));
        return aliases;
    }

    private static Set<String> correctOptionLetters(JsonNode keyQuestion, Map<String, Set<String>> optionAliases) {
        Set<String> letters = new LinkedHashSet<>();
        firstNode(keyQuestion, "correct_options", "correctOptions").forEach(value -> {
            String letter = value.asText("").trim().toUpperCase();
            if (!letter.isBlank()) letters.add(letter);
        });
        Map<String, String> aliasToLetter = new LinkedHashMap<>();
        optionAliases.forEach((letter, ids) -> ids.forEach(id -> aliasToLetter.put(id, letter)));
        for (String field : List.of("correct_option_ids", "correctOptionIds", "source_correct_option_ids",
                "sourceCorrectOptionIds", "bank_correct_option_ids", "bankCorrectOptionIds",
                "legacy_correct_option_ids", "legacyCorrectOptionIds")) {
            JsonNode values = keyQuestion.get(field);
            if (values == null || values.isNull()) continue;
            if (values.isArray()) values.forEach(value -> {
                String letter = aliasToLetter.get(value.asText("").trim());
                if (letter != null) letters.add(letter);
            });
            else {
                String letter = aliasToLetter.get(values.asText("").trim());
                if (letter != null) letters.add(letter);
            }
        }
        return letters;
    }

    private static Set<String> correctOptionAliases(JsonNode keyQuestion, Map<String, Set<String>> optionAliases) {
        Set<String> ids = new LinkedHashSet<>();
        for (String letter : correctOptionLetters(keyQuestion, optionAliases)) {
            ids.addAll(optionAliases.getOrDefault(letter, Set.of()));
        }
        for (String field : List.of("correct_option_ids", "correctOptionIds", "source_correct_option_ids",
                "sourceCorrectOptionIds", "bank_correct_option_ids", "bankCorrectOptionIds",
                "legacy_correct_option_ids", "legacyCorrectOptionIds")) {
            JsonNode values = keyQuestion.get(field);
            if (values == null || values.isNull()) continue;
            if (values.isArray()) values.forEach(value -> {
                String id = value.asText("").trim();
                if (!id.isBlank()) ids.add(id);
            });
            else {
                String id = values.asText("").trim();
                if (!id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        for (String value : left) if (right.contains(value)) return true;
        return false;
    }

    private static String stableQuestionId(JsonNode node) {
        return firstText(node, "question_id", "questionId", "bank_question_id", "bankQuestionId",
                "source_question_id", "sourceQuestionId", "legacy_question_id", "legacyQuestionId",
                "export_question_alias", "exportQuestionAlias", "id");
    }

    private static String primaryQuestionId(JsonNode node) {
        return firstText(node, "question_id", "questionId", "global_question_id", "globalQuestionId",
                "export_question_alias", "exportQuestionAlias", "id");
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) return "";
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static JsonNode firstNode(JsonNode node, String... fields) {
        if (node != null) {
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value != null && !value.isMissingNode() && !value.isNull()) return value;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private void ensurePaperKeyAuthMatches(Exam exam, JsonNode key) {
        if (exam.getQuestionPaperJsonPath() == null || exam.getQuestionPaperJsonPath().isBlank()) {
            throw new IllegalStateException("Question paper is not uploaded for this exam. Clear stale files and upload the assembled paper before evaluation.");
        }
        JsonNode paper = questionPaperStorageService.sanitizedPaper(exam);
        ensureUploadedKeyLicenseMatches(paper, key);
        Map<String, JsonNode> keyByAlias = uniqueNodesByQuestionAlias(key.path("answers"), "answer key");
        for (JsonNode question : paper.path("questions")) {
            if (findByAlias(keyByAlias, questionAliases(question)) == null) {
                throw new IllegalStateException("Answer key is missing active paper question " + stableQuestionId(question) + ".");
            }
        }
    }

    private static void ensureUploadedKeyLicenseMatches(JsonNode paper, JsonNode key) {
        Set<String> paperClaims = licenseClaims(paper, "license_key", "licenseKey", "paper_license_key", "paperLicenseKey",
                "source_paper_license_key", "sourcePaperLicenseKey", "source_license_key", "sourceLicenseKey",
                "paired_paper_license_key", "pairedPaperLicenseKey");
        Set<String> keyClaims = licenseClaims(key, "license_key", "licenseKey", "paper_license_key", "paperLicenseKey",
                "source_paper_license_key", "sourcePaperLicenseKey", "source_license_key", "sourceLicenseKey",
                "paired_paper_license_key", "pairedPaperLicenseKey");
        if (!paperClaims.isEmpty() && !keyClaims.isEmpty() && !intersects(paperClaims, keyClaims)) {
            throw new IllegalArgumentException("Answer key licence does not match the active paper or its protected source lineage.");
        }

        Set<String> expectedKeyClaims = licenseClaims(paper, "paired_answer_key_license_key", "pairedAnswerKeyLicenseKey");
        Set<String> actualKeyClaims = licenseClaims(key, "answer_key_license_key", "answerKeyLicenseKey",
                "answer_key_instance_key", "answerKeyInstanceKey", "paired_answer_key_license_key", "pairedAnswerKeyLicenseKey");
        if (!expectedKeyClaims.isEmpty() && !actualKeyClaims.isEmpty() && !intersects(expectedKeyClaims, actualKeyClaims)) {
            throw new IllegalArgumentException("Answer key instance does not match the paper's paired master key.");
        }
    }

    private static Set<String> licenseClaims(JsonNode node, String... fields) {
        Set<String> claims = new LinkedHashSet<>();
        if (node == null) return claims;
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) claims.add(value);
        }
        return claims;
    }

    private static Set<String> paperQuestionIds(JsonNode paper) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode questions = paper.path("questions");
        if (questions.isArray()) {
            for (JsonNode question : questions) {
                String id = firstNonBlank(
                        question.path("question_id").asText(""),
                        question.path("questionId").asText(""),
                        question.path("bank_question_id").asText(""),
                        question.path("bankQuestionId").asText(""),
                        question.path("source_question_id").asText(""),
                        question.path("sourceQuestionId").asText(""),
                        question.path("legacy_question_id").asText(""),
                        question.path("legacyQuestionId").asText(""),
                        question.path("export_question_alias").asText(""),
                        question.path("exportQuestionAlias").asText(""),
                        question.path("id").asText("")
                );
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static Set<String> keyQuestionIds(JsonNode key) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode answers = key.path("answers");
        if (answers.isArray()) {
            for (JsonNode answer : answers) {
                String id = firstNonBlank(
                        answer.path("question_id").asText(""),
                        answer.path("questionId").asText(""),
                        answer.path("bank_question_id").asText(""),
                        answer.path("bankQuestionId").asText(""),
                        answer.path("source_question_id").asText(""),
                        answer.path("sourceQuestionId").asText(""),
                        answer.path("legacy_question_id").asText(""),
                        answer.path("legacyQuestionId").asText(""),
                        answer.path("export_question_alias").asText(""),
                        answer.path("exportQuestionAlias").asText(""),
                        answer.path("id").asText("")
                );
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static String previewIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return "-";
        }
        List<String> list = new ArrayList<>(ids);
        int limit = Math.min(6, list.size());
        String text = String.join(", ", list.subList(0, limit));
        return list.size() > limit ? text + " +" + (list.size() - limit) + " more" : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String clean = text(value, "");
            if (!clean.isBlank()) {
                return clean;
            }
        }
        return "";
    }

    private static double maxMarks(Exam exam, JsonNode key, JsonNode answers) {
        if (exam.getAnswerKeyTotalMarks() != null && exam.getAnswerKeyTotalMarks() > 0) {
            return exam.getAnswerKeyTotalMarks();
        }
        return key.path("total_marks").asDouble(sumMarks(answers));
    }

    private Exam exam(String examCode) {
        return examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
    }

    private static double sumMarks(JsonNode answers) {
        double total = 0;
        if (answers != null && answers.isArray()) {
            for (JsonNode answer : answers) {
                total += answer.path("marks").asDouble(0);
            }
        }
        return total;
    }

    private static List<String> marked(Map<String, Object> answer) {
        Object raw = answer.get("markedOptionIds");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
    }

    private static String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static BigDecimal number(String value) {
        try {
            String normalized = canonicalNatText(value);
            return normalized.isBlank() ? null : new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String canonicalNatText(Object value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replace('\u2212', '-');
    }

    private static List<Map<String, Object>> sortAnswersForDisplay(List<Map<String, Object>> answers) {
        List<Map<String, Object>> sorted = new ArrayList<>(answers);
        sorted.sort((left, right) -> Integer.compare(displayOrder(left), displayOrder(right)));
        return sorted;
    }

    private static int displayOrder(Map<String, Object> answer) {
        String displayId = text(answer.get("displayQuestionId"), "");
        if (displayId.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String digits = displayId.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String imageDataUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        try {
            Path file = uploadRoot.resolve(relativePath).toAbsolutePath().normalize();
            if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
                return "";
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null || !contentType.startsWith("image/")) {
                return "";
            }
            return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void putPlaceholder(Map<String, String> placeholders, String key, String value) {
        placeholders.put(key, value == null ? "" : value);
    }

    private static String formatNumber(double value) {
        return round(value) == Math.rint(round(value))
                ? String.valueOf((long) round(value))
                : String.valueOf(round(value));
    }

    private record Evaluation(boolean attempted, boolean correct, double awarded, List<String> marked) {
    }

    private record Range(BigDecimal left, BigDecimal right) {
    }

    private record KeyQuestion(String section, String questionId, String questionNumber, String type, double marks, double negativeMarks,
                               List<String> correctOptions, String correctNatAnswer, Map<String, String> optionIds,
                               Map<String, String> optionAliases) {
    }

    private record AnswerKey(double maxMarks, Map<String, KeyQuestion> byQuestionId, Map<String, KeyQuestion> byNumber, List<KeyQuestion> ordered) {
        KeyQuestion find(Map<String, Object> answer, int index) {
            for (String field : List.of(
                    "questionId", "question_id", "bank_question_id", "bankQuestionId",
                    "source_question_id", "sourceQuestionId", "legacy_question_id", "legacyQuestionId",
                    "export_question_alias", "exportQuestionAlias", "id")) {
                String questionId = text(answer.get(field), "");
                if (byQuestionId.containsKey(questionId)) return byQuestionId.get(questionId);
            }
            return null;
        }
    }
}
