package com.shashwat.examplatformbackend.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class CandidatePageSessionService {
    private static final String SESSION_KEY = "candidatePageSessions";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    public String create(HttpSession session, CandidatePageTarget target) {
        cleanup(session);
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions(session).put(token, new CandidatePageGrant(target, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    public boolean isAllowed(HttpSession session, String token, CandidatePageTarget target) {
        cleanup(session);
        CandidatePageGrant grant = sessions(session).get(token);
        return grant != null && grant.target() == target && grant.expiresAt().isAfter(Instant.now());
    }

    @SuppressWarnings("unchecked")
    private Map<String, CandidatePageGrant> sessions(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, CandidatePageGrant>) map;
        }
        Map<String, CandidatePageGrant> created = new HashMap<>();
        session.setAttribute(SESSION_KEY, created);
        return created;
    }

    private void cleanup(HttpSession session) {
        Instant now = Instant.now();
        sessions(session).entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public enum CandidatePageTarget {
        REGISTRATION,
        DASHBOARD,
        DETAILS,
        EXAM
    }

    private record CandidatePageGrant(CandidatePageTarget target, Instant expiresAt) {
    }
}
