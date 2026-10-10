package com.shashwat.examplatformbackend.config;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CandidateErrorPageController {
    private final CandidateErrorPageService errorPageService;

    public CandidateErrorPageController(CandidateErrorPageService errorPageService) {
        this.errorPageService = errorPageService;
    }

    @GetMapping(value = "/candidate-error.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> candidateError(@RequestParam(defaultValue = "invalid-link") String reason) {
        return errorPageService.response(reason);
    }
}
