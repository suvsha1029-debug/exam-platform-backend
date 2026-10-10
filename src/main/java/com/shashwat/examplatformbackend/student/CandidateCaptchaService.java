package com.shashwat.examplatformbackend.student;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CandidateCaptchaService {
    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public CaptchaResponse create(String clientKey) {
        cleanup();
        int left = 2 + RANDOM.nextInt(8);
        int right = 1 + RANDOM.nextInt(9);
        String id = randomUrlToken(24);
        challenges.put(id, new Challenge(hash(String.valueOf(left + right)), clientKey, Instant.now().plus(LIFETIME)));
        return new CaptchaResponse(id, left + " + " + right + " = ?");
    }

    public boolean consume(String challengeId, String answer, String clientKey) {
        if (challengeId == null || answer == null) {
            return false;
        }
        Challenge challenge = challenges.remove(challengeId);
        return challenge != null
                && Instant.now().isBefore(challenge.expiresAt())
                && MessageDigest.isEqual(challenge.clientKey().getBytes(), clientKey.getBytes())
                && MessageDigest.isEqual(challenge.answerHash().getBytes(), hash(answer.trim()).getBytes());
    }

    private void cleanup() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        if (challenges.size() > 10_000) {
            challenges.clear();
        }
    }

    private static String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record CaptchaResponse(String challengeId, String prompt) {
    }

    private record Challenge(String answerHash, String clientKey, Instant expiresAt) {
    }
}
