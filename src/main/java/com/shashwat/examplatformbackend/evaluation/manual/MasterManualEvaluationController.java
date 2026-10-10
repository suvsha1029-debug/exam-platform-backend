package com.shashwat.examplatformbackend.evaluation.manual;

import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.GrantResponse;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.ReviewRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/master-admin/manual-evaluation-requests")
public class MasterManualEvaluationController {
    private final ManualEvaluationService service;

    public MasterManualEvaluationController(ManualEvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public List<GrantResponse> requests() {
        return service.all();
    }

    @PatchMapping("/{id}/approve")
    public GrantResponse approve(@PathVariable Long id,
                                 @RequestBody(required = false) ReviewRequest review,
                                 Authentication authentication) {
        return service.approve(id, review, authentication.getName());
    }

    @PatchMapping("/{id}/reject")
    public GrantResponse reject(@PathVariable Long id,
                                @RequestBody(required = false) ReviewRequest review,
                                Authentication authentication) {
        return service.reject(id, review, authentication.getName());
    }
}
