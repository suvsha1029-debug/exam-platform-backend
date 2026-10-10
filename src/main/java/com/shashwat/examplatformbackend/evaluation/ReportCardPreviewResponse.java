package com.shashwat.examplatformbackend.evaluation;

import java.util.Map;

public record ReportCardPreviewResponse(
        String examCode,
        String examName,
        String applicationNumber,
        String registrationId,
        String candidateName,
        String dateOfBirth,
        String streamCode,
        Double score,
        Double maxMarks,
        Double percentage,
        Integer correctCount,
        Integer wrongCount,
        Integer blankCount,
        Integer rank,
        Integer candidateCount,
        String submittedAt,
        String examConductedOn,
        String photoDataUrl,
        String signatureDataUrl,
        String instituteLogoDataUrl,
        String watermarkLogoDataUrl,
        String authoritySignatureDataUrl,
        String authorityName,
        String authorityDesignation,
        String validityText,
        Map<String, String> placeholders
) {
}
