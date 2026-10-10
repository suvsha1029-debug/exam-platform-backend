package com.shashwat.examplatformbackend.exam;

public record CustomCandidateField(
        String key,
        String label,
        CustomCandidateFieldType type,
        boolean required,
        Integer maxSizeKb,
        String step,
        boolean correctionAllowed
) {
}
