package com.shashwat.examplatformbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class NoStoreCacheFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")
                || path.startsWith("/candidate-secure/")
                || path.equals("/candidate-secure-exam-window")
                || path.equals("/candidate-exam.html")
                || path.equals("/candidate-exam-lobby.html")
                || path.equals("/candidate-responses.html")
                || path.startsWith("/console/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, private");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("X-Robots-Tag", "noindex, noarchive, noimageindex");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        filterChain.doFilter(request, response);
    }
}
