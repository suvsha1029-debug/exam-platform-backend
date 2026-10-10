package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CandidatePasswordResetDtos {
    private CandidatePasswordResetDtos() {
    }

    public record ResetRequest(
            @NotBlank String registrationId,
            @NotBlank @Email String email,
            @NotBlank String challengeId,
            @NotBlank String captchaAnswer
    ) {
    }

    public record ResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 200) String newPassword,
            @NotBlank @Size(max = 200) String confirmPassword
    ) {
    }

    public record ResetResponse(String message) {
    }
}
