package com.shashwat.examplatformbackend.examaccess;

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
@Table(name = "exam_access_requests", indexes = {
        @Index(name = "idx_exam_access_request_candidate", columnList = "exam_code,registration_id"),
        @Index(name = "idx_exam_access_request_status", columnList = "status")
})
public class ExamAccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "registration_id", nullable = false, length = 48)
    private String registrationId;

    @Column(name = "candidate_name", nullable = false, length = 160)
    private String candidateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExamAccessRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExamAccessRequestStatus status;

    @Column(length = 600)
    private String reason;

    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;

    @Column(name = "attempt_session_id")
    private Long attemptSessionId;

    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 600)
    private String reviewNote;

    @Column(name = "applied_by", length = 160)
    private String appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected ExamAccessRequest() {
    }

    public ExamAccessRequest(String examCode, String registrationId, String candidateName, ExamAccessRequestType type,
                             String reason, Integer remainingSeconds, Long attemptSessionId, String requestedBy) {
        this.examCode = examCode;
        this.registrationId = registrationId;
        this.candidateName = candidateName;
        this.type = type;
        this.status = ExamAccessRequestStatus.PENDING_MASTER_APPROVAL;
        this.reason = reason;
        this.remainingSeconds = remainingSeconds;
        this.attemptSessionId = attemptSessionId;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
    }

    public void approve(String reviewer, String note) {
        requireStatus(ExamAccessRequestStatus.PENDING_MASTER_APPROVAL);
        status = ExamAccessRequestStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = clean(note);
    }

    public void reject(String reviewer, String note) {
        requireStatus(ExamAccessRequestStatus.PENDING_MASTER_APPROVAL);
        status = ExamAccessRequestStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = clean(note);
    }

    public void apply(String admin) {
        requireStatus(ExamAccessRequestStatus.APPROVED);
        status = ExamAccessRequestStatus.APPLIED;
        appliedBy = admin;
        appliedAt = Instant.now();
    }

    public void consume() {
        if (type != ExamAccessRequestType.REJOIN || status != ExamAccessRequestStatus.APPLIED || consumedAt != null) {
            throw new IllegalStateException("Approved rejoin grant is not available.");
        }
        consumedAt = Instant.now();
    }

    private void requireStatus(ExamAccessRequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("This request is already " + status.name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getRegistrationId() { return registrationId; }
    public String getCandidateName() { return candidateName; }
    public ExamAccessRequestType getType() { return type; }
    public ExamAccessRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Integer getRemainingSeconds() { return remainingSeconds; }
    public Long getAttemptSessionId() { return attemptSessionId; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public String getAppliedBy() { return appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
