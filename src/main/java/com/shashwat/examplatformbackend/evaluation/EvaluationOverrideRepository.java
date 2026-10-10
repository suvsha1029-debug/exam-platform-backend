package com.shashwat.examplatformbackend.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationOverrideRepository extends JpaRepository<EvaluationOverride, Long> {
    List<EvaluationOverride> findAllByExamCodeOrderByCreatedAtDesc(String examCode);
    List<EvaluationOverride> findAllByExamCodeAndActiveTrueOrderByCreatedAtDesc(String examCode);
    Optional<EvaluationOverride> findFirstByExamCodeAndQuestionIdAndActiveTrueOrderByCreatedAtDesc(String examCode, String questionId);
}
