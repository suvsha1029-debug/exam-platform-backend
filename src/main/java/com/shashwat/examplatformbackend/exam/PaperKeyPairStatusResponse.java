package com.shashwat.examplatformbackend.exam;

import java.util.List;

public record PaperKeyPairStatusResponse(
        String examCode,
        String examName,
        boolean paperUploaded,
        boolean keyUploaded,
        String questionPaperName,
        String answerKeyName,
        int paperQuestionCount,
        int keyAnswerCount,
        Double keyTotalMarks,
        boolean matched,
        String message,
        String paperLicenseKey,
        String keyLicenseKey,
        List<String> missingInKey,
        List<String> extraInKey
) {
}
