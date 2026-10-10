package com.shashwat.examplatformbackend.notification;

/** Safe, admin-only SMTP diagnostic. It never sends an email or returns credentials. */
public record MailHealthResponse(boolean configured, boolean authenticated, String sender, String message) {
}
