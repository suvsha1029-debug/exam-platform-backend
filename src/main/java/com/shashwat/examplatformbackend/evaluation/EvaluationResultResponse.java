package com.shashwat.examplatformbackend.evaluation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EvaluationResultResponse(
        Long responseId,
        String examCode,
        String examName,
        String applicationNumber,
        String registrationId,
        String candidateName,
        LocalDate dateOfBirth,
        Instant submittedAt,
        int questionCount,
        int matchedQuestionCount,
        int correctCount,
        int wrongCount,
        int blankCount,
        double score,
        double maxMarks,
        double percentage,
        List<EvaluatedQuestionResponse> questions
) {
}
