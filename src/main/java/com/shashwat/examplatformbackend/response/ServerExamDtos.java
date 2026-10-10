package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ServerExamDtos {
    private ServerExamDtos() {
    }

    public record TokenRequest(@NotBlank String examSessionToken) {
    }

    public record QuestionRequest(@NotBlank String examSessionToken, int displayIndex) {
    }

    public record AnswerRequest(
            @NotBlank String examSessionToken,
            int displayIndex,
            List<String> markedOptionIds,
            String natAnswer,
            String status,
            boolean markedForReview,
            long timeSpentMs,
            Object activity
    ) {
    }

    public record BootstrapResponse(
            String examCode,
            String registrationId,
            int questionCount,
            Instant dueAt,
            List<SectionSummary> sections,
            List<QuestionSummary> questions
    ) {
    }

    public record PaperResponse(
            String examCode,
            String registrationId,
            int questionCount,
            Instant dueAt,
            List<SectionSummary> sections,
            List<QuestionSummary> questions,
            List<QuestionResponse> paper
    ) {
    }

    public record SectionSummary(String name, int startDisplayIndex, int questionCount) {
    }

    public record QuestionSummary(int displayIndex, String displayQuestionId, String section, String status, boolean markedForReview) {
    }

    public record QuestionResponse(
            int displayIndex,
            String displayQuestionId,
            String section,
            String questionId,
            Object question,
            StateResponse state
    ) {
    }

    public record StateResponse(
            String status,
            boolean markedForReview,
            List<String> markedOptionIds,
            String natAnswer,
            long timeSpentMs,
            Object activity,
            Instant updatedAt
    ) {
    }

    public record AnswerResponse(StateResponse state, Map<String, Long> counts) {
    }
}
