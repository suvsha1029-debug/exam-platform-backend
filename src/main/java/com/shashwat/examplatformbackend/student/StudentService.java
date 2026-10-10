package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.auth.LoginRequest;
import com.shashwat.examplatformbackend.auth.LoginResponse;
import com.shashwat.examplatformbackend.exam.CandidateField;
import com.shashwat.examplatformbackend.exam.CustomCandidateField;
import com.shashwat.examplatformbackend.exam.CustomCandidateFieldJson;
import com.shashwat.examplatformbackend.exam.CustomCandidateFieldType;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamOriginalDocumentStorageService;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.ExamStream;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.common.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.evaluation.EvaluationService;
import com.shashwat.examplatformbackend.evaluation.ReportCardPdfService;
import com.shashwat.examplatformbackend.evaluation.ReportCardPreviewResponse;
import com.shashwat.examplatformbackend.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.shashwat.examplatformbackend.response.ExamResponseDraftRepository;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.examaccess.ExamAccessService;
import com.shashwat.examplatformbackend.examaccess.ExamAccessRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.security.SecureRandom;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class StudentService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long KB = 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> PDF_TYPES = Set.of("application/pdf");
    private static final Pattern INDIA_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile("^\\+\\d{1,4}\\s\\d{6,15}$");
    private static final Pattern INDIA_PINCODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern GENERIC_POSTAL_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\-\\s]{2,11}$");

    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final PasswordEncoder passwordEncoder;
    private final CandidateFileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final ExamOriginalDocumentStorageService examOriginalDocumentStorageService;
    private final PythonExamViewerBridgeService pythonExamViewerBridgeService;
    private final ExamResponseSubmissionRepository responseSubmissionRepository;
    private final ExamResponseDraftRepository responseDraftRepository;
    private final CandidatePasswordResetTokenRepository passwordResetTokenRepository;
    private final ExamAttemptSessionRepository examAttemptSessionRepository;
    private final ExamAccessRequestRepository examAccessRequestRepository;
    private final EvaluationService evaluationService;
    private final ReportCardPdfService reportCardPdfService;
    private final ExamAccessService examAccessService;
    private final ExamAttemptSessionService examAttemptSessionService;
    private final Path uploadRoot;

    public StudentService(StudentRepository studentRepository, ExamRepository examRepository, PasswordEncoder passwordEncoder, CandidateFileStorageService fileStorageService, NotificationService notificationService, QuestionPaperStorageService questionPaperStorageService, ExamOriginalDocumentStorageService examOriginalDocumentStorageService, PythonExamViewerBridgeService pythonExamViewerBridgeService, ExamResponseSubmissionRepository responseSubmissionRepository, ExamResponseDraftRepository responseDraftRepository, CandidatePasswordResetTokenRepository passwordResetTokenRepository, ExamAttemptSessionRepository examAttemptSessionRepository, ExamAccessRequestRepository examAccessRequestRepository, EvaluationService evaluationService, ReportCardPdfService reportCardPdfService, ExamAccessService examAccessService, ExamAttemptSessionService examAttemptSessionService, @Value("${app.upload.dir}") String uploadDir) {
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.questionPaperStorageService = questionPaperStorageService;
        this.examOriginalDocumentStorageService = examOriginalDocumentStorageService;
        this.pythonExamViewerBridgeService = pythonExamViewerBridgeService;
        this.responseSubmissionRepository = responseSubmissionRepository;
        this.responseDraftRepository = responseDraftRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.examAttemptSessionRepository = examAttemptSessionRepository;
        this.examAccessRequestRepository = examAccessRequestRepository;
        this.evaluationService = evaluationService;
        this.reportCardPdfService = reportCardPdfService;
        this.examAccessService = examAccessService;
        this.examAttemptSessionService = examAttemptSessionService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public RegisterStudentResponse register(RegisterStudentRequest request) {
        Exam exam = examRepository.findByExamCode(request.examCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("Exam not found."));
        if (!exam.isRegistrationOpen()) {
            throw new IllegalStateException("Registration is closed for this exam.");
        }
        String streamCode = normalizedStream(exam, request.streamCode());
        validateEnabledFields(exam, request);
        validateGeographyAndPhone(request.nationality(), request.phone(), request.parentPhone(), request.guardianPhone(), request.pincode(), request.state(), false);
        validatePasswordStrength(request.password());
        rejectDuplicateRegistration(exam, request);
        String registrationId = newRegistrationId(streamCode);
        Student student = new Student(
                registrationId,
                exam.getExamCode(),
                streamCode,
                request,
                passwordEncoder.encode(request.password())
        );
        studentRepository.save(student);
        notificationService.queueRegistrationMessages(student, exam);
        return new RegisterStudentResponse(registrationId, exam.getExamCode(), streamCode, student.getFullName(), "Registration successful");
    }

    @Transactional
    public RegisterStudentResponse registerWithFiles(CandidateRegistrationForm form, MultipartHttpServletRequest multipartRequest) {
        RegisterStudentRequest request = form.toRequest();
        Exam exam = examRepository.findByExamCode(request.examCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("Exam not found."));
        if (!exam.isRegistrationOpen()) {
            throw new IllegalStateException("Registration is closed for this exam.");
        }
        String streamCode = normalizedStream(exam, request.streamCode());
        validateEnabledFields(exam, request);
        validateGeographyAndPhone(request.nationality(), request.phone(), request.parentPhone(), request.guardianPhone(), request.pincode(), request.state(), false);
        validatePasswordStrength(request.password());
        rejectDuplicateRegistration(exam, request);
        validateEnabledFiles(exam, form);
        List<CustomCandidateField> customFields = CustomCandidateFieldJson.parse(exam.getCustomFieldsJson());
        String registrationId = newRegistrationId(streamCode);
        Student student = new Student(
                registrationId,
                exam.getExamCode(),
                streamCode,
                request,
                passwordEncoder.encode(request.password())
        );
        student.attachFiles(fileStorageService.store(registrationId, form));
        student.attachCustomFields(
                stringifyJsonMap(readCustomTextValues(customFields, multipartRequest)),
                stringifyJsonMap(storeCustomFiles(registrationId, customFields, multipartRequest))
        );
        studentRepository.save(student);
        notificationService.queueRegistrationMessages(student, exam);
        return new RegisterStudentResponse(registrationId, exam.getExamCode(), streamCode, student.getFullName(), "Registration successful");
    }

    public LoginResponse login(LoginRequest request) {
        return studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(student -> passwordEncoder.matches(request.passcode(), student.getPasscodeHash()))
                .map(student -> new LoginResponse(true, student.getRegistrationId(), student.getFullName(), "Login accepted"))
                .orElseGet(() -> new LoginResponse(false, request.registrationId(), "", "Invalid registration ID or passcode"));
    }

    @Transactional(readOnly = true)
    public CandidateDashboardResponse candidateDashboard(CandidateLoginRequest request) {
        return studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(student -> passwordEncoder.matches(request.password(), student.getPasscodeHash()))
                .map(student -> {
                    Exam exam = examRepository.findByExamCode(student.getExamCode())
                            .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
                    return CandidateDashboardResponse.from(
                            student,
                            exam.getName(),
                            exam.getDurationMinutes(),
                            exam.isCorrectionsOpen(),
                            CandidateCorrectionSupport.permissions(exam),
                            isExamLobbyPageAvailable(exam),
                            exam.getQuestionPaperJsonPath() != null,
                            exam.isExamLaunchEnabled(),
                            exam.isCandidateSubmitEnabled(),
                            exam.isResponsesReleased(),
                            exam.isResultReleased(),
                            exam.isOriginalQuestionPaperReleased(),
                            exam.isOriginalAnswerKeyReleased(),
                            exam.isScoreCardPdfReleased(),
                            exam.isAnswerKeyChallengeEnabled(),
                            exam.getAnswerKeyChallengeOpensAt(),
                            exam.getAnswerKeyChallengeClosesAt(),
                            CustomCandidateFieldJson.parseStringList(exam.getAllowedCountryIso2sJson()),
                            exam.getOriginalQuestionPaperPdfName(),
                            exam.getOriginalAnswerKeyPdfName(),
                            responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId()),
                            exam.getExamStartAt(),
                            exam.getLoginOpensMinutesBeforeStart(),
                            exam.getLoginClosesMinutesBeforeStart(),
                            exam.isExamReadinessTimerEnabled(),
                            exam.getExamReadinessDurationSeconds(),
                            examAccessMessage(exam)
                    );
                })
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID or password."));
    }

    @Transactional(readOnly = true)
    public CandidateDashboardResponse candidateExamDashboard(CandidateExamLoginRequest request) {
        Student student = findByRegistrationIdAndDob(request);
        Exam exam = linkedExam(student);
        return CandidateDashboardResponse.from(
                student,
                exam.getName(),
                exam.getDurationMinutes(),
                exam.isCorrectionsOpen(),
                CandidateCorrectionSupport.permissions(exam),
                isExamLobbyPageAvailable(exam),
                exam.getQuestionPaperJsonPath() != null,
                exam.isExamLaunchEnabled(),
                exam.isCandidateSubmitEnabled(),
                exam.isResponsesReleased(),
                exam.isResultReleased(),
                exam.isOriginalQuestionPaperReleased(),
                exam.isOriginalAnswerKeyReleased(),
                exam.isScoreCardPdfReleased(),
                exam.isAnswerKeyChallengeEnabled(),
                exam.getAnswerKeyChallengeOpensAt(),
                exam.getAnswerKeyChallengeClosesAt(),
                CustomCandidateFieldJson.parseStringList(exam.getAllowedCountryIso2sJson()),
                exam.getOriginalQuestionPaperPdfName(),
                exam.getOriginalAnswerKeyPdfName(),
                responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId()),
                exam.getExamStartAt(),
                exam.getLoginOpensMinutesBeforeStart(),
                exam.getLoginClosesMinutesBeforeStart(),
                exam.isExamReadinessTimerEnabled(),
                exam.getExamReadinessDurationSeconds(),
                examAccessMessage(exam)
        );
    }

    @Transactional(readOnly = true)
    public CandidateDashboardResponse candidateDashboardForSession(String registrationId) {
        Student student = studentRepository.findByRegistrationId(registrationId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Candidate record was not found."));
        Exam exam = linkedExam(student);
        return CandidateDashboardResponse.from(
                student,
                exam.getName(),
                exam.getDurationMinutes(),
                exam.isCorrectionsOpen(),
                CandidateCorrectionSupport.permissions(exam),
                isExamLobbyPageAvailable(exam),
                exam.getQuestionPaperJsonPath() != null,
                exam.isExamLaunchEnabled(),
                exam.isCandidateSubmitEnabled(),
                exam.isResponsesReleased(),
                exam.isResultReleased(),
                exam.isOriginalQuestionPaperReleased(),
                exam.isOriginalAnswerKeyReleased(),
                exam.isScoreCardPdfReleased(),
                exam.isAnswerKeyChallengeEnabled(),
                exam.getAnswerKeyChallengeOpensAt(),
                exam.getAnswerKeyChallengeClosesAt(),
                CustomCandidateFieldJson.parseStringList(exam.getAllowedCountryIso2sJson()),
                exam.getOriginalQuestionPaperPdfName(),
                exam.getOriginalAnswerKeyPdfName(),
                responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId()),
                exam.getExamStartAt(),
                exam.getLoginOpensMinutesBeforeStart(),
                exam.getLoginClosesMinutesBeforeStart(),
                exam.isExamReadinessTimerEnabled(),
                exam.getExamReadinessDurationSeconds(),
                examAccessMessage(exam)
        );
    }

    @Transactional(readOnly = true)
    public JsonNode candidateQuestionPaper(CandidateLoginRequest request) {
        Student student = studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasscodeHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID or password."));
        Exam exam = examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        if (!exam.isExamLobbyEnabled()) {
            throw new IllegalStateException("Exam lobby is not enabled yet.");
        }
        if (!exam.isExamLaunchEnabled()) {
            throw new IllegalStateException("Exam launch is not enabled yet.");
        }
        return questionPaperStorageService.sanitizedPaper(exam);
    }

    @Transactional(readOnly = true)
    public JsonNode candidateQuestionPaper(CandidateExamLoginRequest request) {
        Student student = findByRegistrationIdAndDob(request);
        Exam exam = linkedExam(student);
        validateExamLaunch(exam);
        return questionPaperStorageService.sanitizedPaper(exam);
    }

    @Transactional
    public ExamViewerSessionResponse createExamViewerSession(CandidateExamLoginRequest request) {
        Student student = findByRegistrationIdAndDob(request);
        Exam exam = linkedExam(student);
        validateExamLaunch(exam);
        if (responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())) {
            throw new IllegalStateException("This exam has already been submitted for this application number.");
        }
        return pythonExamViewerBridgeService.createSession(
                student,
                exam,
                examAccessService.consumeResumeContext(exam.getExamCode(), student.getRegistrationId(), Math.max(60, exam.getDurationMinutes() * 60))
        );
    }

    @Transactional
    public NativeExamSessionResponse createNativeExamSession(CandidateExamLoginRequest request) {
        Student student = findByRegistrationIdAndDob(request);
        Exam exam = linkedExam(student);
        validateExamLaunch(exam);
        if (responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())) {
            throw new IllegalStateException("This exam has already been submitted for this application number.");
        }
        int normalDurationSeconds = Math.max(60, exam.getDurationMinutes() * 60);
        var resumeContext = examAccessService.consumeResumeContext(exam.getExamCode(), student.getRegistrationId(), normalDurationSeconds);
        ExamAttemptToken token = examAttemptSessionService.issue(student, exam, resumeContext.durationSeconds());
        return new NativeExamSessionResponse(
                student.getRegistrationId(),
                exam.getExamCode(),
                exam.getName(),
                student.getFullName() == null ? "Candidate" : student.getFullName(),
                candidateImageDataUrl(student.getPhotoPath()),
                blankToFallback(exam.getExamUiHeaderTitle(), exam.getName()),
                blankToFallback(exam.getExamUiHeaderSubtitle(), ""),
                blankToFallback(exam.getExamUiBannerImageUrl(), ""),
                blankToFallback(exam.getReportCardInstituteLogoDataUrl(), ""),
                Math.max(1, resumeContext.durationSeconds()),
                exam.isCandidateSubmitEnabled(),
                exam.isDesktopCaptureProtectionEnabled(),
                exam.isExamReadinessTimerEnabled(),
                exam.getExamReadinessDurationSeconds(),
                token.token()
        );
    }

    private String candidateImageDataUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return "";
        }
        String requestedPath = storedPath.trim().replace('\\', '/');
        try {
            Path file = uploadRoot.resolve(requestedPath).toAbsolutePath().normalize();
            if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
                return "";
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null || !contentType.startsWith("image/")) {
                return "";
            }
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (java.io.IOException exception) {
            return "";
        }
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public String examViewerSessionHtml(String sessionId) {
        return pythonExamViewerBridgeService.readSessionHtml(sessionId);
    }

    @Transactional
    public CandidateDashboardResponse updateCandidateCorrection(CandidateCorrectionRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        Exam exam = examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        if (!exam.isCorrectionsOpen()) {
            throw new IllegalStateException("Corrections are closed for this exam.");
        }
        Set<String> allowedFields = CandidateCorrectionSupport.permissions(exam).keySet();
        if (allowedFields.isEmpty()) {
            throw new IllegalStateException("No fields are enabled for correction by admin.");
        }
        CandidateCorrectionSupport.validateDetails(request, student, exam);
        student.applyCorrection(request, allowedFields);
        CandidateCorrectionSupport.validateRequired(student, exam);
        validateGeographyAndPhone(student.getNationality(), student.getPhone(), student.getParentPhone(),
                student.getGuardianPhone(), student.getPincode(), student.getState(), true);
        Student saved = studentRepository.save(student);
        return CandidateDashboardResponse.from(
                saved,
                exam.getName(),
                exam.getDurationMinutes(),
                exam.isCorrectionsOpen(),
                CandidateCorrectionSupport.permissions(exam),
                isExamLobbyPageAvailable(exam),
                exam.getQuestionPaperJsonPath() != null,
                exam.isExamLaunchEnabled(),
                exam.isCandidateSubmitEnabled(),
                exam.isResponsesReleased(),
                exam.isResultReleased(),
                exam.isOriginalQuestionPaperReleased(),
                exam.isOriginalAnswerKeyReleased(),
                exam.isScoreCardPdfReleased(),
                exam.isAnswerKeyChallengeEnabled(),
                exam.getAnswerKeyChallengeOpensAt(),
                exam.getAnswerKeyChallengeClosesAt(),
                CustomCandidateFieldJson.parseStringList(exam.getAllowedCountryIso2sJson()),
                exam.getOriginalQuestionPaperPdfName(),
                exam.getOriginalAnswerKeyPdfName(),
                responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), saved.getRegistrationId()),
                exam.getExamStartAt(),
                exam.getLoginOpensMinutesBeforeStart(),
                exam.getLoginClosesMinutesBeforeStart(),
                exam.isExamReadinessTimerEnabled(),
                exam.getExamReadinessDurationSeconds(),
                examAccessMessage(exam)
        );
    }

    @Transactional
    public CandidateDashboardResponse submitCandidateCorrection(CandidateCorrectionRequest details,
            MultipartHttpServletRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(details.registrationId(), sessionRegistrationId);
        Exam exam = linkedExam(student);
        if (!exam.isCorrectionsOpen()) throw new IllegalStateException("Corrections are closed for this exam.");
        CandidateCorrectionSupport.validateDetails(details, student, exam);
        new CandidateCorrectionSupport(fileStorageService).applyUploads(student, exam, request);
        return updateCandidateCorrection(details, sessionRegistrationId);
    }

    @Transactional(readOnly = true)
    public Map<String, String> candidateCorrectionFiles(String registrationId, String sessionRegistrationId) {
        return CandidateCorrectionSupport.files(authenticatedCandidate(registrationId, sessionRegistrationId));
    }

    @Transactional(readOnly = true)
    public CandidateFileResponse candidateFile(CandidateFileRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        String requestedPath = request.path().trim().replace('\\', '/');
        if (!isOwnedCandidateFilePath(student, requestedPath)) {
            throw new IllegalArgumentException("Candidate file is not available for preview.");
        }
        return fileDataUrl(requestedPath, false);
    }

    @Transactional(readOnly = true)
    public CandidateFileResponse adminCandidateFile(String registrationId, String kind) {
        Student student = studentRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found."));
        String path = studentFilePath(student, kind);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Candidate file is not uploaded.");
        }
        return fileDataUrl(path, false);
    }

    private CandidateFileResponse fileDataUrl(String storedPath, boolean imagesOnly) {
        try {
            Path file = uploadRoot.resolve(storedPath.trim().replace('\\', '/')).toAbsolutePath().normalize();
            if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
                throw new IllegalArgumentException("Candidate file is not available for preview.");
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            if (imagesOnly && !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Only image files can be previewed.");
            }
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            return new CandidateFileResponse("data:" + contentType + ";base64," + encoded);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Could not open candidate file.");
        }
    }

    private static boolean isOwnedCandidateFilePath(Student student, String requestedPath) {
        return requestedPath.equals(student.getPhotoPath())
                || requestedPath.equals(student.getSignaturePath())
                || requestedPath.equals(student.getIdCardPath())
                || requestedPath.equals(student.getClass10MarksheetPath())
                || requestedPath.equals(student.getClass12MarksheetPath())
                || requestedPath.equals(student.getUgDocumentPath())
                || requestedPath.equals(student.getPgDocumentPath())
                || CustomCandidateFieldJson.parseStringMap(student.getCustomFilePathsJson()).containsValue(requestedPath);
    }

    private static String studentFilePath(Student student, String kind) {
        String clean = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (clean) {
            case "photo" -> student.getPhotoPath();
            case "signature" -> student.getSignaturePath();
            case "id", "id-card", "idcard" -> student.getIdCardPath();
            case "class-10", "class10", "class-10-marksheet", "class10-marksheet" -> student.getClass10MarksheetPath();
            case "class-12", "class12", "class-12-marksheet", "class12-marksheet" -> student.getClass12MarksheetPath();
            case "ug", "ug-document" -> student.getUgDocumentPath();
            case "pg", "pg-document" -> student.getPgDocumentPath();
            default -> throw new IllegalArgumentException("Unsupported candidate file kind.");
        };
    }

    @Transactional(readOnly = true)
    public CandidateReleasedAssetResponse releasedOriginalQuestionPaper(CandidateSessionRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        Exam exam = linkedExam(student);
        if (!exam.isOriginalQuestionPaperReleased() || exam.getOriginalQuestionPaperPdfPath() == null) {
            throw new IllegalStateException("Original question paper PDF is not released by admin.");
        }
        var data = examOriginalDocumentStorageService.readPdf(exam.getOriginalQuestionPaperPdfPath());
        return new CandidateReleasedAssetResponse(
                exam.getOriginalQuestionPaperPdfName() == null ? "question-paper.pdf" : exam.getOriginalQuestionPaperPdfName(),
                data.dataUrl()
        );
    }

    @Transactional(readOnly = true)
    public CandidateReleasedAssetResponse releasedOriginalAnswerKey(CandidateSessionRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        Exam exam = linkedExam(student);
        if (!exam.isOriginalAnswerKeyReleased() || exam.getOriginalAnswerKeyPdfPath() == null) {
            throw new IllegalStateException("Original answer key PDF is not released by admin.");
        }
        var data = examOriginalDocumentStorageService.readPdf(exam.getOriginalAnswerKeyPdfPath());
        return new CandidateReleasedAssetResponse(
                exam.getOriginalAnswerKeyPdfName() == null ? "answer-key.pdf" : exam.getOriginalAnswerKeyPdfName(),
                data.dataUrl()
        );
    }

    @Transactional(readOnly = true)
    public ReportCardPreviewResponse candidateResultCard(CandidateSessionRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        student.requireResultAvailable();
        Exam exam = linkedExam(student);
        if (!exam.isResultReleased()) {
            throw new IllegalStateException("Result card is not released by admin yet.");
        }
        if (!responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())) {
            throw new IllegalStateException("No submitted response found for this candidate.");
        }
        return evaluationService.reportCardPreview(exam.getExamCode(), student.getRegistrationId());
    }

    @Transactional(readOnly = true)
    public com.shashwat.examplatformbackend.evaluation.ScoreCardDocumentResponse candidateScoreCardDocument(CandidateSessionRequest request, String sessionRegistrationId) {
        ReportCardPreviewResponse preview = candidateResultCard(request, sessionRegistrationId);
        Exam exam = linkedExam(authenticatedCandidate(request.registrationId(), sessionRegistrationId));
        return reportCardPdfService.createDocument(preview, exam.isScoreCardPdfReleased());
    }

    @Transactional(readOnly = true)
    public CandidateReleasedAssetResponse releasedScoreCardPdf(CandidateSessionRequest request, String sessionRegistrationId) {
        Student student = authenticatedCandidate(request.registrationId(), sessionRegistrationId);
        student.requireResultAvailable();
        Exam exam = linkedExam(student);
        if (!exam.isResultReleased()) {
            throw new IllegalStateException("Result card is not released by admin yet.");
        }
        if (!exam.isScoreCardPdfReleased()) {
            throw new IllegalStateException("Score card PDF is not released by admin yet.");
        }
        if (!responseSubmissionRepository.existsByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())) {
            throw new IllegalStateException("No submitted response found for this candidate.");
        }
        ReportCardPreviewResponse preview = evaluationService.reportCardPreview(exam.getExamCode(), student.getRegistrationId());
        // Candidate downloads use the official layout even when a studio design is selected.
        byte[] pdfBytes = reportCardPdfService.createPdf(preview);
        String encoded = Base64.getEncoder().encodeToString(pdfBytes);
        return new CandidateReleasedAssetResponse(
                student.getRegistrationId() + "_ScoreCard.pdf",
                "data:application/pdf;base64," + encoded
        );
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> list() {
        return studentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(StudentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> listPage(int page, int size, String examCode) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (examCode != null && !examCode.isBlank()) {
            return PageResponse.from(
                    studentRepository.findAllByExamCode(examCode.trim(), pageable)
                            .map(StudentResponse::from)
            );
        }
        return PageResponse.from(
                studentRepository.findAllBy(pageable)
                        .map(StudentResponse::from)
        );
    }

    @Transactional
    public void delete(String registrationId) {
        Student student = studentRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + registrationId));
        deleteCandidateLinkedRows(student);
        studentRepository.delete(student);
        fileStorageService.deleteCandidateFolder(registrationId);
    }

    private Student authenticatedCandidate(CandidateLoginRequest request) {
        return studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(student -> passwordEncoder.matches(request.password(), student.getPasscodeHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID or password."));
    }

    private Student authenticatedCandidate(String requestedRegistrationId, String sessionRegistrationId) {
        String requested = requestedRegistrationId == null ? "" : requestedRegistrationId.trim();
        String sessionValue = sessionRegistrationId == null ? "" : sessionRegistrationId.trim();
        if (requested.isBlank() || sessionValue.isBlank() || !requested.equals(sessionValue)) {
            throw new IllegalStateException("Candidate session does not match this application number. Log in again.");
        }
        return studentRepository.findByRegistrationId(requested)
                .orElseThrow(() -> new IllegalArgumentException("Candidate record was not found."));
    }

    @Transactional
    public void deleteAll() {
        List<Student> students = studentRepository.findAll();
        students.forEach(this::deleteCandidateLinkedRows);
        studentRepository.deleteAll();
        students.forEach(student -> fileStorageService.deleteCandidateFolder(student.getRegistrationId()));
    }

    private void deleteCandidateLinkedRows(Student student) {
        String registrationId = student.getRegistrationId();
        passwordResetTokenRepository.deleteAllByStudent(student);
        examAttemptSessionRepository.deleteAllByRegistrationId(registrationId);
        examAccessRequestRepository.deleteAllByRegistrationId(registrationId);
        responseDraftRepository.deleteAllByRegistrationId(registrationId);
        responseSubmissionRepository.deleteAllByRegistrationId(registrationId);
    }

    private String newRegistrationId(String streamCode) {
        String id;
        do {
            if (streamCode != null && !streamCode.isBlank()) {
                id = streamCode + randomDigits(10);
            } else {
                id = twoDigitYear(Year.now().getValue()) + randomDigits(10);
            }
        } while (studentRepository.existsByRegistrationId(id));
        return id;
    }

    private void rejectDuplicateRegistration(Exam exam, RegisterStudentRequest request) {
        if (request.email() != null
                && !request.email().isBlank()
                && studentRepository.existsByExamCodeAndEmailIgnoreCase(exam.getExamCode(), request.email().trim())) {
            if (exam.isCorrectionsOpen()) {
                throw new IllegalStateException("This email is already registered for this exam. Please log in to the candidate dashboard for corrections.");
            }
            throw new IllegalStateException("This email is already registered for this exam. Duplicate registration is not allowed.");
        }
    }

    private static String normalizedStream(Exam exam, String requestedStream) {
        String streamCode = requestedStream == null ? "" : requestedStream.trim().toUpperCase(Locale.ROOT);
        if (streamCode.isBlank()) {
            if (exam.isStreamSelectionRequired()) {
                throw new IllegalArgumentException("Choose a valid enabled stream.");
            }
            return null;
        }
        return exam.getStreams()
                .stream()
                .filter(ExamStream::isEnabled)
                .map(ExamStream::getShortCode)
                .filter(code -> streamCode.equals(String.valueOf(code).trim().toUpperCase(Locale.ROOT)))
                .findFirst()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid enabled stream."));
    }

    private static String twoDigitYear(int year) {
        return String.format("%02d", Math.floorMod(year, 100));
    }

    private static boolean isExamLobbyLoginOpen(Exam exam) {
        if (!exam.isExamLobbyEnabled() || exam.getQuestionPaperJsonPath() == null || exam.getExamStartAt() == null) {
            return false;
        }
        Instant now = Instant.now();
        Instant opensAt = exam.getExamStartAt().minus(exam.getLoginOpensMinutesBeforeStart(), ChronoUnit.MINUTES);
        Instant closesAt = exam.getExamStartAt().minus(exam.getLoginClosesMinutesBeforeStart(), ChronoUnit.MINUTES);
        if (closesAt.isBefore(opensAt)) {
            closesAt = exam.getExamStartAt();
        }
        return !now.isBefore(opensAt) && now.isBefore(closesAt);
    }

    private static boolean isExamLobbyPageAvailable(Exam exam) {
        return exam.isExamLobbyEnabled() && exam.getQuestionPaperJsonPath() != null;
    }

    private Student findByRegistrationIdAndDob(CandidateExamLoginRequest request) {
        LocalDate dob = parseDateOfBirth(request.dateOfBirth());
        return studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(candidate -> dob.equals(candidate.getDateOfBirth()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration ID or date of birth."));
    }

    private Exam linkedExam(Student student) {
        return examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
    }

    private void validateExamLaunch(Exam exam) {
        if (!exam.isExamLobbyEnabled()) {
            throw new IllegalStateException("Exam lobby is not enabled yet.");
        }
        if (exam.getQuestionPaperJsonPath() == null) {
            throw new IllegalStateException("Question paper is not uploaded yet.");
        }
        if (!exam.isExamLaunchEnabled()) {
            throw new IllegalStateException("Exam launch is not enabled yet.");
        }
    }

    private static LocalDate parseDateOfBirth(String dateOfBirth) {
        try {
            return LocalDate.parse(dateOfBirth.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter date of birth in YYYY-MM-DD format.");
        }
    }

    private static String examAccessMessage(Exam exam) {
        if (exam.getQuestionPaperJsonPath() == null) {
            return "Question paper is not uploaded yet.";
        }
        if (!exam.isExamLobbyEnabled()) {
            return "Exam lobby is not enabled by admin yet.";
        }
        if (exam.isExamLaunchEnabled()) {
            return "Manual launch is enabled. Candidate can start the exam now.";
        }
        if (exam.getExamStartAt() == null) {
            return "Manual launch is disabled. Enable launch from admin to start the exam.";
        }
        if (isExamLobbyLoginOpen(exam)) {
            return "Exam lobby is open. Continue to the waiting screen.";
        }
        Instant now = Instant.now();
        Instant opensAt = exam.getExamStartAt().minus(exam.getLoginOpensMinutesBeforeStart(), ChronoUnit.MINUTES);
        Instant closesAt = exam.getExamStartAt().minus(exam.getLoginClosesMinutesBeforeStart(), ChronoUnit.MINUTES);
        if (now.isBefore(opensAt)) {
            return "Exam lobby will open later.";
        }
        if (!now.isBefore(closesAt)) {
            return "Exam lobby login is closed.";
        }
        return "Exam access is not available.";
    }

    private static void validateEnabledFields(Exam exam, RegisterStudentRequest request) {
        Set<CandidateField> enabled = EnumSet.copyOf(exam.getEnabledFields());
        Set<String> required = parseEnabledMap(exam.getCandidateRequiredFieldsJson()).keySet();
        require(enabled, CandidateField.FULL_NAME, request.fullName(), "Full name");
        require(enabled, CandidateField.DATE_OF_BIRTH, request.dateOfBirth() == null ? "" : request.dateOfBirth().toString(), "Date of birth");
        require(enabled, CandidateField.EMAIL, request.email(), "Email");
        require(enabled, CandidateField.PHONE, request.phone(), "Phone");
        require(enabled, CandidateField.PASSWORD, request.password(), "Password");
        optionalOrRequired(enabled, required, CandidateField.ADDRESS, request.address(), "Address");
        optionalOrRequired(enabled, required, CandidateField.STATE, request.state(), "State");
        optionalOrRequired(enabled, required, CandidateField.PINCODE, request.pincode(), "Pincode");
        optionalOrRequired(enabled, required, CandidateField.ALTERNATE_EMAIL, request.alternateEmail(), "Alternate email");
        optionalOrRequired(enabled, required, CandidateField.NATIONALITY, request.nationality(), "Nationality");
        optionalOrRequired(enabled, required, CandidateField.GENDER, request.gender(), "Gender");
        optionalOrRequired(enabled, required, CandidateField.PARENT_NAME, request.parentName(), "Parent name");
        optionalOrRequired(enabled, required, CandidateField.PARENT_PHONE, request.parentPhone(), "Parent phone");
        optionalOrRequired(enabled, required, CandidateField.GUARDIAN_NAME, request.guardianName(), "Guardian name");
        optionalOrRequired(enabled, required, CandidateField.GUARDIAN_PHONE, request.guardianPhone(), "Guardian phone");
        optionalOrRequired(enabled, required, CandidateField.GUARDIAN_RELATION, request.guardianRelation(), "Guardian relation");
        optionalOrRequired(enabled, required, CandidateField.UG_DETAILS, request.ugDetails(), "UG details");
        optionalOrRequired(enabled, required, CandidateField.PG_DETAILS, request.pgDetails(), "PG details");
    }

    private static void validateGeographyAndPhone(String country,
                                                  String phone,
                                                  String parentPhone,
                                                  String guardianPhone,
                                                  String pincode,
                                                  String state,
                                                  boolean correctionMode) {
        String normalizedCountry = cleanValue(country);
        boolean india = normalizedCountry == null || normalizedCountry.equalsIgnoreCase("India");
        validatePhoneValue("Candidate phone", phone, india, correctionMode);
        validatePhoneValue("Parent phone", parentPhone, india, correctionMode);
        validatePhoneValue("Guardian phone", guardianPhone, india, correctionMode);
        validatePostalValue(pincode, india, correctionMode);
        if (state != null && !state.isBlank() && state.trim().length() > 80) {
            throw new IllegalArgumentException("State / province is too long.");
        }
    }

    private static void validatePhoneValue(String label, String value, boolean india, boolean correctionMode) {
        String raw = cleanValue(value);
        if (raw == null) {
            return;
        }
        if (india) {
            String digits = raw.replaceAll("\\D+", "");
            if (digits.startsWith("91") && digits.length() == 12) {
                digits = digits.substring(2);
            }
            if (!INDIA_MOBILE_PATTERN.matcher(digits).matches()) {
                throw new IllegalArgumentException(label + " must be a valid 10-digit Indian mobile number.");
            }
            return;
        }
        if (correctionMode && raw.replaceAll("\\D+", "").length() < 6) {
            throw new IllegalArgumentException(label + " looks incomplete.");
        }
        if (!INTERNATIONAL_PHONE_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException(label + " must include a country code and 6 to 15 digits.");
        }
    }

    private static void validatePostalValue(String value, boolean india, boolean correctionMode) {
        String raw = cleanValue(value);
        if (raw == null) {
            return;
        }
        if (india) {
            if (!INDIA_PINCODE_PATTERN.matcher(raw).matches()) {
                throw new IllegalArgumentException("Pincode must be a valid 6-digit Indian PIN code.");
            }
            return;
        }
        if (!GENERIC_POSTAL_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException((correctionMode ? "Postal / ZIP code" : "Pincode") + " format looks invalid.");
        }
    }

    private static String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static void validateEnabledFiles(Exam exam, CandidateRegistrationForm form) {
        Set<CandidateField> enabled = EnumSet.copyOf(exam.getEnabledFields());
        Set<String> required = parseEnabledMap(exam.getCandidateRequiredFieldsJson()).keySet();
        optionalOrRequiredFile(enabled, required, CandidateField.PHOTO, form.getPhoto(), "Photo");
        optionalOrRequiredFile(enabled, required, CandidateField.SIGNATURE, form.getSignature(), "Signature");
        optionalOrRequiredFile(enabled, required, CandidateField.ID_CARD, form.getIdCard(), "ID card");
        optionalOrRequiredFile(enabled, required, CandidateField.CLASS_10_MARKSHEET, form.getClass10Marksheet(), "Class 10 marksheet");
        optionalOrRequiredFile(enabled, required, CandidateField.CLASS_12_MARKSHEET, form.getClass12Marksheet(), "Class 12 marksheet");
        optionalOrRequiredFile(enabled, required, CandidateField.UG_DETAILS, form.getUgDocument(), "UG document");
        optionalOrRequiredFile(enabled, required, CandidateField.PG_DETAILS, form.getPgDocument(), "PG document");
    }

    private Map<String, String> readCustomTextValues(List<CustomCandidateField> customFields, MultipartHttpServletRequest multipartRequest) {
        Map<String, String> values = new LinkedHashMap<>();
        for (CustomCandidateField field : customFields) {
            if (field.type() != CustomCandidateFieldType.TEXT && field.type() != CustomCandidateFieldType.TEXTAREA) {
                continue;
            }
            String value = cleanCustomValue(multipartRequest.getParameter(customFieldInputName(field.key())));
            if (field.required() && (value == null || value.isBlank())) {
                throw new IllegalArgumentException(field.label() + " is required.");
            }
            if (value != null && !value.isBlank()) {
                values.put(field.key(), value);
            }
        }
        return values;
    }

    private Map<String, String> storeCustomFiles(String registrationId, List<CustomCandidateField> customFields, MultipartHttpServletRequest multipartRequest) {
        Map<String, String> values = new LinkedHashMap<>();
        for (CustomCandidateField field : customFields) {
            if (field.type() != CustomCandidateFieldType.IMAGE_UPLOAD && field.type() != CustomCandidateFieldType.PDF_UPLOAD) {
                continue;
            }
            MultipartFile file = multipartRequest.getFile(customFieldInputName(field.key()));
            if (field.required() && (file == null || file.isEmpty())) {
                throw new IllegalArgumentException(field.label() + " is required.");
            }
            if (file == null || file.isEmpty()) {
                continue;
            }
            long maxBytes = Math.max(1, field.maxSizeKb() == null ? defaultMaxSizeKb(field.type()) : field.maxSizeKb()) * KB;
            Set<String> allowedTypes = field.type() == CustomCandidateFieldType.IMAGE_UPLOAD ? IMAGE_TYPES : PDF_TYPES;
            values.put(field.key(), fileStorageService.storeCustomFile(registrationId, field.key(), file, maxBytes, allowedTypes));
        }
        return values;
    }

    private static void optionalFile(Set<CandidateField> enabled, CandidateField field, org.springframework.web.multipart.MultipartFile file, String label) {
        if (!enabled.contains(field) && file != null && !file.isEmpty()) {
            throw new IllegalArgumentException(label + " upload is not enabled for this exam.");
        }
    }

    private static void optionalOrRequiredFile(Set<CandidateField> enabled, Set<String> required, CandidateField field, org.springframework.web.multipart.MultipartFile file, String label) {
        optionalFile(enabled, field, file, label);
        if (enabled.contains(field) && required.contains(field.name()) && (file == null || file.isEmpty())) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static String customFieldInputName(String key) {
        return "customField_" + key;
    }

    private static String cleanCustomValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }

    private static int defaultMaxSizeKb(CustomCandidateFieldType type) {
        return type == CustomCandidateFieldType.IMAGE_UPLOAD ? 100 : 1024;
    }

    private static String stringifyJsonMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not store custom registration data.");
        }
    }

    private static void require(Set<CandidateField> enabled, CandidateField field, String value, String label) {
        if (!enabled.contains(field) || value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static void optional(Set<CandidateField> enabled, CandidateField field, String value, String label) {
        if (!enabled.contains(field) && value != null && !value.isBlank()) {
            throw new IllegalArgumentException(label + " is not enabled for this exam.");
        }
    }

    private static void optionalOrRequired(Set<CandidateField> enabled, Set<String> required, CandidateField field, String value, String label) {
        optional(enabled, field, value, label);
        if (enabled.contains(field) && required.contains(field.name()) && (value == null || value.isBlank())) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static Map<String, String> parseEnabledMap(String json) {
        return CustomCandidateFieldJson.parseStringMap(json)
                .entrySet()
                .stream()
                .filter(entry -> Boolean.parseBoolean(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!(hasLower && hasUpper && hasDigit && hasSymbol)) {
            throw new IllegalArgumentException("Password must include uppercase, lowercase, number, and symbol.");
        }
    }

    private static String randomToken(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private static String randomDigits(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(RANDOM.nextInt(10));
        }
        return out.toString();
    }
}
