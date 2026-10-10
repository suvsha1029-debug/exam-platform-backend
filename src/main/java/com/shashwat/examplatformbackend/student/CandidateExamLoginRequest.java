package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.NotBlank;

public record CandidateExamLoginRequest(
        @NotBlank String registrationId,
        @NotBlank String dateOfBirth
) {
}
