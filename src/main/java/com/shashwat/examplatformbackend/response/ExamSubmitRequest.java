package com.shashwat.examplatformbackend.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExamSubmitRequest(
        @NotBlank String registrationId,
        String dateOfBirth,
        @NotBlank String examSessionToken,
        @NotNull List<ExamAnswerRequest> answers
) {
}
