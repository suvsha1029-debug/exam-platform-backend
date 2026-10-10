package com.shashwat.examplatformbackend.exam;

public record ReportCardTemplateResponse(
        String examCode,
        String templateJson,
        boolean saved,
        String activeTemplateId,
        String activeTemplateName,
        String templatesJson,
        int templateCount
) {
}
