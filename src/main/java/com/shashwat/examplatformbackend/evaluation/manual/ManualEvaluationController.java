package com.shashwat.examplatformbackend.evaluation.manual;

import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.CreateRequest;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.GrantResponse;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.RunResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation/{examCode}/manual-evaluator")
public class ManualEvaluationController {
    private final ManualEvaluationService service;

    public ManualEvaluationController(ManualEvaluationService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public List<GrantResponse> requests(@PathVariable String examCode) {
        return service.forExam(examCode);
    }

    @PostMapping("/requests")
    public GrantResponse request(@PathVariable String examCode,
                                 @RequestBody CreateRequest request,
                                 Authentication authentication) {
        return service.request(examCode, request, authentication.getName());
    }

    @PostMapping("/requests/{id}/run")
    public RunResponse run(@PathVariable String examCode,
                           @PathVariable Long id,
                           @RequestParam("file") MultipartFile file,
                           Authentication authentication) {
        return service.run(id, examCode, file, authentication.getName());
    }
}
