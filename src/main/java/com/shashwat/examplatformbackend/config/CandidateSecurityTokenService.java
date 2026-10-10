package com.shashwat.examplatformbackend.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CandidateSecurityTokenService {
    public static final String HEADER_NAME = "X-Candidate-CSRF";
    private static final String SESSION_KEY = "candidateCsrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String token(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    public boolean matches(HttpSession session, String token) {
        Object existing = session.getAttribute(SESSION_KEY);
        return existing instanceof String expected && expected.equals(token);
    }
}
