package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ExamSubmissionResponse(
        Long id,
        String examCode,
        String examName,
        String streamCode,
        Instant examStartAt,
        String registrationId,
        String applicationNumber,
        String candidateName,
        LocalDate dateOfBirth,
        int questionCount,
        Instant submittedAt,
        List<Map<String, Object>> answers
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ANSWER_LIST = new TypeReference<>() {
    };

    public static ExamSubmissionResponse from(ExamResponseSubmission submission) {
        return from(submission, null, null, null, parseAnswers(submission.getAnswersJson()));
    }

    public static ExamSubmissionResponse from(ExamResponseSubmission submission, String examName, String streamCode, Instant examStartAt, List<Map<String, Object>> answers) {
        return new ExamSubmissionResponse(
                submission.getId(),
                submission.getExamCode(),
                examName,
                streamCode,
                examStartAt,
                submission.getRegistrationId(),
                submission.getApplicationNumber(),
                submission.getCandidateName(),
                submission.getDateOfBirth(),
                submission.getQuestionCount(),
                submission.getSubmittedAt(),
                answers == null ? List.of() : answers
        );
    }

    public static List<Map<String, Object>> parseAnswers(String answersJson) {
        try {
            return OBJECT_MAPPER.readValue(answersJson == null ? "[]" : answersJson, ANSWER_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
