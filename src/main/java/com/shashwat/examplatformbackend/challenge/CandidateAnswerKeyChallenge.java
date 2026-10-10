package com.shashwat.examplatformbackend.challenge;

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
@Table(name = "candidate_answer_key_challenges", indexes = {
        @Index(name = "idx_candidate_challenge_exam", columnList = "exam_code,created_at"),
        @Index(name = "idx_candidate_challenge_candidate", columnList = "registration_id,created_at"),
        @Index(name = "idx_candidate_challenge_status", columnList = "status")
})
public class CandidateAnswerKeyChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "registration_id", nullable = false, length = 48)
    private String registrationId;

    @Column(name = "candidate_name", nullable = false, length = 160)
    private String candidateName;

    @Column(name = "question_id", nullable = false, length = 96)
    private String questionId;

    @Column(name = "display_question_id", length = 48)
    private String displayQuestionId;

    @Column(name = "question_number", length = 48)
    private String questionNumber;

    @Column(name = "proof_pdf_path", nullable = false, length = 1200)
    private String proofPdfPath;

    @Column(name = "proof_pdf_name", nullable = false, length = 240)
    private String proofPdfName;

    @Column(name = "proof_image_paths_json", columnDefinition = "TEXT")
    private String proofImagePathsJson;

    @Column(name = "note_text", length = 2400)
    private String noteText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CandidateAnswerKeyChallengeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "review_note", length = 2400)
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 32)
    private CandidateAnswerKeyChallengeResolution resolution;

    protected CandidateAnswerKeyChallenge() {
    }

    public CandidateAnswerKeyChallenge(String examCode,
                                       String registrationId,
                                       String candidateName,
                                       String questionId,
                                       String displayQuestionId,
                                       String questionNumber,
                                       String proofPdfPath,
                                       String proofPdfName,
                                       String proofImagePathsJson,
                                       String noteText) {
        this.examCode = examCode;
        this.registrationId = registrationId;
        this.candidateName = candidateName;
        this.questionId = questionId;
        this.displayQuestionId = clean(displayQuestionId);
        this.questionNumber = clean(questionNumber);
        this.proofPdfPath = proofPdfPath;
        this.proofPdfName = proofPdfName;
        this.proofImagePathsJson = cleanLarge(proofImagePathsJson);
        this.noteText = cleanLarge(noteText);
        this.status = CandidateAnswerKeyChallengeStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void accept(String reviewer, String note) {
        accept(reviewer, note, CandidateAnswerKeyChallengeResolution.MARK_TO_ALL);
    }

    public void accept(String reviewer, String note, CandidateAnswerKeyChallengeResolution resolution) {
        ensurePending();
        this.status = CandidateAnswerKeyChallengeStatus.ACCEPTED;
        this.resolution = resolution;
        this.reviewedBy = clean(reviewer);
        this.reviewNote = cleanLarge(note);
        this.reviewedAt = Instant.now();
    }

    public void reject(String reviewer, String note) {
        ensurePending();
        this.status = CandidateAnswerKeyChallengeStatus.REJECTED;
        this.resolution = CandidateAnswerKeyChallengeResolution.KEEP_CURRENT_KEY;
        this.reviewedBy = clean(reviewer);
        this.reviewNote = cleanLarge(note);
        this.reviewedAt = Instant.now();
    }

    private void ensurePending() {
        if (this.status != CandidateAnswerKeyChallengeStatus.PENDING) {
            throw new IllegalStateException("This challenge is already finalized and can no longer be changed.");
        }
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String cleanLarge(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public Long getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getRegistrationId() { return registrationId; }
    public String getCandidateName() { return candidateName; }
    public String getQuestionId() { return questionId; }
    public String getDisplayQuestionId() { return displayQuestionId; }
    public String getQuestionNumber() { return questionNumber; }
    public String getProofPdfPath() { return proofPdfPath; }
    public String getProofPdfName() { return proofPdfName; }
    public String getProofImagePathsJson() { return proofImagePathsJson; }
    public String getNoteText() { return noteText; }
    public CandidateAnswerKeyChallengeStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
    public CandidateAnswerKeyChallengeResolution getResolution() { return resolution; }
}
