package com.shashwat.examplatformbackend.evaluation;

import java.util.List;

public record SyllabusAnalysisResponse(
        String examCode,
        String examName,
        String applicationNumber,
        String candidateName,
        String syllabusName,
        int topicCount,
        int mappedQuestionCount,
        int unmappedQuestionCount,
        double score,
        double maxMarks,
        List<SyllabusTopicScoreResponse> topics,
        List<SyllabusQuestionMappingResponse> questions
) {
}
