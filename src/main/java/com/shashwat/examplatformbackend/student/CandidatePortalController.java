package com.shashwat.examplatformbackend.student;

import jakarta.validation.Valid;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionService;
import com.shashwat.examplatformbackend.response.ExamSubmissionResponse;
import com.shashwat.examplatformbackend.response.ExamSubmitRequest;
import com.shashwat.examplatformbackend.response.ExamSubmitResponse;
import com.shashwat.examplatformbackend.evaluation.ReportCardPreviewResponse;
import com.shashwat.examplatformbackend.evaluation.SyllabusAnalysisResponse;
import com.shashwat.examplatformbackend.evaluation.SyllabusAnalysisService;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.ExamSessionSignalRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Map;

@RestController
@RequestMapping("/api/candidate")
public class CandidatePortalController {
    private static final String CANDIDATE_SESSION_REGISTRATION_ID = "candidateRegistrationId";

    private final StudentService studentService;
    private final ExamResponseSubmissionService responseSubmissionService;
    private final ExamAttemptSessionService attemptSessionService;
    private final SyllabusAnalysisService syllabusAnalysisService;

    public CandidatePortalController(StudentService studentService, ExamResponseSubmissionService responseSubmissionService,
                                     ExamAttemptSessionService attemptSessionService,
                                     SyllabusAnalysisService syllabusAnalysisService) {
        this.studentService = studentService;
        this.responseSubmissionService = responseSubmissionService;
        this.attemptSessionService = attemptSessionService;
        this.syllabusAnalysisService = syllabusAnalysisService;
    }

    @PostMapping("/login")
    public CandidateDashboardResponse login(@Valid @RequestBody CandidateLoginRequest request, HttpSession session) {
        CandidateDashboardResponse response = studentService.candidateDashboard(request);
        session.setAttribute(CANDIDATE_SESSION_REGISTRATION_ID, response.registrationId());
        return response;
    }

    @PostMapping("/exam-paper")
    public ResponseEntity<Void> examPaper(@Valid @RequestBody CandidateLoginRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/exam-login")
    public CandidateDashboardResponse examLogin(@Valid @RequestBody CandidateExamLoginRequest request) {
        return studentService.candidateExamDashboard(request);
    }

    @PostMapping("/exam-paper-by-dob")
    public ResponseEntity<Void> examPaperByDob(@Valid @RequestBody CandidateExamLoginRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/exam-viewer-session")
    public ExamViewerSessionResponse examViewerSession(@Valid @RequestBody CandidateExamLoginRequest request) {
        return studentService.createExamViewerSession(request);
    }

    @PostMapping("/native-exam/session")
    public NativeExamSessionResponse nativeExamSession(@Valid @RequestBody CandidateExamLoginRequest request) {
        return studentService.createNativeExamSession(request);
    }

    @PostMapping("/exam-submit")
    public ExamSubmitResponse examSubmit(@Valid @RequestBody ExamSubmitRequest request) {
        return responseSubmissionService.submit(request);
    }

    @PostMapping("/exam-draft")
    public ExamSubmitResponse examDraft(@Valid @RequestBody ExamSubmitRequest request) {
        return responseSubmissionService.saveDraft(request);
    }

    @PostMapping("/exam-heartbeat")
    public void examHeartbeat(@Valid @RequestBody ExamSessionSignalRequest request) {
        attemptSessionService.heartbeat(request.examSessionToken());
    }

    @PostMapping("/exam-interruption")
    public void examInterruption(@Valid @RequestBody ExamSessionSignalRequest request) {
        String reason = request.reason() == null || request.reason().isBlank()
                ? "Candidate exam window closed or disconnected."
                : request.reason().trim();
        attemptSessionService.flagInterruption(request.examSessionToken(), reason);
    }

    @GetMapping("/client-network")
    public Map<String, String> clientNetwork(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return Map.of(
                "ipAddress", ip == null || ip.isBlank() ? "not available" : ip,
                "machineIpAddress", localMachineIp()
        );
    }

    private static String localMachineIp() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                var addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fallback below keeps the screen usable even if adapter inspection is blocked.
        }
        return "not available";
    }

