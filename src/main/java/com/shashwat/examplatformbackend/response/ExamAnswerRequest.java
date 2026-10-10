package com.shashwat.examplatformbackend.response;

import java.util.List;

public record ExamAnswerRequest(
        String section,
        String questionId,
        String displayQuestionId,
        String type,
        List<String> markedOptionIds,
        String natAnswer,
        String status,
        boolean markedForReview,
        Object activity,
        String questionImage,
        String body,
        Object options
) {
}
