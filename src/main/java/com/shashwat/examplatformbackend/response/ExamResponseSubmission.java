package com.shashwat.examplatformbackend.response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "exam_response_submissions",
        indexes = {
                @Index(name = "idx_exam_response_submissions_exam_code", columnList = "exam_code")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_response_candidate", columnNames = {"exam_code", "registration_id"})
)
public class ExamResponseSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_code", nullable = false, length = 32)
    private String examCode;

    @Column(name = "registration_id", nullable = false, length = 48)
    private String registrationId;

    @Column(nullable = false, length = 48)
    private String applicationNumber;

    @Column(nullable = false, length = 160)
    private String candidateName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private Instant submittedAt;

    @Column(columnDefinition = "text", nullable = false)
    private String answersJson;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "evaluated_matched_question_count")
    private Integer evaluatedMatchedQuestionCount;

    @Column(name = "evaluated_correct_count")
    private Integer evaluatedCorrectCount;

    @Column(name = "evaluated_wrong_count")
    private Integer evaluatedWrongCount;

    @Column(name = "evaluated_blank_count")
    private Integer evaluatedBlankCount;

    @Column(name = "evaluated_score")
    private Double evaluatedScore;

    @Column(name = "evaluated_max_marks")
    private Double evaluatedMaxMarks;

    @Column(name = "evaluated_percentage")
    private Double evaluatedPercentage;

    @Column(name = "evaluation_source", length = 24)
    private String evaluationSource;

    @Column(name = "evaluated_questions_json", columnDefinition = "text")
    private String evaluatedQuestionsJson;

    protected ExamResponseSubmission() {
    }

    public ExamResponseSubmission(String examCode, String registrationId, String applicationNumber, String candidateName, LocalDate dateOfBirth) {
        this.examCode = examCode;
        this.registrationId = registrationId;
        this.applicationNumber = applicationNumber;
        this.candidateName = candidateName;
        this.dateOfBirth = dateOfBirth;
    }

    public void replaceAnswers(String answersJson, int questionCount) {
        this.answersJson = answersJson;
        this.questionCount = questionCount;
        this.submittedAt = Instant.now();
        this.evaluatedAt = null;
        this.evaluatedMatchedQuestionCount = null;
        this.evaluatedCorrectCount = null;
        this.evaluatedWrongCount = null;
        this.evaluatedBlankCount = null;
        this.evaluatedScore = null;
        this.evaluatedMaxMarks = null;
        this.evaluatedPercentage = null;
        this.evaluationSource = null;
        this.evaluatedQuestionsJson = null;
    }

    public Long getId() {
        return id;
    }

    public String getExamCode() {
        return examCode;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public void recordEvaluation(
            int matchedQuestionCount,
            int correctCount,
            int wrongCount,
            int blankCount,
            double score,
            double maxMarks,
            double percentage,
            String evaluationSource,
            String evaluatedQuestionsJson
    ) {
        this.evaluatedAt = Instant.now();
        this.evaluatedMatchedQuestionCount = matchedQuestionCount;
        this.evaluatedCorrectCount = correctCount;
        this.evaluatedWrongCount = wrongCount;
        this.evaluatedBlankCount = blankCount;
        this.evaluatedScore = score;
        this.evaluatedMaxMarks = maxMarks;
        this.evaluatedPercentage = percentage;
        this.evaluationSource = evaluationSource;
        this.evaluatedQuestionsJson = evaluatedQuestionsJson;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public Integer getEvaluatedMatchedQuestionCount() {
        return evaluatedMatchedQuestionCount;
    }

    public Integer getEvaluatedCorrectCount() {
        return evaluatedCorrectCount;
    }

    public Integer getEvaluatedWrongCount() {
        return evaluatedWrongCount;
    }

    public Integer getEvaluatedBlankCount() {
        return evaluatedBlankCount;
    }

    public Double getEvaluatedScore() {
        return evaluatedScore;
    }

    public Double getEvaluatedMaxMarks() {
        return evaluatedMaxMarks;
    }

    public Double getEvaluatedPercentage() {
        return evaluatedPercentage;
    }

    public String getEvaluationSource() {
        return evaluationSource;
    }

    public String getEvaluatedQuestionsJson() {
        return evaluatedQuestionsJson;
    }
}
