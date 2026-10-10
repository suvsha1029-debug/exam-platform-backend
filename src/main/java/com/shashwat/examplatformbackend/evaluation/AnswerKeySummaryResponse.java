package com.shashwat.examplatformbackend.evaluation;

public record AnswerKeySummaryResponse(
        String examCode,
        String examName,
        String originalName,
        int answerCount,
        double maxMarks,
        boolean uploaded
) {
}
