package com.shashwat.examplatformbackend.platform;

import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestAction;
import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestCreate;
import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestResponse;
import com.shashwat.examplatformbackend.platform.PlatformDtos.PublicRequestResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlatformController {
    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @PostMapping("/api/public/institute-service-requests")
    public PublicRequestResponse create(@Valid @RequestBody InstituteServiceRequestCreate request) {
        return service.createInstituteRequest(request);
    }

    @GetMapping("/api/platform/institute-requests")
    public List<InstituteServiceRequestResponse> list(@RequestParam(defaultValue = "ALL") String status) {
        return service.list(status);
    }

    @PatchMapping("/api/platform/institute-requests/{id}/review")
    public InstituteServiceRequestResponse review(@PathVariable Long id, @Valid @RequestBody InstituteServiceRequestAction action) {
        return service.markUnderReview(id, action);
    }

    @PatchMapping("/api/platform/institute-requests/{id}/approve")
    public InstituteServiceRequestResponse approve(@PathVariable Long id, @Valid @RequestBody InstituteServiceRequestAction action) {
        return service.approve(id, action);
    }

    @PatchMapping("/api/platform/institute-requests/{id}/reject")
    public InstituteServiceRequestResponse reject(@PathVariable Long id, @Valid @RequestBody InstituteServiceRequestAction action) {
        return service.reject(id, action);
    }
}
