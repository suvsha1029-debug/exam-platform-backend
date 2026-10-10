package com.shashwat.examplatformbackend.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class EvaluationOverrideDtos {
    private EvaluationOverrideDtos() {
    }

    public record CreateOverrideRequest(
            @NotBlank String examCode,
            @NotBlank String questionId,
            String displayQuestionId,
            @NotNull EvaluationOverrideType type,
            String reason
    ) {
    }

    public record OverrideResponse(
            Long id,
            String examCode,
            String questionId,
            String displayQuestionId,
            EvaluationOverrideType type,
            boolean active,
            String reason,
            String createdBy,
            Instant createdAt
    ) {
        public static OverrideResponse from(EvaluationOverride row) {
            return new OverrideResponse(
                    row.getId(),
                    row.getExamCode(),
                    row.getQuestionId(),
                    row.getDisplayQuestionId(),
                    row.getType(),
                    row.isActive(),
                    row.getReason(),
                    row.getCreatedBy(),
                    row.getCreatedAt()
            );
        }
    }
}
