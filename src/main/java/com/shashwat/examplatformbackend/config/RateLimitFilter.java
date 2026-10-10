package com.shashwat.examplatformbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        RateRule rule = ruleFor(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = clientKey(request) + "|" + rule.key();
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.isAfter(existing.resetAt())) {
                return new Bucket(1, now.plus(WINDOW));
            }
            return new Bucket(existing.count() + 1, existing.resetAt());
        });
        if (bucket.count() > rule.maxRequests()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, Duration.between(Instant.now(), bucket.resetAt()).toSeconds())));
            response.getWriter().write("{\"title\":\"Too many attempts\",\"detail\":\"Please wait a minute and try again.\"}");
            return;
        }
        if (buckets.size() > 10_000) {
            cleanup();
        }
        filterChain.doFilter(request, response);
    }

    private static RateRule ruleFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && path.equals("/api/candidate/login")) return new RateRule("candidate-login", 12);
        if ("POST".equals(method) && path.equals("/api/candidate/exam-login")) return new RateRule("exam-login", 18);
        if ("POST".equals(method) && path.equals("/api/candidate/exam-viewer-session")) return new RateRule("exam-session", 12);
        if ("POST".equals(method) && path.equals("/api/candidate/native-exam/session")) return new RateRule("native-exam-session", 12);
        if ("POST".equals(method) && path.equals("/api/candidate/exam-draft")) return new RateRule("exam-draft", 60);
        if ("POST".equals(method) && path.equals("/api/candidate/exam-submit")) return new RateRule("exam-submit", 10);
        if ("POST".equals(method) && path.equals("/api/candidate/server-exam/paper")) return new RateRule("server-exam-paper", 8);
        if ("POST".equals(method) && path.equals("/api/candidate/server-exam/bootstrap")) return new RateRule("server-exam-bootstrap", 12);
        if ("POST".equals(method) && path.equals("/api/candidate/server-exam/question")) return new RateRule("server-exam-question", 180);
        if ("POST".equals(method) && path.equals("/api/candidate/server-exam/answer")) return new RateRule("server-exam-answer", 240);
        if ("POST".equals(method) && path.equals("/api/candidate/server-exam/submit")) return new RateRule("server-exam-submit", 8);
        if ("POST".equals(method) && path.equals("/api/candidate/responses")) return new RateRule("candidate-responses", 18);
        if ("POST".equals(method) && path.equals("/api/candidate/syllabus-analysis")) return new RateRule("candidate-syllabus-analysis", 6);
        if ("POST".equals(method) && path.equals("/api/candidate/file")) return new RateRule("candidate-file", 30);
        if ("PATCH".equals(method) && path.equals("/api/candidate/correction")) return new RateRule("candidate-correction", 20);
        if ("POST".equals(method) && (path.equals("/api/students/register") || path.equals("/api/students/register-with-files"))) return new RateRule("candidate-registration", 8);
        if ("GET".equals(method) && path.equals("/api/public/candidate-security-token")) return new RateRule("candidate-token", 60);
        if ("GET".equals(method) && path.equals("/api/public/candidate-password-reset/captcha")) return new RateRule("candidate-reset-captcha", 20);
        if ("POST".equals(method) && path.equals("/api/public/candidate-password-reset/request")) return new RateRule("candidate-reset-request", 5);
        if ("POST".equals(method) && path.equals("/api/public/candidate-password-reset/confirm")) return new RateRule("candidate-reset-confirm", 8);
        if ("POST".equals(method) && path.equals("/login")) return new RateRule("admin-login", 6);
        if ("POST".equals(method) && path.equals("/api/admin-access/setup-password")) return new RateRule("admin-password-setup", 4);
        if ("POST".equals(method) && path.equals("/api/desktop-console/enroll")) return new RateRule("desktop-console-enroll", 4);
        if (path.startsWith("/api/master-admin/")) return new RateRule("master-admin", 30);
        if (path.startsWith("/api/exam-access/")) return new RateRule("exam-access", 60);
        if (path.startsWith("/api/auth/")) return new RateRule("admin-auth", 10);
        return null;
    }

    private static String clientKey(HttpServletRequest request) {
        // Never trust a client-supplied forwarding header by default. A production
        // reverse proxy can enforce and translate trusted source IPs separately.
        return request.getRemoteAddr();
    }

    private void cleanup() {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(entry -> now.isAfter(entry.getValue().resetAt()));
    }

    private record RateRule(String key, int maxRequests) {
    }

    private record Bucket(int count, Instant resetAt) {
    }
}
