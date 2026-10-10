package com.shashwat.examplatformbackend.exam;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PostExamPublicationRequest(
        @NotNull Boolean responsesReleased, @NotNull Boolean resultReleased,
        @NotNull Boolean scoreCardPdfReleased, @NotNull Boolean originalQuestionPaperReleased,
        @NotNull Boolean originalAnswerKeyReleased, @NotNull Boolean answerKeyChallengeEnabled,
        Instant answerKeyChallengeOpensAt, Instant answerKeyChallengeClosesAt) {}
