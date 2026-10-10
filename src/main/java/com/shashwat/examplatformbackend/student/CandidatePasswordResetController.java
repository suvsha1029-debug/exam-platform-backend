package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.config.CandidateSecurityException;
import com.shashwat.examplatformbackend.config.CandidateSecurityTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidatePasswordResetController {
    private final CandidateCaptchaService captchaService;
    private final CandidatePasswordResetService resetService;
    private final CandidateSecurityTokenService securityTokenService;

    public CandidatePasswordResetController(CandidateCaptchaService captchaService, CandidatePasswordResetService resetService,
                                            CandidateSecurityTokenService securityTokenService) {
        this.captchaService = captchaService;
        this.resetService = resetService;
        this.securityTokenService = securityTokenService;
    }

    @GetMapping(value = "/candidate-entry/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<ClassPathResource> resetPage() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new ClassPathResource("static/candidate-reset-password.html"));
    }

    @GetMapping("/api/public/candidate-password-reset/captcha")
    public CandidateCaptchaService.CaptchaResponse captcha(HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        return captchaService.create(request.getRemoteAddr());
    }

    @PostMapping("/api/public/candidate-password-reset/request")
    public CandidatePasswordResetDtos.ResetResponse requestReset(
            @Valid @RequestBody CandidatePasswordResetDtos.ResetRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response,
            HttpSession session,
            @RequestHeader(value = CandidateSecurityTokenService.HEADER_NAME, required = false) String securityToken
    ) {
        noStore(response);
        requireSecurityToken(session, securityToken);
        return resetService.requestReset(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/api/public/candidate-password-reset/confirm")
    public CandidatePasswordResetDtos.ResetResponse confirmReset(
            @Valid @RequestBody CandidatePasswordResetDtos.ResetConfirmRequest request,
            HttpServletResponse response,
            HttpSession session,
            @RequestHeader(value = CandidateSecurityTokenService.HEADER_NAME, required = false) String securityToken
    ) {
        noStore(response);
        requireSecurityToken(session, securityToken);
        return resetService.confirmReset(request);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

    private void requireSecurityToken(HttpSession session, String securityToken) {
        if (!securityTokenService.matches(session, securityToken)) {
            throw new CandidateSecurityException();
        }
    }
}
