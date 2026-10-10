package com.shashwat.examplatformbackend.evaluation;

public record SyllabusQuestionMappingResponse(
        String displayQuestionId,
        String questionId,
        String type,
        String topic,
        boolean attempted,
        boolean correct,
        double awardedMarks,
        double maxMarks,
        double confidence,
        String evidence
) {
}
