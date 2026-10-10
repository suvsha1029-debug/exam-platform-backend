package com.shashwat.examplatformbackend.qbank;

import java.util.List;
import java.util.Map;

public final class QBankDtos {
    private QBankDtos() {
    }

    public record DifficultyInventory(
            String difficulty,
            int questionCount,
            double totalMarks,
            Map<String, Integer> marks
    ) {
    }

    public record TopicInventory(
            String subject,
            String subjectCode,
            String section,
            String sectionCode,
            String topic,
            String topicCode,
            int questionCount,
            double totalMarks,
            List<DifficultyInventory> difficulties
    ) {
    }

    public record QBankAnalyzeResponse(
            String examCode,
            String qbankId,
            String originalName,
            int questionCount,
            double totalMarks,
            List<String> difficulties,
            List<TopicInventory> topics
    ) {
    }

    public record QBankDefaultKeyResponse(
            String examCode,
            String originalName,
            int answerCount,
            double totalMarks
    ) {
    }

    public record BlueprintTopicRequest(
            String subject,
            String section,
            String topic,
            String topicCode,
            Map<String, Integer> difficultyCounts
    ) {
    }

    public record QBankAssembleRequest(
            String qbankId,
            String seed,
            List<BlueprintTopicRequest> topics,
            boolean randomizeOptions
    ) {
    }

    public record SelectedQuestionSummary(
            String questionId,
            String questionNumber,
            String subject,
            String section,
            String topic,
            String topicCode,
            String difficulty,
            double marks
    ) {
    }

    public record QBankAssemblyResponse(
            String examCode,
            String assemblyId,
            String qbankId,
            int questionCount,
            double totalMarks,
            int matchedAnswerCount,
            boolean answerKeyAvailable,
            String questionPaperDownloadUrl,
            String answerKeyDownloadUrl,
            List<SelectedQuestionSummary> selectedQuestions
    ) {
    }

    public record QBankAssemblyHistoryResponse(
            String examCode,
            String assemblyId,
            String qbankId,
            int questionCount,
            double totalMarks,
            int matchedAnswerCount,
            boolean answerKeyAvailable,
            String createdAt,
            String publishedAt,
            List<SelectedQuestionSummary> selectedQuestions
    ) {
    }

    public record QBankAggregatedKeyResponse(
            String examCode,
            String assemblyId,
            int questionCount,
            int matchedAnswerCount,
            double totalMarks,
            String answerKeyDownloadUrl,
            String message
    ) {
    }

    public record QBankPublishResponse(
            String examCode,
            String assemblyId,
            int questionCount,
            double totalMarks,
            boolean questionPaperPublished,
            boolean answerKeyPublished,
            String message
    ) {
    }
}
