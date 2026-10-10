package com.shashwat.examplatformbackend.response;

import java.time.Instant;

public record ExamSubmitResponse(
        String registrationId,
        String examCode,
        Instant submittedAt,
        int questionCount,
        String message
) {
}
