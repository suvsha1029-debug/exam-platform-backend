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
public class CandidateCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");

    private final CandidateSecurityTokenService tokenService;

    public CandidateCsrfFilter(CandidateSecurityTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!requiresCandidateCsrf(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        String token = request.getHeader(CandidateSecurityTokenService.HEADER_NAME);
        if (session == null || !tokenService.matches(session, token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Security check failed\",\"detail\":\"Refresh the page and try again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean requiresCandidateCsrf(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/candidate/logout")) {
            return false;
        }
        return path.startsWith("/api/candidate/")
                || path.equals("/api/students/register")
                || path.equals("/api/students/register-with-files")
                || path.equals("/api/public/candidate-password-reset/request")
                || path.equals("/api/public/candidate-password-reset/confirm");
    }
}
