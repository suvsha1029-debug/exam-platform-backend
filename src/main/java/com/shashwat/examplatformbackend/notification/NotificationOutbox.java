package com.shashwat.examplatformbackend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;

@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_notification_outbox_status_channel", columnList = "status, channel")
})
public class NotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String recipient;

    @Column(length = 180)
    private String senderEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 1200)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(length = 600)
    private String errorMessage;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(NotificationChannel channel, String recipient, String senderEmail, String subject, String body, NotificationStatus status) {
        this.channel = channel;
        this.recipient = recipient;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void useSenderEmail(String senderEmail) {
        this.senderEmail = cleanError(senderEmail);
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = cleanError(errorMessage);
    }

    public void markSkipped(String errorMessage) {
        this.status = NotificationStatus.SKIPPED;
        this.errorMessage = cleanError(errorMessage);
    }

    private static String cleanError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 600 ? trimmed : trimmed.substring(0, 600);
    }
}
