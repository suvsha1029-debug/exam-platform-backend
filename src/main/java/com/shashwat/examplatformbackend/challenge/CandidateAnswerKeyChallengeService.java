package com.shashwat.examplatformbackend.challenge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shashwat.examplatformbackend.evaluation.AnswerKeyStorageService;
import com.shashwat.examplatformbackend.evaluation.EvaluationOverrideService;
import com.shashwat.examplatformbackend.evaluation.EvaluationOverrideType;
import com.shashwat.examplatformbackend.exam.CustomCandidateFieldJson;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.student.CandidateFileStorageService;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.CandidateChallengeAssetResponse;
import static com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.ChallengeAnswerKeyView;
import static com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.ChallengeProofDownload;
import static com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.ChallengeQuestionSummary;
import static com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.CandidateChallengeResponse;

@Service
public class CandidateAnswerKeyChallengeService {
    private static final long REQUIRED_PDF_LIMIT = 10L * 1024L * 1024L;
    private static final long OPTIONAL_IMAGE_LIMIT = 2L * 1024L * 1024L;
    private static final Set<String> PDF_TYPES = Set.of("application/pdf");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final CandidateAnswerKeyChallengeRepository repository;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final CandidateFileStorageService fileStorageService;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final AnswerKeyStorageService answerKeyStorageService;
    private final EvaluationOverrideService evaluationOverrideService;
    private final ExamResponseSubmissionRepository responseSubmissionRepository;
    private final Path uploadRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CandidateAnswerKeyChallengeService(CandidateAnswerKeyChallengeRepository repository,
                                              StudentRepository studentRepository,
                                              ExamRepository examRepository,
                                              CandidateFileStorageService fileStorageService,
                                              QuestionPaperStorageService questionPaperStorageService,
                                              AnswerKeyStorageService answerKeyStorageService,
                                              EvaluationOverrideService evaluationOverrideService,
                                              ExamResponseSubmissionRepository responseSubmissionRepository,
                                              @Value("${app.upload.dir}") String uploadDir) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.fileStorageService = fileStorageService;
        this.questionPaperStorageService = questionPaperStorageService;
        this.answerKeyStorageService = answerKeyStorageService;
        this.evaluationOverrideService = evaluationOverrideService;
        this.responseSubmissionRepository = responseSubmissionRepository;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public CandidateChallengeResponse submit(String registrationId,
                                            String questionId,
                                            String displayQuestionId,
                                            String questionNumber,
                                            String noteText,
                                            MultipartFile proofPdf,
                                            List<MultipartFile> proofImages) {
        Student student = studentRepository.findByRegistrationId(registrationId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Candidate record was not found."));
        Exam exam = examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        validateWindow(exam, student);
        String cleanQuestionId = clean(questionId);
        if (cleanQuestionId.isBlank()) {
            throw new IllegalArgumentException("Question ID is required for challenge filing.");
        }
        if (servedQuestion(exam, cleanQuestionId) == null) {
            throw new IllegalArgumentException("The selected question is not part of the served question paper.");
        }
        if (repository.existsByExamCodeAndRegistrationIdAndQuestionId(exam.getExamCode(), student.getRegistrationId(), cleanQuestionId)) {
            throw new IllegalStateException("A challenge for this question has already been filed and is locked to its existing review status.");
        }
        if (proofPdf == null || proofPdf.isEmpty()) {
            throw new IllegalArgumentException("PDF proof is compulsory for challenge submission.");
        }
        String proofPdfPath = fileStorageService.storeCustomFile(student.getRegistrationId(), "challenge-" + cleanQuestionId + "-pdf", proofPdf, REQUIRED_PDF_LIMIT, PDF_TYPES);
        if (proofPdfPath == null || proofPdfPath.isBlank()) {
            throw new IllegalStateException("PDF proof could not be stored.");
        }
        List<String> imagePaths = new ArrayList<>();
        if (proofImages != null) {
            for (int index = 0; index < proofImages.size(); index++) {
                MultipartFile image = proofImages.get(index);
                if (image == null || image.isEmpty()) {
                    continue;
                }
                imagePaths.add(fileStorageService.storeCustomFile(student.getRegistrationId(), "challenge-" + cleanQuestionId + "-img-" + (index + 1), image, OPTIONAL_IMAGE_LIMIT, IMAGE_TYPES));
            }
        }
        CandidateAnswerKeyChallenge saved = repository.save(new CandidateAnswerKeyChallenge(
                exam.getExamCode(),
                student.getRegistrationId(),
                student.getFullName(),
                cleanQuestionId,
                clean(displayQuestionId),
                clean(questionNumber),
                proofPdfPath,
                clean(proofPdf.getOriginalFilename()).isBlank() ? "challenge-proof.pdf" : proofPdf.getOriginalFilename().trim(),
                writeImagePaths(imagePaths),
                clean(noteText)
        ));
        return CandidateChallengeResponse.from(saved, imagePaths);
    }

    @Transactional(readOnly = true)
    public List<CandidateChallengeResponse> candidateHistory(String registrationId) {
        Student student = studentRepository.findByRegistrationId(registrationId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Candidate record was not found."));
        return repository.findAllByExamCodeAndRegistrationIdOrderByCreatedAtDesc(student.getExamCode(), student.getRegistrationId())
                .stream()
                .map(row -> CandidateChallengeResponse.from(row, imagePaths(row)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateChallengeResponse> examChallenges(String examCode) {
        return repository.findAllByExamCodeOrderByCreatedAtDesc(examCode.trim())
                .stream()
                .map(row -> CandidateChallengeResponse.from(row, imagePaths(row)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChallengeQuestionSummary> challengeSummary(String examCode) {
        List<CandidateAnswerKeyChallenge> challenges = repository.findAllByExamCodeOrderByCreatedAtDesc(examCode.trim());
        Map<String, List<CandidateAnswerKeyChallenge>> grouped = new LinkedHashMap<>();
        for (CandidateAnswerKeyChallenge challenge : challenges) {
            grouped.computeIfAbsent(challenge.getQuestionId(), ignored -> new ArrayList<>()).add(challenge);
        }
        List<List<CandidateAnswerKeyChallenge>> ranked = grouped.values().stream()
                .sorted(Comparator
                        .<List<CandidateAnswerKeyChallenge>>comparingLong(List::size).reversed()
                        .thenComparing(rows -> rows.getFirst().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<ChallengeQuestionSummary> out = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            List<CandidateAnswerKeyChallenge> rows = ranked.get(index);
            CandidateAnswerKeyChallenge latest = rows.stream()
                    .max(Comparator.comparing(CandidateAnswerKeyChallenge::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(rows.getFirst());
            out.add(new ChallengeQuestionSummary(
                    index + 1,
                    latest.getQuestionId(),
                    firstNonBlank(latest.getDisplayQuestionId(), latest.getQuestionNumber()),
                    rows.size(),
                    rows.stream().map(CandidateAnswerKeyChallenge::getRegistrationId).filter(value -> value != null && !value.isBlank()).distinct().count(),
                    countStatus(rows, CandidateAnswerKeyChallengeStatus.PENDING),
                    countStatus(rows, CandidateAnswerKeyChallengeStatus.ACCEPTED),
                    countStatus(rows, CandidateAnswerKeyChallengeStatus.REJECTED),
                    latest.getCreatedAt(),
                    latest.getId()
            ));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public CandidateChallengeAssetResponse challengeAssets(Long id) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        Exam exam = examRepository.findByExamCode(row.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        List<String> imagePaths = imagePaths(row);
        JsonNode question = servedQuestion(exam, row.getQuestionId());
        JsonNode answer = answerKeyQuestion(exam, row.getQuestionId());
        return new CandidateChallengeAssetResponse(
                CandidateChallengeResponse.from(row, imagePaths),
                "",
                List.of(),
                reviewQuestion(row.getId(), question),
                answerKeyView(answer),
                evaluationOverrideService.active(row.getExamCode(), row.getQuestionId())
                        .map(value -> value.type().name())
                        .orElse(""),
                "/api/evaluation/challenges/" + row.getId() + "/proof-pdf",
                proofDownloads(row.getId(), imagePaths)
        );
    }

    @Transactional(readOnly = true)
    public ChallengeAssetDownload proofPdf(Long id) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        return readDownload(row.getProofPdfPath(), row.getProofPdfName(), "application/pdf");
    }

    @Transactional(readOnly = true)
    public ChallengeAssetDownload proofImage(Long id, int index) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        List<String> paths = imagePaths(row);
        if (index < 0 || index >= paths.size()) {
            throw new IllegalArgumentException("Challenge proof image was not found.");
        }
        Path path = Path.of(paths.get(index));
        return readDownload(paths.get(index), path.getFileName().toString(), "image/png");
    }

    @Transactional(readOnly = true)
    public ChallengeAssetDownload questionImage(Long id) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        Exam exam = examRepository.findByExamCode(row.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        JsonNode question = servedQuestion(exam, row.getQuestionId());
        String image = mediaImage(question,
                "question_viewer_image", "questionViewerImage", "viewer_image", "viewerImage",
                "question_display_image", "questionDisplayImage", "display_image", "displayImage",
                "question_base_image", "questionBaseImage", "base_image", "baseImage",
                "question_full_image", "questionFullImage", "full_image", "fullImage",
                "question_image", "questionImage", "image");
        return decodeDataImage(image, "question-" + row.getQuestionId() + ".png");
    }

    @Transactional(readOnly = true)
    public ChallengeAssetDownload optionImage(Long id, String optionLetter) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        Exam exam = examRepository.findByExamCode(row.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        JsonNode question = servedQuestion(exam, row.getQuestionId());
        String letter = clean(optionLetter).toUpperCase(Locale.ROOT);
        if (!letter.matches("^[A-Z]$")) {
            throw new IllegalArgumentException("Option image identifier is invalid.");
        }
        JsonNode option = questionOptions(question).get(letter);
        String image = mediaImage(option,
                "viewer_url", "viewerUrl", "viewer_image", "viewerImage",
                "option_viewer_image", "optionViewerImage",
                "display_image", "displayImage", "option_display_image", "optionDisplayImage",
                "base_image", "baseImage", "option_base_image", "optionBaseImage",
                "full_image", "fullImage", "image", "option_image", "optionImage");
        return decodeDataImage(image, "question-" + row.getQuestionId() + "-option-" + letter + ".png");
    }

    @Transactional
    public CandidateChallengeResponse resolve(Long id,
                                              String reviewer,
                                              CandidateAnswerKeyChallengeResolution resolution,
                                              String note) {
        CandidateAnswerKeyChallenge row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge record not found."));
        if (row.getStatus() != CandidateAnswerKeyChallengeStatus.PENDING) {
            throw new IllegalStateException("This challenge is already finalized and can no longer be changed.");
        }
        if (resolution == null) {
            throw new IllegalArgumentException("Choose a challenge resolution.");
        }
        String cleanNote = clean(note);
        if (cleanNote.isBlank()) {
            throw new IllegalArgumentException("A short decision reason is required for the audit ledger.");
        }
        if (resolution == CandidateAnswerKeyChallengeResolution.KEEP_CURRENT_KEY) {
            row.reject(reviewer, cleanNote);
            return CandidateChallengeResponse.from(row, imagePaths(row));
        }

        EvaluationOverrideType overrideType = resolution == CandidateAnswerKeyChallengeResolution.MARK_TO_ALL
                ? EvaluationOverrideType.MARK_TO_ALL
                : EvaluationOverrideType.DROP_QUESTION;
        evaluationOverrideService.replaceActive(
                row.getExamCode(),
                row.getQuestionId(),
                row.getDisplayQuestionId(),
                overrideType,
                cleanNote,
                reviewer
        );
        List<CandidateAnswerKeyChallenge> sameQuestion = repository.findAllByExamCodeAndQuestionIdAndStatus(
                row.getExamCode(), row.getQuestionId(), CandidateAnswerKeyChallengeStatus.PENDING);
        for (CandidateAnswerKeyChallenge pending : sameQuestion) {
            String decisionNote = pending.getId().equals(row.getId())
                    ? cleanNote
                    : cleanNote + " (Applied globally after review of challenge #" + row.getId() + ".)";
            pending.accept(reviewer, decisionNote, resolution);
        }
        return CandidateChallengeResponse.from(row, imagePaths(row));
    }

    @Transactional
    public CandidateChallengeResponse accept(Long id, String reviewer, String note) {
        return resolve(
                id,
                reviewer,
                CandidateAnswerKeyChallengeResolution.MARK_TO_ALL,
                clean(note).isBlank() ? "Challenge accepted; full marks awarded to all candidates." : note
        );
    }

    @Transactional
    public CandidateChallengeResponse reject(Long id, String reviewer, String note) {
        return resolve(
                id,
                reviewer,
                CandidateAnswerKeyChallengeResolution.KEEP_CURRENT_KEY,
                clean(note).isBlank() ? "Challenge rejected; current answer key retained." : note
        );
    }

    public Map<String, Object> timeline(Exam exam) {
        return Map.of(
                "enabled", exam.isAnswerKeyChallengeEnabled(),
                "opensAt", exam.getAnswerKeyChallengeOpensAt(),
                "closesAt", exam.getAnswerKeyChallengeClosesAt(),
                "allowedCountries", CustomCandidateFieldJson.parseStringList(exam.getAllowedCountryIso2sJson()),
                "zone", ZoneId.of("Asia/Calcutta").getId()
        );
    }

    private void validateWindow(Exam exam, Student student) {
        if (!exam.isAnswerKeyChallengeEnabled()) {
            throw new IllegalStateException("Answer-key challenge window is disabled by admin.");
        }
        if (!responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())) {
            throw new IllegalStateException("Submit the exam response before filing an answer-key challenge.");
        }
        if (!exam.isOriginalAnswerKeyReleased()) {
            throw new IllegalStateException("The answer key has not been released by admin yet.");
        }
        Instant now = Instant.now();
        Instant opensAt = exam.getAnswerKeyChallengeOpensAt();
        Instant closesAt = exam.getAnswerKeyChallengeClosesAt();
        if (opensAt != null && now.isBefore(opensAt)) {
            throw new IllegalStateException("Answer-key challenge window has not opened yet.");
        }
        if (closesAt != null && !now.isBefore(closesAt)) {
            throw new IllegalStateException("Answer-key challenge window is closed.");
        }
    }

    private List<String> imagePaths(CandidateAnswerKeyChallenge row) {
        String raw = row.getProofImagePathsJson();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (IOException exception) {
            return List.of();
        }
    }

    private JsonNode servedQuestion(Exam exam, String questionId) {
        return findQuestion(questionPaperStorageService.sanitizedPaper(exam), questionId);
    }

    private JsonNode reviewQuestion(Long challengeId, JsonNode question) {
        if (question == null) {
            return null;
        }
        ObjectNode out = objectMapper.createObjectNode();
        for (String field : List.of(
                "question_id", "question_number", "question_type", "section", "subject_name",
                "marks", "negative_marks", "option_ids")) {
            if (question.has(field)) out.set(field, question.get(field).deepCopy());
        }
        String viewer = mediaImage(question,
                "question_viewer_image", "questionViewerImage", "viewer_image", "viewerImage",
                "question_display_image", "questionDisplayImage", "display_image", "displayImage",
                "question_base_image", "questionBaseImage", "base_image", "baseImage",
                "question_full_image", "questionFullImage", "full_image", "fullImage",
                "question_image", "questionImage", "image");
        String selectable = firstText(question, "question_selectable_text", "questionSelectableText", "question_text", "questionText", "body", "text");
        if (isDataImage(viewer)) {
            out.put("question_viewer_url", "/api/evaluation/challenges/" + challengeId + "/question-image");
        }
        if (!selectable.isBlank()) out.put("question_selectable_text", selectable);
        Map<String, JsonNode> options = questionOptions(question);
        if (!options.isEmpty()) {
            ObjectNode optionViews = objectMapper.createObjectNode();
            options.forEach((letter, option) -> {
                if (option != null && option.isObject()) {
                    ObjectNode view = objectMapper.createObjectNode();
                    String optionImage = mediaImage(option,
                            "viewer_image", "viewerImage", "option_viewer_image", "optionViewerImage",
                            "display_image", "displayImage", "option_display_image", "optionDisplayImage",
                            "base_image", "baseImage", "option_base_image", "optionBaseImage",
                            "full_image", "fullImage", "image", "option_image", "optionImage");
                    String optionText = firstText(option, "selectable_text", "selectableText", "text", "label");
                    if (isDataImage(optionImage)) {
                        view.put("viewer_url", "/api/evaluation/challenges/" + challengeId + "/option-images/" + letter);
                    }
                    if (!optionText.isBlank()) view.put("selectable_text", optionText);
                    optionViews.set(letter, view);
                } else if (option != null) {
                    optionViews.set(letter, option.deepCopy());
                }
            });
            out.set("options", optionViews);
        }
        return out;
    }

    private static List<ChallengeProofDownload> proofDownloads(Long challengeId, List<String> imagePaths) {
        List<ChallengeProofDownload> out = new ArrayList<>();
        for (int index = 0; index < imagePaths.size(); index++) {
            Path path = Path.of(imagePaths.get(index));
            out.add(new ChallengeProofDownload(
                    path.getFileName().toString(),
                    "/api/evaluation/challenges/" + challengeId + "/proof-images/" + index
            ));
        }
        return out;
    }

    private ChallengeAssetDownload readDownload(String pathText, String requestedName, String fallbackType) {
        Path path = uploadRoot.resolve(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw new IllegalArgumentException("Stored challenge asset was not found.");
        }
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || contentType.isBlank()) contentType = fallbackType;
            String fileName = clean(requestedName);
            if (fileName.isBlank()) fileName = path.getFileName().toString();
            return new ChallengeAssetDownload(fileName.replaceAll("[\\r\\n\\\"]", "_"), contentType, Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not open challenge asset.");
        }
    }

    private static ChallengeAssetDownload decodeDataImage(String dataUrl, String fileName) {
        String value = clean(dataUrl);
        int separator = value.indexOf(',');
        if (!value.startsWith("data:image/") || separator < 0) {
            throw new IllegalArgumentException("Stored question image was not found.");
        }
        String metadata = value.substring(5, separator);
        String contentType = metadata.split(";", 2)[0];
        try {
            byte[] bytes = metadata.toLowerCase(Locale.ROOT).contains(";base64")
                    ? Base64.getDecoder().decode(value.substring(separator + 1))
                    : java.net.URLDecoder.decode(value.substring(separator + 1), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            return new ChallengeAssetDownload(fileName, contentType, bytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored question image is invalid.");
        }
    }

    private static long countStatus(List<CandidateAnswerKeyChallenge> rows, CandidateAnswerKeyChallengeStatus status) {
        return rows.stream().filter(row -> row.getStatus() == status).count();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private JsonNode answerKeyQuestion(Exam exam, String questionId) {
        if (exam.getAnswerKeyJsonPath() == null || exam.getAnswerKeyJsonPath().isBlank()) {
            return null;
        }
        return findQuestion(answerKeyStorageService.read(exam.getAnswerKeyJsonPath()), questionId);
    }

    private static JsonNode findQuestion(JsonNode root, String questionId) {
        if (root == null) {
            return null;
        }
        for (String arrayName : List.of("questions", "answers", "qs")) {
            JsonNode rows = root.get(arrayName);
            if (!(rows instanceof ArrayNode array)) {
                continue;
            }
            for (JsonNode row : array) {
                if (questionAliases(row).contains(clean(questionId))) {
                    return row.deepCopy();
                }
            }
        }
        return null;
    }

    private static Set<String> questionAliases(JsonNode node) {
        Set<String> aliases = new LinkedHashSet<>();
        if (node == null) {
            return aliases;
        }
        for (String field : List.of(
                "question_id", "questionId", "qid", "bank_question_id", "bankQuestionId",
                "source_question_id", "sourceQuestionId", "legacy_question_id", "legacyQuestionId", "id")) {
            String value = clean(node.path(field).asText(""));
            if (!value.isBlank()) {
                aliases.add(value);
            }
        }
        return aliases;
    }

    private static ChallengeAnswerKeyView answerKeyView(JsonNode answer) {
        if (answer == null) {
            return null;
        }
        return new ChallengeAnswerKeyView(
                firstText(answer, "question_type", "questionType", "type"),
                answer.path("marks").asDouble(0),
                answer.has("negative_marks") ? answer.path("negative_marks").asDouble(0) : answer.path("negativeMarks").asDouble(0),
                textList(answer, "correct_options", "correctOptions"),
                textList(answer, "correct_option_ids", "correctOptionIds"),
                firstText(answer, "correct_answer", "correctAnswer", "nat_answer", "natAnswer", "answer")
        );
    }

    private static List<String> textList(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode values = node.get(field);
            if (values != null && values.isArray()) {
                List<String> out = new ArrayList<>();
                values.forEach(value -> {
                    String text = clean(value.asText(""));
                    if (!text.isBlank()) out.add(text);
                });
                return out;
            }
        }
        return List.of();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = clean(node.path(field).asText(""));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String mediaImage(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            String value = clean(node.asText(""));
            return isDataImage(value) ? value : "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual() && isDataImage(value.asText(""))) {
                return value.asText().trim();
            }
            if (value.isObject()) {
                String nested = mediaImage(value,
                        "viewer_image", "viewerImage", "display_image", "displayImage",
                        "base_image", "baseImage", "full_image", "fullImage", "image");
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }
        return "";
    }

    private static boolean isDataImage(String value) {
        return clean(value).toLowerCase(Locale.ROOT).startsWith("data:image/");
    }

    private static Map<String, JsonNode> questionOptions(JsonNode question) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (question == null || question.isNull()) {
            return out;
        }
        JsonNode options = question.get("options");
        if (options != null && options.isArray()) {
            for (int index = 0; index < options.size() && index < 26; index++) {
                out.put(String.valueOf((char) ('A' + index)), options.get(index));
            }
        } else if (options != null && options.isObject()) {
            options.fields().forEachRemaining(entry -> {
                String letter = clean(entry.getKey()).toUpperCase(Locale.ROOT);
                if (letter.matches("^[A-Z]$")) {
                    out.put(letter, entry.getValue());
                }
            });
        }
        for (String letter : List.of("A", "B", "C", "D", "E", "F", "G", "H")) {
            if (out.containsKey(letter)) {
                continue;
            }
            String lower = letter.toLowerCase(Locale.ROOT);
            for (String field : List.of("option_" + lower, "option" + letter, lower, letter)) {
                JsonNode value = question.get(field);
                if (value != null && !value.isNull() && !(value.isTextual() && value.asText("").isBlank())) {
                    out.put(letter, value);
                    break;
                }
            }
        }
        return out;
    }

    private String writeImagePaths(List<String> paths) {
        try {
            return objectMapper.writeValueAsString(paths == null ? List.of() : paths);
        } catch (IOException exception) {
            return "[]";
        }
    }

    private String readImageDataUrl(String pathText) {
        Path path = uploadRoot.resolve(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            return "";
        }
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return "";
            }
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException exception) {
            return "";
        }
    }

    private String readDataUrl(String pathText, String fallbackType) {
        Path path = uploadRoot.resolve(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw new IllegalArgumentException("Stored challenge asset was not found.");
        }
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || contentType.isBlank()) {
                contentType = fallbackType;
            }
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not open challenge asset.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record ChallengeAssetDownload(String fileName, String contentType, byte[] bytes) {
    }
}
