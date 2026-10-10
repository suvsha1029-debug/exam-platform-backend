package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.NotBlank;

public record CandidateFileRequest(
        @NotBlank String registrationId,
        String password,
        @NotBlank String path
) {
}
