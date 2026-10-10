package com.shashwat.examplatformbackend.response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "exam_response_drafts",
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_response_draft_candidate", columnNames = {"exam_code", "registration_id"})
)
public class ExamResponseDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "registration_id", nullable = false, length = 48)
    private String registrationId;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(columnDefinition = "text", nullable = false)
    private String answersJson;

    protected ExamResponseDraft() {
    }

    public ExamResponseDraft(String examCode, String registrationId) {
        this.examCode = examCode;
        this.registrationId = registrationId;
    }

    public void replaceAnswers(String answersJson, int questionCount) {
        this.answersJson = answersJson;
        this.questionCount = questionCount;
        this.updatedAt = Instant.now();
    }

    public String getAnswersJson() {
        return answersJson;
    }
}
