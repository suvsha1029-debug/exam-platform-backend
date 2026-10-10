package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.common.PageResponse;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.student.CandidateExamLoginRequest;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionService;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExamResponseSubmissionService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExamResponseSubmissionRepository submissionRepository;
    private final ExamResponseDraftRepository draftRepository;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final ExamAttemptSessionService attemptSessionService;

    public ExamResponseSubmissionService(ExamResponseSubmissionRepository submissionRepository, ExamResponseDraftRepository draftRepository, StudentRepository studentRepository, ExamRepository examRepository, QuestionPaperStorageService questionPaperStorageService, ExamAttemptSessionService attemptSessionService) {
        this.submissionRepository = submissionRepository;
        this.draftRepository = draftRepository;
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.questionPaperStorageService = questionPaperStorageService;
        this.attemptSessionService = attemptSessionService;
    }

    @Transactional
    public ExamSubmitResponse submit(ExamSubmitRequest request) {
        Student student = findCandidateForSubmission(request);
        Exam exam = linkedExam(student);
        if (!exam.isExamLobbyEnabled() || exam.getQuestionPaperJsonPath() == null) {
            throw new IllegalStateException("Exam paper is not available for submission.");
        }
        var existingSubmission = submissionRepository.findByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId());
        if (existingSubmission.isPresent()) {
            ExamResponseSubmission saved = existingSubmission.get();
            return new ExamSubmitResponse(
                    saved.getRegistrationId(),
                    saved.getExamCode(),
                    saved.getSubmittedAt(),
                    saved.getQuestionCount(),
                    "Exam response was already saved for this application number."
            );
        }
        attemptSessionService.verify(request.examSessionToken(), exam.getExamCode(), student.getRegistrationId());
        List<ExamAnswerRequest> answers = canonicalAnswers(request.answers(), exam);
        ExamResponseSubmission submission = new ExamResponseSubmission(
                exam.getExamCode(),
                student.getRegistrationId(),
                student.getRegistrationId(),
                student.getFullName(),
                student.getDateOfBirth()
        );
        submission.replaceAnswers(toJson(answers), answers.size());
        ExamResponseSubmission saved = submissionRepository.saveAndFlush(submission);
        attemptSessionService.markSubmitted(request.examSessionToken(), exam.getExamCode(), student.getRegistrationId());
        draftRepository.deleteAllByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId());
        draftRepository.flush();
        return new ExamSubmitResponse(
                saved.getRegistrationId(),
                saved.getExamCode(),
                saved.getSubmittedAt(),
                saved.getQuestionCount(),
                "Exam response saved successfully."
        );
    }

    @Transactional
    public ExamSubmitResponse saveDraft(ExamSubmitRequest request) {
        Student student = findCandidateForSubmission(request);
        Exam exam = linkedExam(student);
        if (!exam.isExamLobbyEnabled() || exam.getQuestionPaperJsonPath() == null) {
            throw new IllegalStateException("Exam paper is not available for saving.");
        }
        var existingSubmission = submissionRepository.findByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId());
        if (existingSubmission.isPresent()) {
            ExamResponseSubmission saved = existingSubmission.get();
            return new ExamSubmitResponse(
                    saved.getRegistrationId(),
                    saved.getExamCode(),
                    saved.getSubmittedAt(),
                    saved.getQuestionCount(),
                    "Final response is already saved."
            );
        }
        attemptSessionService.verify(request.examSessionToken(), exam.getExamCode(), student.getRegistrationId());
        List<ExamAnswerRequest> answers = canonicalAnswers(request.answers(), exam);
        ExamResponseDraft draft = draftRepository.findByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())
                .orElseGet(() -> new ExamResponseDraft(exam.getExamCode(), student.getRegistrationId()));
        draft.replaceAnswers(toJson(answers), answers.size());
        draftRepository.save(draft);
        return new ExamSubmitResponse(
                student.getRegistrationId(),
                exam.getExamCode(),
                java.time.Instant.now(),
                answers.size(),
                "Draft saved."
        );
    }

    @Transactional(readOnly = true)
    public ExamSubmissionResponse candidateSubmission(CandidateExamLoginRequest request) {
        Student student = findCandidateForExam(request.registrationId(), request.dateOfBirth());
        Exam exam = linkedExam(student);
        if (!exam.isResponsesReleased()) {
            throw new IllegalStateException("Responses are not released by admin yet.");
        }
        return submissionRepository.findByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())
                .map(submission -> toResponse(submission, exam))
                .orElseThrow(() -> new IllegalStateException("No saved response sheet found for this candidate."));
    }

    @Transactional(readOnly = true)
    public List<ExamSubmissionResponse> list() {
        return submissionRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamSubmissionResponse> listPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(
                submissionRepository.findAllBy(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "submittedAt")))
                        .map(this::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public List<ExamSubmissionResponse> listByExam(String examCode) {
        return submissionRepository.findAllByExamCodeOrderBySubmittedAtDesc(examCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamSubmissionResponse> listByExamPage(String examCode, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(
                submissionRepository.findAllByExamCode(examCode, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "submittedAt")))
                        .map(this::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public ExamSubmissionResponse get(Long id) {
        return submissionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Response submission not found."));
    }

    @Transactional
    public void delete(Long id) {
        throw new IllegalStateException("Direct response deletion is disabled. Send a re-exam request for master-admin approval.");
    }

    private Student findCandidateForExam(String registrationId, String dateOfBirth) {
        LocalDate dob = parseDateOfBirth(dateOfBirth);
        return studentRepository.findByRegistrationId(registrationId.trim())
                .filter(candidate -> dob.equals(candidate.getDateOfBirth()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID or date of birth."));
    }

    private Student findCandidateForSubmission(ExamSubmitRequest request) {
        if (request.examSessionToken() != null && !request.examSessionToken().isBlank()) {
            String requestedRegistrationId = request.registrationId() == null ? "" : request.registrationId().trim();
            Student requested = studentRepository.findByRegistrationId(requestedRegistrationId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID."));
            Exam exam = linkedExam(requested);
            String verifiedRegistrationId = attemptSessionService.verifiedRegistrationId(
                    request.examSessionToken(),
                    exam.getExamCode(),
                    requestedRegistrationId
            );
            if (!verifiedRegistrationId.equals(requested.getRegistrationId())) {
                throw new IllegalStateException("Secure exam session does not match this application number.");
            }
            return requested;
        }
        return findCandidateForExam(request.registrationId(), request.dateOfBirth());
    }

    private Exam linkedExam(Student student) {
        return examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
    }

    private ExamSubmissionResponse toResponse(ExamResponseSubmission submission) {
        return examRepository.findByExamCode(submission.getExamCode())
                .map(exam -> toResponse(submission, exam))
                .orElseGet(() -> ExamSubmissionResponse.from(submission));
    }

    private ExamSubmissionResponse toResponse(ExamResponseSubmission submission, Exam exam) {
        List<Map<String, Object>> answers = ExamSubmissionResponse.parseAnswers(submission.getAnswersJson());
        String streamCode = studentRepository.findByRegistrationId(submission.getRegistrationId())
                .map(Student::getStreamCode)
                .orElse(null);
        return ExamSubmissionResponse.from(submission, exam.getName(), streamCode, exam.getExamStartAt(), sortAnswersForDisplay(enrichAnswers(answers, exam)));
    }

    private List<Map<String, Object>> enrichAnswers(List<Map<String, Object>> answers, Exam exam) {
        if (answers.isEmpty() || exam.getQuestionPaperJsonPath() == null) {
            return answers;
        }
        try {
            List<Map<String, Object>> paperQuestions = paperQuestions(exam);
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (Map<String, Object> question : paperQuestions) {
                addQuestionIndex(byId, question, "question_id");
                addQuestionIndex(byId, question, "questionId");
                addQuestionIndex(byId, question, "id");
                addQuestionIndex(byId, question, "bank_question_id");
                addQuestionIndex(byId, question, "bankQuestionId");
                addQuestionIndex(byId, question, "source_question_id");
                addQuestionIndex(byId, question, "sourceQuestionId");
            }
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (int index = 0; index < answers.size(); index++) {
                Map<String, Object> answer = new LinkedHashMap<>(answers.get(index));
                Map<String, Object> question = byId.get(text(answer.get("questionId")));
                if (question != null) {
                    mergeQuestion(answer, question);
                }
                enriched.add(answer);
            }
            return enriched;
        } catch (RuntimeException exception) {
            return answers;
        }
    }

    private List<Map<String, Object>> paperQuestions(Exam exam) {
        JsonNode root = questionPaperStorageService.sanitizedPaper(exam);
        JsonNode questions = root.get("questions");
        if (questions == null || !questions.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode question : questions) {
            rows.add(OBJECT_MAPPER.convertValue(question, Map.class));
        }
        return rows;
    }

    private List<ExamAnswerRequest> canonicalAnswers(List<ExamAnswerRequest> submittedAnswers, Exam exam) {
        List<ExamAnswerRequest> submitted = submittedAnswers == null ? List.of() : submittedAnswers;
        List<Map<String, Object>> questions = paperQuestions(exam);
        if (questions.isEmpty()) {
            return submitted;
        }
        Map<String, ExamAnswerRequest> submittedById = submittedAnswersById(submitted);
        List<ExamAnswerRequest> canonical = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            Map<String, Object> question = questions.get(index);
            ExamAnswerRequest submittedAnswer = submittedAnswerFor(question, submittedById);
            String type = text(pick(question, "question_type", "questionType", "type"));
            boolean nat = isNatType(type);
            canonical.add(new ExamAnswerRequest(
                    text(pick(question, "section", "section_name", "sectionName", "subject_name", "subjectName")),
                    text(pick(question, "question_id", "questionId", "id", "bank_question_id", "bankQuestionId", "source_question_id", "sourceQuestionId")),
                    submittedAnswer == null
                            ? text(pick(question, "question_number", "questionNumber", "source_question_number", "sourceQuestionNumber"))
                            : firstNonBlank(
                                    text(submittedAnswer.displayQuestionId()),
                                    text(pick(question, "question_number", "questionNumber", "source_question_number", "sourceQuestionNumber"))
                            ),
                    type,
                    nat ? List.of() : canonicalMarkedOptionIds(question, submittedAnswer),
                    nat && submittedAnswer != null ? text(submittedAnswer.natAnswer()) : "",
                    submittedAnswer == null ? "not_visited" : text(submittedAnswer.status()),
                    submittedAnswer != null && submittedAnswer.markedForReview(),
                    submittedAnswer == null ? Map.of() : submittedAnswer.activity(),
                    text(pick(question,
                            "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image",
                            "questionDisplayImage", "question_display_image", "displayImage", "display_image",
                            "questionBaseImage", "question_base_image", "baseImage", "base_image",
                            "question_image", "questionImage", "image")),
                    text(pick(question, "question_text", "questionText", "body", "text", "question")),
                    pick(question, "options", "option_images", "optionImages")
            ));
        }
        return canonical;
    }

    private static List<String> canonicalMarkedOptionIds(Map<String, Object> question, ExamAnswerRequest submittedAnswer) {
        if (submittedAnswer == null || submittedAnswer.markedOptionIds() == null || submittedAnswer.markedOptionIds().isEmpty()) {
            return List.of();
        }
        Map<String, String> optionIds = optionIdMap(question);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : submittedAnswer.markedOptionIds()) {
            String clean = text(value);
            if (clean.isBlank()) {
                continue;
            }
            String upper = clean.toUpperCase();
            if (optionIds.containsKey(upper)) {
                normalized.add(text(optionIds.get(upper)));
                continue;
            }
            if (optionIds.containsValue(clean)) {
                normalized.add(clean);
            }
        }
        return List.copyOf(normalized);
    }

    private static boolean isNatType(String type) {
        String value = text(type).toUpperCase();
        return value.contains("NAT") || value.contains("NUM");
    }

    private static Map<String, ExamAnswerRequest> submittedAnswersById(List<ExamAnswerRequest> submitted) {
        Map<String, ExamAnswerRequest> byId = new LinkedHashMap<>();
        for (ExamAnswerRequest answer : submitted) {
            putAnswerIndex(byId, answer.questionId(), answer);
        }
        return byId;
    }

    private static ExamAnswerRequest submittedAnswerFor(Map<String, Object> question, Map<String, ExamAnswerRequest> submittedById) {
        for (String key : List.of("question_id", "questionId", "id", "bank_question_id", "bankQuestionId", "source_question_id", "sourceQuestionId")) {
            String value = text(question.get(key));
            if (!value.isBlank()) {
                ExamAnswerRequest answer = submittedById.get(value);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return null;
    }

    private static void putAnswerIndex(Map<String, ExamAnswerRequest> byId, String id, ExamAnswerRequest answer) {
        String value = text(id);
        if (!value.isBlank()) {
            byId.putIfAbsent(value, answer);
        }
    }

    private static void mergeQuestion(Map<String, Object> answer, Map<String, Object> question) {
        putIfBlank(answer, "section", pick(question, "section", "section_name", "sectionName", "subject_name", "subjectName"));
        putIfBlank(answer, "displayQuestionId", pick(question, "question_number", "questionNumber", "source_question_number", "sourceQuestionNumber"));
        putIfBlank(answer, "questionId", pick(question, "question_id", "questionId", "id", "bank_question_id", "bankQuestionId", "source_question_id", "sourceQuestionId"));
        putIfBlank(answer, "type", pick(question, "question_type", "questionType", "type"));
        putIfBlank(answer, "questionImage", pick(question,
                "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image",
                "questionDisplayImage", "question_display_image", "displayImage", "display_image",
                "questionBaseImage", "question_base_image", "baseImage", "base_image",
                "question_image", "questionImage", "image"));
        putIfBlank(answer, "body", pick(question, "question_text", "questionText", "body", "text", "question"));
        putIfBlank(answer, "marks", pick(question, "marks", "positive_marks", "positiveMarks"));
        putIfBlank(answer, "negativeMarks", pick(question, "negative_marks", "negativeMarks"));
        putIfMissing(answer, "options", pick(question, "options", "option_images", "optionImages"));
        putIfMissing(answer, "optionIds", pick(question, "option_ids", "optionIds"));
    }

    private static void addQuestionIndex(Map<String, Map<String, Object>> index, Map<String, Object> question, String key) {
        String value = text(question.get(key));
        if (!value.isBlank()) {
            index.putIfAbsent(value, question);
        }
    }

    private static Map<String, String> optionIdMap(Map<String, Object> question) {
        Object raw = pick(question, "option_ids", "optionIds");
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = text(entry.getKey()).toUpperCase();
            String value = text(entry.getValue());
            if (!key.isBlank() && !value.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> sortAnswersForDisplay(List<Map<String, Object>> answers) {
        List<Map<String, Object>> sorted = new ArrayList<>(answers);
        sorted.sort((left, right) -> Integer.compare(displayOrder(left), displayOrder(right)));
        return sorted;
    }

    private static int displayOrder(Map<String, Object> answer) {
        String displayId = text(answer.get("displayQuestionId"));
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String clean = text(value);
            if (!clean.isBlank()) {
                return clean;
            }
        }
        return "";
    }

    private static Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void putIfBlank(Map<String, Object> target, String key, Object value) {
        if (value != null && text(target.get(key)).isBlank()) {
            target.put(key, value);
        }
    }

    private static void putIfMissing(Map<String, Object> target, String key, Object value) {
        if (value != null && isEmpty(target.get(key))) {
            target.put(key, value);
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String toJson(List<ExamAnswerRequest> answers) {
        try {
            return OBJECT_MAPPER.writeValueAsString(answers);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not save response sheet.");
        }
    }

    private static LocalDate parseDateOfBirth(String dateOfBirth) {
        try {
            return LocalDate.parse(dateOfBirth.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter date of birth in YYYY-MM-DD format.");
        }
    }
}
