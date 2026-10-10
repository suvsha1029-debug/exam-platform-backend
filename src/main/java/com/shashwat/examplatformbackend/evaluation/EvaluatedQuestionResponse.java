package com.shashwat.examplatformbackend.evaluation;

import java.util.List;

public record EvaluatedQuestionResponse(
        String section,
        String questionId,
        String displayQuestionId,
        String type,
        List<String> marked,
        List<String> correct,
        String natAnswer,
        String correctNatAnswer,
        String status,
        boolean attempted,
        boolean correctAnswer,
        double marks,
        double negativeMarks,
        double awardedMarks,
        String resolution
) {
}
