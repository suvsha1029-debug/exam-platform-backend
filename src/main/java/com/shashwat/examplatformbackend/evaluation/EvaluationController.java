package com.shashwat.examplatformbackend.evaluation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {
    private final EvaluationService evaluationService;
    private final EvaluationOverrideService overrideService;
    private final ReportCardPdfService reportCardPdfService;

    public EvaluationController(EvaluationService evaluationService, EvaluationOverrideService overrideService, ReportCardPdfService reportCardPdfService) {
        this.evaluationService = evaluationService;
        this.overrideService = overrideService;
        this.reportCardPdfService = reportCardPdfService;
    }

    @GetMapping({
            "/candidate/{applicationNumber}",
            "/candidates/{applicationNumber}",
            "/application/{applicationNumber}"
    })
    public EvaluationCandidateResponse candidate(@PathVariable String applicationNumber) {
        return evaluationService.candidate(applicationNumber);
    }

    @GetMapping("/{examCode}/answer-key")
    public AnswerKeySummaryResponse answerKey(@PathVariable String examCode) {
        return evaluationService.answerKey(examCode);
    }

    @GetMapping("/{examCode}/results")
    public List<EvaluationResultResponse> results(@PathVariable String examCode) {
        return evaluationService.results(examCode);
    }

    @GetMapping("/{examCode}/responses/{responseId}")
    public EvaluationResultResponse responseResult(@PathVariable String examCode, @PathVariable Long responseId) {
        return evaluationService.responseResult(examCode, responseId);
    }

    @GetMapping("/{examCode}/overrides")
    public List<EvaluationOverrideDtos.OverrideResponse> overrides(@PathVariable String examCode) {
        return overrideService.list(examCode);
    }

    @PostMapping("/overrides")
    public EvaluationOverrideDtos.OverrideResponse createOverride(@RequestBody EvaluationOverrideDtos.CreateOverrideRequest request) {
        return overrideService.create(request, "exam-admin");
    }

    @PatchMapping("/overrides/{id}/disable")
    public EvaluationOverrideDtos.OverrideResponse disableOverride(@PathVariable Long id) {
        return overrideService.disable(id);
    }

    @GetMapping("/{examCode}/report-card/{applicationNumber}")
    public ReportCardPreviewResponse reportCardPreview(@PathVariable String examCode, @PathVariable String applicationNumber) {
        return evaluationService.reportCardPreview(examCode, applicationNumber);
    }

    @GetMapping("/{examCode}/score-card-document/{applicationNumber}")
    public ScoreCardDocumentResponse scoreCardDocument(@PathVariable String examCode, @PathVariable String applicationNumber) {
        return reportCardPdfService.createDocument(evaluationService.reportCardPreview(examCode, applicationNumber), true);
    }
}
