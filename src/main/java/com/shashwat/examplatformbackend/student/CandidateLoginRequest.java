package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.NotBlank;

public record CandidateLoginRequest(
        @NotBlank String registrationId,
        @NotBlank String password
) {
}
