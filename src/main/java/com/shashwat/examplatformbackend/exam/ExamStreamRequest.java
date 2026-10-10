package com.shashwat.examplatformbackend.exam;

import jakarta.validation.constraints.NotBlank;

public record ExamStreamRequest(
        @NotBlank String shortCode,
        @NotBlank String fullName,
        boolean enabled
) {
}
