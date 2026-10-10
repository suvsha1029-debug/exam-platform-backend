package com.shashwat.examplatformbackend.student;

public record NativeExamSessionResponse(
        String registrationId,
        String examCode,
        String examName,
        String fullName,
        String photoDataUrl,
        String examUiHeaderTitle,
        String examUiHeaderSubtitle,
        String examUiBannerImageUrl,
        String instituteLogoDataUrl,
        int remainingSeconds,
        boolean candidateSubmitEnabled,
        boolean desktopCaptureProtectionEnabled,
        boolean examReadinessTimerEnabled,
        int examReadinessDurationSeconds,
        String examSessionToken
) {
}
