package com.shashwat.examplatformbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

@Component
public class AdminSameOriginFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresSameOrigin(request) || isSameOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"title\":\"Security check failed\",\"detail\":\"Open the official admin page and try again.\"}");
    }

    private static boolean requiresSameOrigin(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/login") || path.equals("/api/admin-access/setup-password");
    }

    private static boolean isSameOrigin(HttpServletRequest request) {
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) {
            source = request.getHeader("Referer");
        }
        if (source == null || source.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(source);
            int sourcePort = uri.getPort() < 0 ? defaultPort(uri.getScheme()) : uri.getPort();
            int requestPort = request.getServerPort();
            return request.getScheme().equalsIgnoreCase(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost())
                    && requestPort == sourcePort;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
