package com.shashwat.examplatformbackend.evaluation;

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
@Table(name = "evaluation_overrides", indexes = {
        @Index(name = "idx_evaluation_override_exam", columnList = "exam_code,question_id"),
        @Index(name = "idx_evaluation_override_active", columnList = "active")
})
public class EvaluationOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "question_id", nullable = false, length = 96)
    private String questionId;

    @Column(name = "display_question_id", length = 48)
    private String displayQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvaluationOverrideType type;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 2400)
    private String reason;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EvaluationOverride() {
    }

    public EvaluationOverride(String examCode, String questionId, String displayQuestionId, EvaluationOverrideType type, String reason, String createdBy) {
        this.examCode = examCode;
        this.questionId = questionId;
        this.displayQuestionId = blankToNull(displayQuestionId);
        this.type = type;
        this.reason = blankToNull(reason);
        this.createdBy = blankToNull(createdBy);
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void disable() {
        this.active = false;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public Long getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getQuestionId() { return questionId; }
    public String getDisplayQuestionId() { return displayQuestionId; }
    public EvaluationOverrideType getType() { return type; }
    public boolean isActive() { return active; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
