package com.shashwat.examplatformbackend.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidateSecurityTokenController {
    private final CandidateSecurityTokenService tokenService;

    public CandidateSecurityTokenController(CandidateSecurityTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/api/public/candidate-security-token")
    public CandidateSecurityTokenResponse token(HttpSession session) {
        return new CandidateSecurityTokenResponse(tokenService.token(session));
    }

    public record CandidateSecurityTokenResponse(String token) {
    }
}
