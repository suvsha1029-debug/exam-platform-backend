package com.shashwat.examplatformbackend.exam;

import java.util.List;

public record QuestionPaperMetadataResponse(
        List<SectionMarks> sections
) {
    public record SectionMarks(String name, int questionCount, List<String> marks) {
    }
}
