package com.shashwat.examplatformbackend.response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "exam_attempt_question_states",
        indexes = {
                @Index(name = "idx_attempt_question_state_attempt", columnList = "attempt_id"),
                @Index(name = "idx_attempt_question_state_candidate", columnList = "exam_code, registration_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attempt_question_state_display",
                columnNames = {"attempt_id", "display_index"}
        )
)
public class ExamAttemptQuestionState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "registration_id", nullable = false, length = 48)
    private String registrationId;

    @Column(name = "display_index", nullable = false)
    private int displayIndex;

    @Column(name = "section_name", nullable = false, length = 120)
    private String sectionName;

    @Column(name = "section_index", nullable = false)
    private int sectionIndex;

    @Column(name = "source_index", nullable = false)
    private int sourceIndex;

    @Column(name = "display_question_id", nullable = false, length = 48)
    private String displayQuestionId;

    @Column(name = "question_id", nullable = false, length = 120)
    private String questionId;

    @Column(name = "question_json", nullable = false, columnDefinition = "text")
    private String questionJson;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(name = "marked_option_ids", columnDefinition = "text")
    private String markedOptionIdsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "marked_for_review", nullable = false)
    private boolean markedForReview;

    @Column(name = "time_spent_ms", nullable = false)
    private long timeSpentMs;

    @Column(name = "activity_json", columnDefinition = "text")
    private String activityJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExamAttemptQuestionState() {
    }

    public ExamAttemptQuestionState(
            Long attemptId,
            String examCode,
            String registrationId,
            int displayIndex,
            String sectionName,
            int sectionIndex,
            int sourceIndex,
            String displayQuestionId,
            String questionId,
            String questionJson
    ) {
        this.attemptId = attemptId;
        this.examCode = examCode;
        this.registrationId = registrationId;
        this.displayIndex = displayIndex;
        this.sectionName = sectionName;
        this.sectionIndex = sectionIndex;
        this.sourceIndex = sourceIndex;
        this.displayQuestionId = displayQuestionId;
        this.questionId = questionId;
        this.questionJson = questionJson;
        this.status = "not_visited";
        this.markedForReview = false;
        this.timeSpentMs = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void recordAnswer(String answerText, String markedOptionIdsJson, String status, boolean markedForReview, long timeSpentMs, String activityJson) {
        this.answerText = blankToNull(answerText);
        this.markedOptionIdsJson = markedOptionIdsJson;
        this.status = blankToDefault(status, "not_answered");
        this.markedForReview = markedForReview;
        this.timeSpentMs = Math.max(0L, timeSpentMs);
        this.activityJson = activityJson;
        this.updatedAt = Instant.now();
    }

    public Long getAttemptId() { return attemptId; }
    public String getExamCode() { return examCode; }
    public String getRegistrationId() { return registrationId; }
    public int getDisplayIndex() { return displayIndex; }
    public String getSectionName() { return sectionName; }
    public int getSectionIndex() { return sectionIndex; }
    public int getSourceIndex() { return sourceIndex; }
    public String getDisplayQuestionId() { return displayQuestionId; }
    public String getQuestionId() { return questionId; }
    public String getQuestionJson() { return questionJson; }
    public String getAnswerText() { return answerText; }
    public String getMarkedOptionIdsJson() { return markedOptionIdsJson; }
    public String getStatus() { return status; }
    public boolean isMarkedForReview() { return markedForReview; }
    public long getTimeSpentMs() { return timeSpentMs; }
    public String getActivityJson() { return activityJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
