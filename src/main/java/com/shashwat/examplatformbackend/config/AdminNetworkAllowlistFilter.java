package com.shashwat.examplatformbackend.config;

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
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(4)
public class AdminNetworkAllowlistFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final boolean trustProxyHeaders;
    private final List<IpRule> rules;

    public AdminNetworkAllowlistFilter(
            @Value("${app.admin-network.allowlist.enabled:true}") boolean enabled,
            @Value("${app.admin-network.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.admin-network.allowlist:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String allowlist
    ) {
        this.enabled = enabled;
        this.trustProxyHeaders = trustProxyHeaders;
        this.rules = parseRules(allowlist);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !sensitive(request) || allowed(clientIp(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"title\":\"Network not allowed\",\"detail\":\"This admin operation is available only from approved control-room IP addresses.\"}");
    }

    private boolean allowed(String ip) {
        if (ip.isBlank()) return false;
        try {
            InetAddress address = InetAddress.getByName(ip);
            return rules.stream().anyMatch(rule -> rule.matches(address));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",", 2)[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr().trim();
    }

    private static boolean sensitive(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (publicCandidateApi(request, path)) {
            return false;
        }
        if (path.equals("/login")
                || path.equals("/api/admin-access/setup-password")
                || path.equals("/api/desktop-console/enroll")
                || path.equals(SecureConsolePaths.ADMIN)
                || path.equals(SecureConsolePaths.MASTER_ADMIN)
                || path.equals(SecureConsolePaths.PLATFORM)
                || path.equals(SecureConsolePaths.MASTER_ADMIN_SETUP)
                || path.equals("/admin.html")
                || path.equals("/admin-7xk92q-portal.html")
                || path.equals("/master-admin.html")
                || path.equals("/platform-control.html")) {
            return true;
        }
        return startsWithOrEquals(path, "/api/admin")
                || startsWithOrEquals(path, "/api/auth")
                || startsWithOrEquals(path, "/api/master-admin")
                || startsWithOrEquals(path, "/api/platform")
                || startsWithOrEquals(path, "/api/exams")
                || startsWithOrEquals(path, "/api/students")
                || startsWithOrEquals(path, "/api/notifications")
                || startsWithOrEquals(path, "/api/responses")
                || startsWithOrEquals(path, "/api/evaluation")
                || startsWithOrEquals(path, "/api/exam-access")
                || startsWithOrEquals(path, "/h2-console");
    }

    private static boolean publicCandidateApi(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if ("POST".equals(method) && (path.equals("/api/students/register") || path.equals("/api/students/register-with-files"))) {
            return true;
        }
        return startsWithOrEquals(path, "/api/candidate")
                || startsWithOrEquals(path, "/api/public")
                || startsWithOrEquals(path, "/candidate-entry")
                || startsWithOrEquals(path, "/candidate-secure")
                || path.equals("/candidate-secure-exam-window")
                || path.equals("/candidate-error.html");
    }

    private static boolean startsWithOrEquals(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static List<IpRule> parseRules(String allowlist) {
        List<IpRule> parsed = new ArrayList<>();
        if (allowlist == null || allowlist.isBlank()) return parsed;
        for (String raw : allowlist.split(",")) {
            String token = raw.trim();
            if (token.isBlank()) continue;
            parsed.add(IpRule.parse(token));
        }
        return List.copyOf(parsed);
    }

    private record IpRule(BigInteger network, BigInteger mask, int byteLength) {
        static IpRule parse(String token) {
            try {
                String[] parts = token.split("/", 2);
                InetAddress address = InetAddress.getByName(parts[0].trim());
                byte[] bytes = address.getAddress();
                int bits = bytes.length * 8;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : bits;
                if (prefix < 0 || prefix > bits) {
                    throw new IllegalArgumentException("Invalid IP prefix length: " + token);
                }
                BigInteger allOnes = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
                BigInteger mask = prefix == 0
                        ? BigInteger.ZERO
                        : allOnes.shiftRight(bits - prefix).shiftLeft(bits - prefix);
                BigInteger value = new BigInteger(1, bytes);
                return new IpRule(value.and(mask), mask, bytes.length);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid admin network allowlist entry: " + token, exception);
            }
        }

        boolean matches(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes.length != byteLength) return false;
            BigInteger value = new BigInteger(1, bytes);
            return value.and(mask).equals(network);
        }
    }
}
