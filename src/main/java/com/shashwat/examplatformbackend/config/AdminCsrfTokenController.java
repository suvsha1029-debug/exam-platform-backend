package com.shashwat.examplatformbackend.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCsrfTokenController {
    private final AdminCsrfTokenService tokenService;

    public AdminCsrfTokenController(AdminCsrfTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/api/admin/csrf-token")
    public AdminCsrfTokenResponse token(HttpSession session) {
        return new AdminCsrfTokenResponse(tokenService.token(session));
    }

    public record AdminCsrfTokenResponse(String token) {
    }
}
