package com.shashwat.examplatformbackend.tools;

public record PdfExtractorResponse(
        String profileId,
        String format,
        int questionCount,
        Object payload
) {
}
