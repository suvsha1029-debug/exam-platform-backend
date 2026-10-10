package com.shashwat.examplatformbackend.exam;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateExamRequest(
        @NotBlank String name,
        @NotNull ExamMode mode,
        @Min(1) int durationMinutes,
        Instant examStartAt
) {
}
