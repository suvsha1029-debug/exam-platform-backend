package com.shashwat.examplatformbackend.qbank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "qbank_assemblies", indexes = {
        @Index(name = "idx_qbank_assemblies_exam", columnList = "examCode"),
        @Index(name = "idx_qbank_assemblies_exam_assembly", columnList = "examCode,assemblyId", unique = true)
})
public class QBankAssemblyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String examCode;

    @Column(nullable = false, length = 64)
    private String assemblyId;

    @Column(nullable = false, length = 64)
    private String qbankId;

    @Column(nullable = false, length = 1200)
    private String questionPaperPath;

    @Column(length = 1200)
    private String answerKeyPath;

    private int questionCount;

    private double totalMarks;

    private int matchedAnswerCount;

    private boolean answerKeyAvailable;

    @Column(columnDefinition = "TEXT")
    private String blueprintJson;

    @Column(columnDefinition = "TEXT")
    private String selectedQuestionsJson;

    /**
     * Server-only source/display identity map. It contains no correct answers and is
     * never returned by an API; it is used to reconstruct a shuffled paper key.
     */
    @Column(columnDefinition = "TEXT")
    private String privateLineageJson;

    @Column(columnDefinition = "TEXT")
    private String questionPaperJson;

    @Column(columnDefinition = "TEXT")
    private String answerKeyJson;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected QBankAssemblyRecord() {
    }

    public QBankAssemblyRecord(String examCode, String assemblyId, String qbankId, String questionPaperPath, String answerKeyPath, int questionCount, double totalMarks, int matchedAnswerCount, boolean answerKeyAvailable, String blueprintJson, String selectedQuestionsJson, String questionPaperJson, String answerKeyJson) {
        this.examCode = examCode;
        this.assemblyId = assemblyId;
        this.qbankId = qbankId;
        this.questionPaperPath = questionPaperPath;
        this.answerKeyPath = answerKeyPath;
        this.questionCount = questionCount;
        this.totalMarks = totalMarks;
        this.matchedAnswerCount = matchedAnswerCount;
        this.answerKeyAvailable = answerKeyAvailable;
        this.blueprintJson = blueprintJson;
        this.selectedQuestionsJson = selectedQuestionsJson;
        this.questionPaperJson = questionPaperJson;
        this.answerKeyJson = answerKeyJson;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public String getExamCode() {
        return examCode;
    }

    public String getAssemblyId() {
        return assemblyId;
    }

    public String getQbankId() {
        return qbankId;
    }

    public String getQuestionPaperPath() {
        return questionPaperPath;
    }

    public String getAnswerKeyPath() {
        return answerKeyPath;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public double getTotalMarks() {
        return totalMarks;
    }

    public int getMatchedAnswerCount() {
        return matchedAnswerCount;
    }

    public boolean isAnswerKeyAvailable() {
        return answerKeyAvailable;
    }

    public String getBlueprintJson() {
        return blueprintJson;
    }

    public String getSelectedQuestionsJson() {
        return selectedQuestionsJson;
    }

    public String getPrivateLineageJson() {
        return privateLineageJson;
    }

    public void setPrivateLineageJson(String privateLineageJson) {
        this.privateLineageJson = privateLineageJson;
    }

    public String getQuestionPaperJson() {
        return questionPaperJson;
    }

    public String getAnswerKeyJson() {
        return answerKeyJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
