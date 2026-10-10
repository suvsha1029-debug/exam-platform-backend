package com.shashwat.examplatformbackend.examaccess;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ExamAccessDtos {
    private ExamAccessDtos() {
    }

    public record CreateRequest(
            @NotBlank String examCode,
            @NotBlank String registrationId,
            @NotNull ExamAccessRequestType type,
            @NotBlank String reason
    ) {
    }

    public record ReviewRequest(String note) {
    }

    public record RequestResponse(
            Long id,
            String examCode,
            String registrationId,
            String candidateName,
            ExamAccessRequestType type,
            ExamAccessRequestStatus status,
            String reason,
            Integer remainingSeconds,
            Long attemptSessionId,
            String requestedBy,
            Instant requestedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewNote,
            String appliedBy,
            Instant appliedAt,
            Instant consumedAt
    ) {
        static RequestResponse from(ExamAccessRequest request) {
            return new RequestResponse(
                    request.getId(), request.getExamCode(), request.getRegistrationId(), request.getCandidateName(),
                    request.getType(), request.getStatus(), request.getReason(), request.getRemainingSeconds(),
                    request.getAttemptSessionId(), request.getRequestedBy(), request.getRequestedAt(),
                    request.getReviewedBy(), request.getReviewedAt(), request.getReviewNote(), request.getAppliedBy(),
                    request.getAppliedAt(), request.getConsumedAt()
            );
        }
    }

    public record IncidentResponse(
            Long attemptSessionId,
            String examCode,
            String registrationId,
            String candidateName,
            Instant interruptedAt,
            Integer remainingSeconds,
            String reason,
            Instant lastHeartbeatAt
    ) {
    }

    public record ExamSessionSignalRequest(@NotBlank String examSessionToken, String reason) {
    }

    public record ResumeContext(int durationSeconds, String draftAnswersJson) {
        public static ResumeContext fresh(int durationSeconds) {
            return new ResumeContext(durationSeconds, "[]");
        }
    }
}
