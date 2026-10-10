package com.shashwat.examplatformbackend.exam;

import jakarta.validation.constraints.NotBlank;

public record ReportCardTemplateRequest(
        @NotBlank String templateJson,
        String templateId,
        String templateName,
        Boolean activate
) {
}
