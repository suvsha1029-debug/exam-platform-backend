package com.shashwat.examplatformbackend.evaluation;

public record SyllabusTopicScoreResponse(
        String topic,
        int questionCount,
        int attemptedCount,
        int correctCount,
        int wrongCount,
        int blankCount,
        double awardedMarks,
        double maxMarks,
        double accuracyPercent,
        double confidence
) {
}
