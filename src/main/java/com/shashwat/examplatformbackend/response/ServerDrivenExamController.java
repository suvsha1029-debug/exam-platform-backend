package com.shashwat.examplatformbackend.response;

import com.shashwat.examplatformbackend.response.ServerExamDtos.AnswerRequest;
import com.shashwat.examplatformbackend.response.ServerExamDtos.AnswerResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.BootstrapResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.PaperResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionRequest;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.TokenRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidate/server-exam")
public class ServerDrivenExamController {
    private final ServerDrivenExamService service;

    public ServerDrivenExamController(ServerDrivenExamService service) {
        this.service = service;
    }

    @PostMapping("/bootstrap")
    public BootstrapResponse bootstrap(@Valid @RequestBody TokenRequest request) {
        return service.bootstrap(request);
    }

    @PostMapping("/paper")
    public PaperResponse paper(@Valid @RequestBody TokenRequest request) {
        return service.paper(request);
    }

    @PostMapping("/question")
    public QuestionResponse question(@Valid @RequestBody QuestionRequest request) {
        return service.question(request);
    }

    @PostMapping("/answer")
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        return service.answer(request);
    }

    @PostMapping("/submit")
    public ExamSubmitResponse submit(@Valid @RequestBody TokenRequest request) {
        return service.submit(request);
    }
}
