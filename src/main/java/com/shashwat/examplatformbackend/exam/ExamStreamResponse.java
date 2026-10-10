package com.shashwat.examplatformbackend.exam;

public record ExamStreamResponse(
        String shortCode,
        String fullName,
        boolean enabled
) {
    static ExamStreamResponse from(ExamStream stream) {
        return new ExamStreamResponse(stream.getShortCode(), stream.getFullName(), stream.isEnabled());
    }
}
