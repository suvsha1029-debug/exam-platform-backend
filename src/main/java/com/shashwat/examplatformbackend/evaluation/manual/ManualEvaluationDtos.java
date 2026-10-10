package com.shashwat.examplatformbackend.evaluation.manual;

import com.shashwat.examplatformbackend.evaluation.EvaluationResultResponse;

import java.time.Instant;
import java.util.List;

public final class ManualEvaluationDtos {
    private ManualEvaluationDtos() {
    }

    public record CreateRequest(String reason) {
    }

    public record ReviewRequest(String note, Integer validMinutes) {
    }

    public record GrantResponse(
            Long id,
            String examCode,
            String reason,
            ManualEvaluationGrantStatus status,
            String requestedBy,
            Instant requestedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewNote,
            Instant expiresAt,
            String consumedBy,
            Instant consumedAt,
            String keyOriginalName,
            String keySha256,
            Integer evaluatedResponseCount,
            boolean usable
    ) {
        public static GrantResponse from(ManualEvaluationGrant grant) {
            return new GrantResponse(
                    grant.getId(), grant.getExamCode(), grant.getReason(), grant.getStatus(),
                    grant.getRequestedBy(), grant.getRequestedAt(), grant.getReviewedBy(), grant.getReviewedAt(),
                    grant.getReviewNote(), grant.getExpiresAt(), grant.getConsumedBy(), grant.getConsumedAt(),
                    grant.getKeyOriginalName(), grant.getKeySha256(), grant.getEvaluatedResponseCount(),
                    grant.isUsable(Instant.now())
            );
        }
    }

    public record RunResponse(
            GrantResponse grant,
            int evaluatedResponses,
            List<EvaluationResultResponse> results
    ) {
    }
}
