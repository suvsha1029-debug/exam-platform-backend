package com.shashwat.examplatformbackend.student;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import com.shashwat.examplatformbackend.common.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/register")
    public RegisterStudentResponse register(@Valid @RequestBody RegisterStudentRequest request) {
        return studentService.register(request);
    }

    @PostMapping(value = "/register-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RegisterStudentResponse registerWithFiles(@Valid @ModelAttribute CandidateRegistrationForm form, MultipartHttpServletRequest request) {
        return studentService.registerWithFiles(form, request);
    }

    @GetMapping
    public List<StudentResponse> list() {
        return studentService.list();
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<StudentResponse> listPage(@RequestParam int page,
                                                  @RequestParam int size,
                                                  @RequestParam(required = false) String examCode) {
        return studentService.listPage(page, size, examCode);
    }

    @GetMapping("/{registrationId}/file")
    public CandidateFileResponse candidateFile(@PathVariable String registrationId, @RequestParam String kind) {
        return studentService.adminCandidateFile(registrationId, kind);
    }

    @DeleteMapping("/{registrationId}")
    public void delete(@PathVariable String registrationId) {
        studentService.delete(registrationId);
    }

    @DeleteMapping
    public void deleteAll() {
        studentService.deleteAll();
    }
}
