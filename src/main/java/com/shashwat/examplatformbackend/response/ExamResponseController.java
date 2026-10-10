package com.shashwat.examplatformbackend.response;

import com.shashwat.examplatformbackend.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/responses")
public class ExamResponseController {
    private final ExamResponseSubmissionService submissionService;

    public ExamResponseController(ExamResponseSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping
    public List<ExamSubmissionResponse> list() {
        return submissionService.list();
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<ExamSubmissionResponse> listPage(@RequestParam int page, @RequestParam int size) {
        return submissionService.listPage(page, size);
    }

    @GetMapping("/{id}")
    public ExamSubmissionResponse get(@PathVariable Long id) {
        return submissionService.get(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        submissionService.delete(id);
    }

    @GetMapping("/exam/{examCode}")
    public List<ExamSubmissionResponse> listByExam(@PathVariable String examCode) {
        return submissionService.listByExam(examCode);
    }

    @GetMapping(value = "/exam/{examCode}", params = {"page", "size"})
    public PageResponse<ExamSubmissionResponse> listByExamPage(@PathVariable String examCode,
                                                               @RequestParam int page,
                                                               @RequestParam int size) {
        return submissionService.listByExamPage(examCode, page, size);
    }
}
