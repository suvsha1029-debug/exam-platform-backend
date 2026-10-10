package com.shashwat.examplatformbackend.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shashwat.examplatformbackend.config.CandidateErrorPageService;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.ResumeContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PythonExamViewerBridgeService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> consumedSessions = ConcurrentHashMap.newKeySet();
    private final Path sessionRoot;
    private final ExamAttemptSessionService attemptSessionService;
    private final CandidateErrorPageService errorPageService;

    public PythonExamViewerBridgeService(
            @Value("${app.upload.dir}") String uploadDir,
            ExamAttemptSessionService attemptSessionService,
            CandidateErrorPageService errorPageService
    ) {
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.sessionRoot = uploadRoot.resolve("exam-viewer-sessions").normalize();
        this.attemptSessionService = attemptSessionService;
        this.errorPageService = errorPageService;
    }

    public ExamViewerSessionResponse createSession(Student student, Exam exam, ResumeContext resumeContext) {
        if (exam.getQuestionPaperJsonPath() == null) {
            throw new IllegalStateException("Question paper is not uploaded yet.");
        }
        try {
            Files.createDirectories(sessionRoot);
            String sessionId = secureSessionId();
            ExamAttemptToken attemptToken = attemptSessionService.issue(student, exam, resumeContext.durationSeconds());
            Path htmlPath = sessionRoot.resolve(sessionId + ".html").normalize();
            if (!htmlPath.startsWith(sessionRoot)) {
                throw new IllegalStateException("Invalid exam viewer session path.");
            }
            Files.writeString(htmlPath, serverDrivenSessionHtml(student, exam, attemptToken, resumeContext), StandardCharsets.UTF_8);
            return new ExamViewerSessionResponse("/api/candidate/exam-viewer-session/" + sessionId);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create exam viewer session.");
        }
    }

    public String readSessionHtml(String sessionId) {
        if (!sessionId.matches("[A-Za-z0-9_-]{43}")) {
            return errorPageService.html("invalid-link");
        }
        if (!consumedSessions.add(sessionId)) {
            return submittedOrExpiredHtml();
        }
        Path htmlPath = sessionRoot.resolve(sessionId + ".html").normalize();
        if (!htmlPath.startsWith(sessionRoot) || !Files.exists(htmlPath)) {
            return submittedOrExpiredHtml();
        }
        try {
            String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
            Files.deleteIfExists(htmlPath);
            Files.deleteIfExists(sessionRoot.resolve(sessionId + ".payload.json").normalize());
            return html;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read exam viewer session.");
        }
    }

    private String serverDrivenSessionHtml(Student student, Exam exam, ExamAttemptToken attemptToken, ResumeContext resumeContext) throws IOException {
        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("registrationId", student.getRegistrationId());
        candidate.put("examCode", exam.getExamCode());
        candidate.put("examName", exam.getName());
        candidate.put("durationMinutes", Math.max(1, (int) Math.ceil(resumeContext.durationSeconds() / 60.0)));
        candidate.put("streamCode", student.getStreamCode() == null ? "" : student.getStreamCode());
        candidate.put("fullName", student.getFullName() == null ? "Candidate" : student.getFullName());
        candidate.put("candidateSubmitEnabled", exam.isCandidateSubmitEnabled());
        candidate.put("examReadinessTimerEnabled", exam.isExamReadinessTimerEnabled());
        candidate.put("examReadinessDurationSeconds", exam.getExamReadinessDurationSeconds());
        candidate.put("examLobbyAvailable", true);
        candidate.put("questionPaperUploaded", true);
        candidate.put("examLaunchEnabled", true);
        candidate.put("responseSubmitted", false);
        candidate.put("examStartAt", Instant.now().toString());

        ObjectNode auth = objectMapper.createObjectNode();
        auth.put("registrationId", student.getRegistrationId());
        auth.put("examSessionToken", attemptToken.token());
        auth.put("remainingSeconds", Math.max(1, resumeContext.durationSeconds()));

        String candidateJson = objectMapper.writeValueAsString(candidate)
                .replace("</", "<\\/")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        String authJson = objectMapper.writeValueAsString(auth)
                .replace("</", "<\\/")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta http-equiv="Cache-Control" content="no-store">
                  <title>Secure Exam Window</title>
                </head>
                <body>
                  <!-- SERVER_DRIVEN_EXAM_SHELL -->
                  <script>
                    sessionStorage.setItem('candidateDashboardData', %s);
                    sessionStorage.setItem('candidateExamAuth', %s);
                    location.replace('/candidate-exam.html?secureWindow=1');
                  </script>
                  <noscript>This exam requires JavaScript.</noscript>
                </body>
                </html>
                """.formatted(objectMapper.writeValueAsString(candidateJson), objectMapper.writeValueAsString(authJson));
    }

    private static String secureSessionId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String submittedOrExpiredHtml() {
        return errorPageService.html("session-closed");
    }

}
