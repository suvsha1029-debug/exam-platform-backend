package com.shashwat.examplatformbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AdminCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/master-admin/",
            "/api/exams",
            "/api/students",
            "/api/notifications",
            "/api/responses",
            "/api/evaluation",
            "/api/exam-access"
    );

    private final AdminCsrfTokenService tokenService;

    public AdminCsrfFilter(AdminCsrfTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresAdminCsrf(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (!tokenService.matches(session, request.getHeader(AdminCsrfTokenService.HEADER_NAME))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"title\":\"Security check failed\",\"detail\":\"Refresh the admin page and try again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean requiresAdminCsrf(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/students/register") || path.equals("/api/students/register-with-files")) {
            return false;
        }
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
