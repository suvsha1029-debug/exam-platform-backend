package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CandidatePasswordResetService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_REQUEST_MESSAGE = "If the details match an account, a password reset link has been sent.";

    private final StudentRepository studentRepository;
    private final CandidatePasswordResetTokenRepository tokenRepository;
    private final CandidateCaptchaService captchaService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final String publicBaseUrl;

    public CandidatePasswordResetService(
            StudentRepository studentRepository,
            CandidatePasswordResetTokenRepository tokenRepository,
            CandidateCaptchaService captchaService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            @Value("${app.public-base-url:http://localhost:8081}") String publicBaseUrl
    ) {
        this.studentRepository = studentRepository;
        this.tokenRepository = tokenRepository;
        this.captchaService = captchaService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public CandidatePasswordResetDtos.ResetResponse requestReset(CandidatePasswordResetDtos.ResetRequest request, String clientKey) {
        if (!captchaService.consume(request.challengeId(), request.captchaAnswer(), clientKey)) {
            throw new IllegalArgumentException("Captcha could not be verified. Request a new challenge and try again.");
        }
        studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(student -> student.getEmail() != null && student.getEmail().equalsIgnoreCase(request.email().trim()))
                .ifPresent(this::queueResetLink);
        return new CandidatePasswordResetDtos.ResetResponse(GENERIC_REQUEST_MESSAGE);
    }

    @Transactional
    public CandidatePasswordResetDtos.ResetResponse confirmReset(CandidatePasswordResetDtos.ResetConfirmRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("The new passwords do not match.");
        }
        validatePasswordStrength(request.newPassword());
        CandidatePasswordResetToken token = tokenRepository.findByTokenHash(hash(request.token()))
                .filter(candidate -> candidate.isUsableAt(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has expired. Request a new link."));
        token.getStudent().changePasscodeHash(passwordEncoder.encode(request.newPassword()));
        tokenRepository.findAllByStudentAndUsedAtIsNull(token.getStudent()).forEach(CandidatePasswordResetToken::markUsed);
        return new CandidatePasswordResetDtos.ResetResponse("Password updated. You can now sign in.");
    }

    private void queueResetLink(Student student) {
        tokenRepository.findAllByStudentAndUsedAtIsNull(student).forEach(CandidatePasswordResetToken::markUsed);
        String rawToken = randomUrlToken(32);
        tokenRepository.save(new CandidatePasswordResetToken(student, hash(rawToken), Instant.now().plus(TOKEN_LIFETIME)));
        String resetUrl = UriComponentsBuilder.fromUriString(publicBaseUrl)
                .path("/candidate-entry/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        notificationService.queueCandidatePasswordReset(student, resetUrl);
    }

    private static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters long.");
        }
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!(hasLower && hasUpper && hasDigit && hasSymbol)) {
            throw new IllegalArgumentException("Password must include uppercase, lowercase, number, and symbol.");
        }
    }

    private static String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
