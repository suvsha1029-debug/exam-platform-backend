package com.shashwat.examplatformbackend.exam;

import jakarta.validation.Valid;
import com.shashwat.examplatformbackend.common.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    public ExamResponse create(@Valid @RequestBody CreateExamRequest request) {
        return examService.create(request);
    }

    @GetMapping
    public List<ExamResponse> list() {
        return examService.list();
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<ExamResponse> listPage(@RequestParam int page, @RequestParam int size) {
        return examService.listPage(page, size);
    }

    @PatchMapping("/{examCode}/registration-config")
    public ExamResponse updateRegistrationConfig(@PathVariable String examCode, @Valid @RequestBody UpdateExamConfigRequest request) {
        return examService.updateConfig(examCode, request);
    }

    @PatchMapping("/{examCode}/post-exam-publication")
    public ExamResponse updatePostExamPublication(@PathVariable String examCode, @Valid @RequestBody PostExamPublicationRequest request) {
        return examService.updatePostExamPublication(examCode, request);
    }

    @PostMapping(value = "/{examCode}/portal-banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadPortalBanner(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadPortalBanner(examCode, file);
    }

    @PostMapping(value = "/{examCode}/portal-image/{placement}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadPortalImage(@PathVariable String examCode, @PathVariable String placement, @RequestParam("file") MultipartFile file) {
        return examService.uploadPortalImage(examCode, placement, file);
    }

    @PostMapping(value = "/{examCode}/question-paper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadQuestionPaper(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadQuestionPaper(examCode, file);
    }

    @PostMapping(value = "/{examCode}/answer-key", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadAnswerKey(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadAnswerKey(examCode, file);
    }

    @DeleteMapping("/{examCode}/question-paper")
    public ExamResponse clearQuestionPaper(@PathVariable String examCode) {
        return examService.clearQuestionPaper(examCode);
    }

    @DeleteMapping("/{examCode}/answer-key")
    public ExamResponse clearAnswerKey(@PathVariable String examCode) {
        return examService.clearAnswerKey(examCode);
    }

    @GetMapping(value = "/{examCode}/active-question-paper", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object activeQuestionPaper(@PathVariable String examCode) {
        return examService.activeQuestionPaper(examCode);
    }

    @GetMapping(value = "/{examCode}/active-answer-key", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object activeAnswerKey(@PathVariable String examCode) {
        return examService.activeAnswerKey(examCode);
    }

    @GetMapping("/{examCode}/paper-key-status")
    public PaperKeyPairStatusResponse paperKeyStatus(@PathVariable String examCode) {
        return examService.paperKeyPairStatus(examCode);
    }

    @PostMapping(value = "/{examCode}/original-question-paper-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadOriginalQuestionPaperPdf(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadOriginalQuestionPaperPdf(examCode, file);
    }

    @PostMapping(value = "/{examCode}/original-answer-key-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadOriginalAnswerKeyPdf(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadOriginalAnswerKeyPdf(examCode, file);
    }

    @PostMapping(value = "/{examCode}/portal-notice-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExamResponse uploadPortalNoticePdf(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return examService.uploadPortalNoticePdf(examCode, file);
    }

    @GetMapping("/{examCode}/question-paper-metadata")
    public QuestionPaperMetadataResponse questionPaperMetadata(@PathVariable String examCode) {
        return examService.questionPaperMetadata(examCode);
    }

    @GetMapping("/{examCode}/report-card-template")
    public ReportCardTemplateResponse reportCardTemplate(@PathVariable String examCode) {
        return examService.reportCardTemplate(examCode);
    }

    @PatchMapping("/{examCode}/report-card-template")
    public ReportCardTemplateResponse saveReportCardTemplate(@PathVariable String examCode, @Valid @RequestBody ReportCardTemplateRequest request) {
        return examService.saveReportCardTemplate(examCode, request);
    }

    @PostMapping("/{examCode}/report-card-template")
    public ReportCardTemplateResponse createOrSaveReportCardTemplate(@PathVariable String examCode, @Valid @RequestBody ReportCardTemplateRequest request) {
        return examService.saveReportCardTemplate(examCode, request);
    }

    @PostMapping("/{examCode}/report-card-template/{templateId}/activate")
    public ReportCardTemplateResponse activateReportCardTemplate(@PathVariable String examCode, @PathVariable String templateId) {
        return examService.activateReportCardTemplate(examCode, templateId);
    }

    @DeleteMapping("/{examCode}")
    public void delete(@PathVariable String examCode) {
        examService.delete(examCode);
    }

    @DeleteMapping
    public void deleteAll() {
        examService.deleteAll();
    }
}
