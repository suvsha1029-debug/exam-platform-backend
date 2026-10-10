package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.NotBlank;

public record CandidateSessionRequest(
        @NotBlank String registrationId
) {
}
