package com.shashwat.examplatformbackend.response;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExamResponseSubmissionEvaluationTests {
    @Test
    void replacingAnswersInvalidatesEveryStoredEvaluationField() {
        ExamResponseSubmission submission = new ExamResponseSubmission(
                "EXAM01", "REG01", "APP01", "Candidate", LocalDate.of(2000, 1, 1));
        submission.recordEvaluation(2, 1, 1, 0, 2.5, 4, 62.5, "MANUAL", "[]");

        submission.replaceAnswers("[]", 0);

        assertNull(submission.getEvaluatedAt());
        assertNull(submission.getEvaluatedScore());
        assertNull(submission.getEvaluationSource());
        assertNull(submission.getEvaluatedQuestionsJson());
        assertEquals(0, submission.getQuestionCount());
    }
}
