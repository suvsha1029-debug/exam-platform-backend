package com.shashwat.examplatformbackend.examaccess;

import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.CreateRequest;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.IncidentResponse;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.RequestResponse;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ExamAccessController {
    private final ExamAccessService service;

    public ExamAccessController(ExamAccessService service) {
        this.service = service;
    }

    @GetMapping("/api/exam-access/incidents")
    public List<IncidentResponse> incidents() {
        return service.incidents();
    }

    @GetMapping("/api/exam-access/requests")
    public List<RequestResponse> requests() {
        return service.requests();
    }

    @PostMapping("/api/exam-access/requests")
    public RequestResponse create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        return service.create(request, authentication.getName());
    }

    @PatchMapping("/api/exam-access/requests/{id}/apply")
    public RequestResponse apply(@PathVariable Long id, Authentication authentication) {
        return service.apply(id, authentication.getName());
    }

    @GetMapping("/api/master-admin/exam-access-requests")
    public List<RequestResponse> masterRequests() {
        return service.requests();
    }

    @PatchMapping("/api/master-admin/exam-access-requests/{id}/approve")
    public RequestResponse approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest review,
                                   Authentication authentication) {
        return service.approve(id, authentication.getName(), review == null ? null : review.note());
    }

    @PatchMapping("/api/master-admin/exam-access-requests/{id}/reject")
    public RequestResponse reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest review,
                                  Authentication authentication) {
        return service.reject(id, authentication.getName(), review == null ? null : review.note());
    }
}