    @PostMapping("/responses")
    public ExamSubmissionResponse responses(@Valid @RequestBody CandidateExamLoginRequest request) {
        return responseSubmissionService.candidateSubmission(request);
    }

    @GetMapping("/exam-viewer-launch")
    public ResponseEntity<Void> examViewerLaunch(
            @RequestParam String registrationId,
            @RequestParam String dateOfBirth
    ) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @GetMapping(value = "/exam-viewer-session/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    public String examViewerSessionHtml(@PathVariable String sessionId) {
        return studentService.examViewerSessionHtml(sessionId);
    }

    @PatchMapping("/correction")
    public CandidateDashboardResponse correction(@Valid @RequestBody CandidateCorrectionRequest request, HttpSession session) {
        return studentService.updateCandidateCorrection(request, candidateSessionRegistrationId(session));
    }

    @PostMapping(value = "/correction/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateDashboardResponse submitCorrection(
            @Valid @org.springframework.web.bind.annotation.RequestPart("details") CandidateCorrectionRequest details,
            org.springframework.web.multipart.MultipartHttpServletRequest request, HttpSession session) {
        return studentService.submitCandidateCorrection(details, request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/correction/files")
    public Map<String, String> correctionFiles(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.candidateCorrectionFiles(request.registrationId(), candidateSessionRegistrationId(session));
    }

    @PostMapping("/file")
    public CandidateFileResponse file(@Valid @RequestBody CandidateFileRequest request, HttpSession session) {
        return studentService.candidateFile(request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/original-question-paper")
    public CandidateReleasedAssetResponse originalQuestionPaper(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.releasedOriginalQuestionPaper(request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/dashboard")
    public CandidateDashboardResponse dashboard(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        String sessionRegistrationId = candidateSessionRegistrationId(session);
        if (!sessionRegistrationId.equals(request.registrationId().trim())) {
            throw new IllegalStateException("Candidate session does not match this application number. Log in again.");
        }
        return studentService.candidateDashboardForSession(sessionRegistrationId);
    }

    @PostMapping("/original-answer-key")
    public CandidateReleasedAssetResponse originalAnswerKey(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.releasedOriginalAnswerKey(request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/result-card")
    public ReportCardPreviewResponse resultCard(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.candidateResultCard(request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/score-card-pdf")
    public CandidateReleasedAssetResponse scoreCardPdf(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.releasedScoreCardPdf(request, candidateSessionRegistrationId(session));
    }

    @PostMapping("/score-card-document")
    public com.shashwat.examplatformbackend.evaluation.ScoreCardDocumentResponse scoreCardDocument(@Valid @RequestBody CandidateSessionRequest request, HttpSession session) {
        return studentService.candidateScoreCardDocument(request, candidateSessionRegistrationId(session));
    }

    @PostMapping(value = "/syllabus-analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SyllabusAnalysisResponse syllabusAnalysis(@RequestParam String registrationId,
                                                     @RequestParam("file") MultipartFile file,
                                                     HttpSession session) {
        String sessionRegistrationId = candidateSessionRegistrationId(session);
        if (!sessionRegistrationId.equals(registrationId == null ? "" : registrationId.trim())) {
            throw new IllegalStateException("Candidate session does not match this application number. Log in again.");
        }
        return syllabusAnalysisService.analyzeCandidate(sessionRegistrationId, file);
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.removeAttribute(CANDIDATE_SESSION_REGISTRATION_ID);
    }

    private static String candidateSessionRegistrationId(HttpSession session) {
        Object value = session.getAttribute(CANDIDATE_SESSION_REGISTRATION_ID);
        if (value instanceof String registrationId && !registrationId.isBlank()) {
            return registrationId;
        }
        throw new IllegalStateException("Candidate session is not active. Log in again.");
    }
}
