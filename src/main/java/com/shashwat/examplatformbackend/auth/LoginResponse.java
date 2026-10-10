package com.shashwat.examplatformbackend.auth;

public record LoginResponse(
        boolean authenticated,
        String registrationId,
        String fullName,
        String message
) {
}
