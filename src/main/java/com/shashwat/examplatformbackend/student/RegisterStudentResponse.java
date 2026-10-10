package com.shashwat.examplatformbackend.student;

public record RegisterStudentResponse(
        String registrationId,
        String examCode,
        String streamCode,
        String fullName,
        String message
) {
}
