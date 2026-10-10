package com.shashwat.examplatformbackend.evaluation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.shashwat.examplatformbackend.evaluation.EvaluationOverrideDtos.CreateOverrideRequest;
import static com.shashwat.examplatformbackend.evaluation.EvaluationOverrideDtos.OverrideResponse;

@Service
public class EvaluationOverrideService {
    private final EvaluationOverrideRepository repository;

    public EvaluationOverrideService(EvaluationOverrideRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> list(String examCode) {
        return repository.findAllByExamCodeOrderByCreatedAtDesc(examCode.trim()).stream()
                .map(OverrideResponse::from)
                .toList();
    }

    @Transactional
    public OverrideResponse create(CreateOverrideRequest request, String actor) {
        EvaluationOverride row = repository.save(new EvaluationOverride(
                request.examCode().trim(),
                request.questionId().trim(),
                request.displayQuestionId(),
                request.type(),
                request.reason(),
                actor
        ));
        return OverrideResponse.from(row);
    }

    @Transactional
    public OverrideResponse replaceActive(String examCode,
                                          String questionId,
                                          String displayQuestionId,
                                          EvaluationOverrideType type,
                                          String reason,
                                          String actor) {
        repository.findFirstByExamCodeAndQuestionIdAndActiveTrueOrderByCreatedAtDesc(examCode.trim(), questionId.trim())
                .ifPresent(EvaluationOverride::disable);
        return OverrideResponse.from(repository.save(new EvaluationOverride(
                examCode.trim(),
                questionId.trim(),
                displayQuestionId,
                type,
                reason,
                actor
        )));
    }

    @Transactional(readOnly = true)
    public Optional<OverrideResponse> active(String examCode, String questionId) {
        return repository.findFirstByExamCodeAndQuestionIdAndActiveTrueOrderByCreatedAtDesc(examCode.trim(), questionId.trim())
                .map(OverrideResponse::from);
    }

    @Transactional
    public OverrideResponse disable(Long id) {
        EvaluationOverride row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation override not found."));
        row.disable();
        return OverrideResponse.from(row);
    }
}
