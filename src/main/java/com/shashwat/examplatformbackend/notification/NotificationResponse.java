package com.shashwat.examplatformbackend.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationChannel channel,
        String recipient,
        String senderEmail,
        String subject,
        String body,
        NotificationStatus status,
        Instant createdAt,
        Instant sentAt,
        String errorMessage
) {
    static NotificationResponse from(NotificationOutbox notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getChannel(),
                notification.getRecipient(),
                notification.getSenderEmail(),
                notification.getSubject(),
                notification.getBody(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getErrorMessage()
        );
    }
}
