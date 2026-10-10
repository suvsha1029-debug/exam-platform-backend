package com.shashwat.examplatformbackend.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AdminCsrfTokenService {
    public static final String HEADER_NAME = "X-Admin-CSRF";
    private static final String SESSION_KEY = AdminCsrfTokenService.class.getName() + ".TOKEN";
    private final SecureRandom secureRandom = new SecureRandom();

    public String token(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    public boolean matches(HttpSession session, String providedToken) {
        if (session == null || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        Object existing = session.getAttribute(SESSION_KEY);
        if (!(existing instanceof String expected)) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(), providedToken.getBytes());
    }
}
