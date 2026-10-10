package com.shashwat.examplatformbackend.notification;

public record SendQueuedEmailResponse(
        int attempted,
        int sent,
        int failed,
        int skipped
) {
}
