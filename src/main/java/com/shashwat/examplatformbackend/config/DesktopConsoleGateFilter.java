package com.shashwat.examplatformbackend.config;

import com.shashwat.examplatformbackend.desktopconsole.DesktopConsoleDeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
@Order(5)
public class DesktopConsoleGateFilter extends OncePerRequestFilter {
    private static final long MAX_SKEW_SECONDS = 120;
    private final boolean required;
    private final boolean sharedSecretFallback;
    private final byte[] secret;
    private final DesktopConsoleDeviceService deviceService;

    public DesktopConsoleGateFilter(
            @Value("${app.desktop-console.required:true}") boolean required,
            @Value("${app.desktop-console.shared-secret-fallback:false}") boolean sharedSecretFallback,
            @Value("${app.desktop-console.secret:local-dev-desktop-console-secret-change-me}") String secret,
            DesktopConsoleDeviceService deviceService
    ) {
        this.required = required;
        this.sharedSecretFallback = sharedSecretFallback;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.deviceService = deviceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!required || !sensitive(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (validDesktopSignature(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"title\":\"Protected operator access required\",\"detail\":\"This operation is available only from an authorized operator console.\"}");
    }

    private static boolean sensitive(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (publicCandidateApi(request, path)) {
            return false;
        }
        if (path.equals(SecureConsolePaths.ADMIN)
                || path.equals(SecureConsolePaths.MASTER_ADMIN)
                || path.equals(SecureConsolePaths.PLATFORM)
                || path.equals("/admin.html")
                || path.equals("/admin-7xk92q-portal.html")
                || path.equals("/master-admin.html")
                || path.equals("/platform-control.html")) {
            return true;
        }
        return startsWithOrEquals(path, "/api/admin")
                || startsWithOrEquals(path, "/api/master-admin")
                || startsWithOrEquals(path, "/api/platform")
                || startsWithOrEquals(path, "/api/exams")
                || startsWithOrEquals(path, "/api/students")
                || startsWithOrEquals(path, "/api/notifications")
                || startsWithOrEquals(path, "/api/responses")
                || startsWithOrEquals(path, "/api/evaluation")
                || startsWithOrEquals(path, "/api/exam-access");
    }

    private static boolean publicCandidateApi(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if ("POST".equals(method) && (path.equals("/api/students/register") || path.equals("/api/students/register-with-files"))) {
            return true;
        }
        return startsWithOrEquals(path, "/api/candidate")
                || startsWithOrEquals(path, "/api/public");
    }

    private static boolean startsWithOrEquals(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean validDesktopSignature(HttpServletRequest request) {
        String timestamp = header(request, "X-Desktop-Console-Timestamp");
        String nonce = header(request, "X-Desktop-Console-Nonce");
        String signature = header(request, "X-Desktop-Console-Signature");
        String deviceId = header(request, "X-Desktop-Console-Device");
        if (timestamp.isBlank() || nonce.isBlank() || signature.isBlank()) return false;
        long epoch;
        try {
            epoch = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - epoch);
        if (skew > MAX_SKEW_SECONDS) return false;
        if (!deviceId.isBlank()) {
            String canonical = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n" + nonce + "\n" + deviceId;
            return deviceService.verify(deviceId, canonical, signature, nonce);
        }
        if (!sharedSecretFallback) {
            return false;
        }
        String legacyCanonical = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n" + nonce;
        String expected = hmacSha256Hex(legacyCanonical);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String value) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Desktop console signature could not be verified.", exception);
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
