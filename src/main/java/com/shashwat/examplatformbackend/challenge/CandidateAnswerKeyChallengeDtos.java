package com.shashwat.examplatformbackend.challenge;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public final class CandidateAnswerKeyChallengeDtos {
    private CandidateAnswerKeyChallengeDtos() {
    }

    public record CandidateChallengeResponse(
            Long id,
            String examCode,
            String registrationId,
            String candidateName,
            String questionId,
            String displayQuestionId,
            String questionNumber,
            String proofPdfName,
            int proofImageCount,
            String noteText,
            CandidateAnswerKeyChallengeStatus status,
            Instant createdAt,
            Instant reviewedAt,
            String reviewedBy,
            String reviewNote,
            CandidateAnswerKeyChallengeResolution resolution
    ) {
        public static CandidateChallengeResponse from(CandidateAnswerKeyChallenge row, List<String> imagePaths) {
            return new CandidateChallengeResponse(
                    row.getId(),
                    row.getExamCode(),
                    row.getRegistrationId(),
                    row.getCandidateName(),
                    row.getQuestionId(),
                    row.getDisplayQuestionId(),
                    row.getQuestionNumber(),
                    row.getProofPdfName(),
                    imagePaths == null ? 0 : imagePaths.size(),
                    row.getNoteText(),
                    row.getStatus(),
                    row.getCreatedAt(),
                    row.getReviewedAt(),
                    row.getReviewedBy(),
                    row.getReviewNote(),
                    row.getResolution()
            );
        }
    }

    public record CandidateChallengeAssetResponse(
            CandidateChallengeResponse challenge,
            String proofPdfDataUrl,
            List<String> proofImageDataUrls,
            JsonNode servedQuestion,
            ChallengeAnswerKeyView currentKey,
            String activeScoringOverride,
            String proofPdfDownloadUrl,
            List<ChallengeProofDownload> proofImageDownloads
    ) {
    }

    public record ChallengeProofDownload(String fileName, String downloadUrl) {
    }

    public record ChallengeQuestionSummary(
            int rank,
            String questionId,
            String displayQuestionId,
            long challengeCount,
            long candidateCount,
            long pendingCount,
            long acceptedCount,
            long rejectedCount,
            Instant latestChallengeAt,
            Long latestChallengeId
    ) {
    }

    public record ChallengeAnswerKeyView(
            String questionType,
            double marks,
            double negativeMarks,
            List<String> correctOptions,
            List<String> correctOptionIds,
            String correctAnswer
    ) {
    }

    public record CandidateChallengeReviewRequest(
            CandidateAnswerKeyChallengeResolution resolution,
            String reviewNote
    ) {
    }
}
