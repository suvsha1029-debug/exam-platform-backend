package com.shashwat.examplatformbackend.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;

@Entity
@Table(name = "exam_attempt_sessions", indexes = {
        @Index(name = "idx_exam_attempt_sessions_expires_at", columnList = "expiresAt")
})
public class ExamAttemptSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String tokenHash;

    @Column(nullable = false, length = 32)
    private String examCode;

    @Column(nullable = false, length = 32)
    private String registrationId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant dueAt;

    @Column
    private Instant lastHeartbeatAt;

    @Column
    private Instant interruptedAt;

    @Column
    private Integer interruptedRemainingSeconds;

    @Column(length = 160)
    private String interruptionReason;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false)
    private boolean submitted;

    protected ExamAttemptSession() {
    }

    public ExamAttemptSession(String tokenHash, String examCode, String registrationId, Instant dueAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.examCode = examCode;
        this.registrationId = registrationId;
        this.issuedAt = Instant.now();
        this.dueAt = dueAt;
        this.expiresAt = expiresAt;
        this.lastHeartbeatAt = Instant.now();
        this.revoked = false;
        this.submitted = false;
    }

    public boolean belongsTo(String examCode, String registrationId) {
        return this.examCode.equals(examCode) && this.registrationId.equals(registrationId);
    }

    public String getExamCode() {
        return examCode;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public Long getId() {
        return id;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getDueAt() {
        return dueAt == null ? expiresAt : dueAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getInterruptedAt() {
        return interruptedAt;
    }

    public Integer getInterruptedRemainingSeconds() {
        return interruptedRemainingSeconds;
    }

    public String getInterruptionReason() {
        return interruptionReason;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public boolean isUsableAt(Instant now) {
        return !revoked && !submitted && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revoked = true;
    }

    public void markSubmitted() {
        this.submitted = true;
    }

    public void heartbeat() {
        this.lastHeartbeatAt = Instant.now();
    }

    public void flagInterruption(String reason) {
        if (submitted || revoked) {
            return;
        }
        Instant now = Instant.now();
        Instant effectiveDueAt = getDueAt();
        this.lastHeartbeatAt = now;
        this.interruptedAt = now;
        this.interruptedRemainingSeconds = (int) Math.max(0, java.time.Duration.between(now, effectiveDueAt).toSeconds());
        this.interruptionReason = reason == null || reason.isBlank() ? "Candidate exam window disconnected." : reason.trim();
    }
}
