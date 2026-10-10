package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.exam.Exam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class ExamAttemptSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration EXTRA_SUBMIT_GRACE = Duration.ofMinutes(30);

    private final ExamAttemptSessionRepository repository;

    public ExamAttemptSessionService(ExamAttemptSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExamAttemptToken issue(Student student, Exam exam) {
        return issue(student, exam, Math.max(60, exam.getDurationMinutes() * 60));
    }

    @Transactional
    public ExamAttemptToken issue(Student student, Exam exam, int durationSeconds) {
        Instant now = Instant.now();
        repository.findAllByExamCodeAndRegistrationIdAndSubmittedFalseAndRevokedFalseAndExpiresAtAfter(
                exam.getExamCode(),
                student.getRegistrationId(),
                now
        ).forEach(ExamAttemptSession::revoke);

        String token = newToken();
        Instant dueAt = now.plusSeconds(Math.max(60, durationSeconds));
        Instant expiresAt = dueAt.plus(EXTRA_SUBMIT_GRACE);
        repository.save(new ExamAttemptSession(hash(token), exam.getExamCode(), student.getRegistrationId(), dueAt, expiresAt));
        return new ExamAttemptToken(token);
    }

    @Transactional
    public void heartbeat(String token) {
        usableAttemptByToken(token).heartbeat();
    }

    @Transactional
    public void flagInterruption(String token, String reason) {
        usableAttemptByToken(token).flagInterruption(reason);
    }

    @Transactional
    public void revokeCandidateAttempts(String examCode, String registrationId) {
        repository.findAllByExamCodeAndRegistrationIdOrderByIssuedAtDesc(examCode, registrationId)
                .forEach(ExamAttemptSession::revoke);
    }

    @Transactional
    public void markSubmitted(String token, String examCode, String registrationId) {
        ExamAttemptSession attempt = usableAttempt(token, examCode, registrationId);
        attempt.markSubmitted();
    }

    @Transactional(readOnly = true)
    public void verify(String token, String examCode, String registrationId) {
        usableAttempt(token, examCode, registrationId);
    }

    @Transactional(readOnly = true)
    public String verifiedRegistrationId(String token, String examCode, String requestedRegistrationId) {
        ExamAttemptSession attempt = usableAttemptByToken(token);
        if (!attempt.getExamCode().equals(examCode)) {
            throw new IllegalStateException("Secure exam session is expired or no longer active.");
        }
        if (requestedRegistrationId != null
                && !requestedRegistrationId.isBlank()
                && !attempt.getRegistrationId().equals(requestedRegistrationId.trim())) {
            throw new IllegalStateException("Secure exam session does not match this application number.");
        }
        return attempt.getRegistrationId();
    }

    @Transactional(readOnly = true)
    public ExamAttemptSession verifiedAttempt(String token) {
        return usableAttemptByToken(token);
    }

    private ExamAttemptSession usableAttempt(String token, String examCode, String registrationId) {
        ExamAttemptSession attempt = usableAttemptByToken(token);
        if (!attempt.belongsTo(examCode, registrationId) || !attempt.isUsableAt(Instant.now())) {
            throw new IllegalStateException("Secure exam session is expired or no longer active.");
        }
        return attempt;
    }

    private ExamAttemptSession usableAttemptByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Secure exam session is missing. Launch the exam again from the candidate dashboard.");
        }
        ExamAttemptSession attempt = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalStateException("Secure exam session is invalid. Launch the exam again from the candidate dashboard."));
        if (!attempt.isUsableAt(Instant.now())) {
            throw new IllegalStateException("Secure exam session is expired or no longer active.");
        }
        return attempt;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Secure token hashing is not available.");
        }
    }
}
