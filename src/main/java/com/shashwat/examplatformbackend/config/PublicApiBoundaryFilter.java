package com.shashwat.examplatformbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(3)
public class PublicApiBoundaryFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!startsWithOrEquals(path, "/api/public") || allowedPublicEndpoint(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"title\":\"Not found\",\"detail\":\"The requested public resource is not available.\"}");
    }

    private static boolean allowedPublicEndpoint(String method, String path) {
        if ("GET".equals(method) && path.equals("/api/public/candidate-security-token")) {
            return true;
        }
        if ("GET".equals(method) && (path.equals("/api/public/exams") || path.equals("/api/public/exams/open"))) {
            return true;
        }
        if ("GET".equals(method) && startsWithOrEquals(path, "/api/public/exams/portal-banner")) {
            return true;
        }
        if ("GET".equals(method) && path.matches("^/api/public/exams/[^/]+/notice-document\\.pdf$")) {
            return true;
        }
        if ("POST".equals(method) && path.equals("/api/public/institute-service-requests")) {
            return true;
        }
        if ("GET".equals(method) && path.equals("/api/public/candidate-password-reset/captcha")) {
            return true;
        }
        return "POST".equals(method)
                && (path.equals("/api/public/candidate-password-reset/request")
                || path.equals("/api/public/candidate-password-reset/confirm"));
    }

    private static boolean startsWithOrEquals(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
