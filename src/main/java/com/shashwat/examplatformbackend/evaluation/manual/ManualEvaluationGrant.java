package com.shashwat.examplatformbackend.evaluation.manual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "manual_evaluation_grants", indexes = {
        @Index(name = "idx_manual_evaluation_exam", columnList = "exam_code"),
        @Index(name = "idx_manual_evaluation_status", columnList = "status")
})
public class ManualEvaluationGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(nullable = false, length = 800)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ManualEvaluationGrantStatus status;

    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 800)
    private String reviewNote;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "consumed_by", length = 160)
    private String consumedBy;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "key_original_name", length = 255)
    private String keyOriginalName;

    @Column(name = "key_sha256", length = 64)
    private String keySha256;

    @Column(name = "evaluated_response_count")
    private Integer evaluatedResponseCount;

    protected ManualEvaluationGrant() {
    }

    public ManualEvaluationGrant(String examCode, String reason, String requestedBy) {
        this.examCode = examCode;
        this.reason = reason;
        this.status = ManualEvaluationGrantStatus.PENDING_MASTER_APPROVAL;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
    }

    public void approve(String reviewer, String note, int validMinutes) {
        requireStatus(ManualEvaluationGrantStatus.PENDING_MASTER_APPROVAL);
        status = ManualEvaluationGrantStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = clean(note);
        expiresAt = reviewedAt.plusSeconds(validMinutes * 60L);
    }

    public void reject(String reviewer, String note) {
        requireStatus(ManualEvaluationGrantStatus.PENDING_MASTER_APPROVAL);
        status = ManualEvaluationGrantStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = clean(note);
    }

    public void assertUsable(String requestedExamCode, Instant now) {
        if (!examCode.equals(requestedExamCode)) {
            throw new IllegalStateException("Manual evaluation grant does not belong to the selected exam.");
        }
        if (status != ManualEvaluationGrantStatus.APPROVED || consumedAt != null) {
            throw new IllegalStateException("Manual evaluation grant is not approved or has already been consumed.");
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("Manual evaluation grant has expired. Request fresh master approval.");
        }
    }

    public void consume(String actor, String originalName, String sha256, int responseCount) {
        assertUsable(examCode, Instant.now());
        status = ManualEvaluationGrantStatus.CONSUMED;
        consumedBy = actor;
        consumedAt = Instant.now();
        keyOriginalName = clean(originalName);
        keySha256 = sha256;
        evaluatedResponseCount = responseCount;
    }

    public boolean isUsable(Instant now) {
        return status == ManualEvaluationGrantStatus.APPROVED
                && consumedAt == null
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    private void requireStatus(ManualEvaluationGrantStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("This manual evaluation request is already "
                    + status.name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getReason() { return reason; }
    public ManualEvaluationGrantStatus getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getConsumedBy() { return consumedBy; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getKeyOriginalName() { return keyOriginalName; }
    public String getKeySha256() { return keySha256; }
    public Integer getEvaluatedResponseCount() { return evaluatedResponseCount; }
}
