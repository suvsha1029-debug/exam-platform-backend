package com.shashwat.examplatformbackend.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String registrationId,
        @NotBlank String passcode
) {
}
