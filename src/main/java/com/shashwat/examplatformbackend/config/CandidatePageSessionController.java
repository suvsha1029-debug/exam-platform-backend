package com.shashwat.examplatformbackend.config;

import com.shashwat.examplatformbackend.config.CandidatePageSessionService.CandidatePageTarget;
import com.shashwat.examplatformbackend.student.CandidateExamLoginRequest;
import com.shashwat.examplatformbackend.student.ExamViewerSessionResponse;
import com.shashwat.examplatformbackend.student.StudentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Controller
public class CandidatePageSessionController {
    private static final Map<String, String> PAGE_FILES = Map.ofEntries(
            Map.entry("registration", "candidate-registration.html"),
            Map.entry("registration-personal", "candidate-registration-personal.html"),
            Map.entry("registration-education", "candidate-registration-education.html"),
            Map.entry("registration-uploads", "candidate-registration-uploads.html"),
            Map.entry("registration-fee", "candidate-registration-fee.html"),
            Map.entry("registration-preview", "candidate-registration-preview.html"),
            Map.entry("dashboard", "candidate-dashboard.html"),
            Map.entry("details", "candidate-details.html"),
            Map.entry("result", "candidate-result.html"),
            Map.entry("correction", "candidate-correction.html"),
            Map.entry("responses", "candidate-responses.html"),
            Map.entry("exam", "candidate-exam.html"),
            Map.entry("exam-lobby", "candidate-exam-lobby.html")
    );

    private final CandidatePageSessionService sessionService;
    private final StudentService studentService;
    private final CandidateErrorPageService errorPageService;

    public CandidatePageSessionController(CandidatePageSessionService sessionService, StudentService studentService, CandidateErrorPageService errorPageService) {
        this.sessionService = sessionService;
        this.studentService = studentService;
        this.errorPageService = errorPageService;
    }

    @GetMapping("/candidate-entry/{page}")
    public ResponseEntity<?> entry(
            @PathVariable String page,
            @RequestParam(required = false) String registered,
            @RequestParam(required = false) String fresh,
            HttpSession session
    ) {
        String normalized = normalizePage(page);
        if (normalized == null) {
            return errorPageService.response("unknown-page", HttpStatus.NOT_FOUND);
        }
        String token = sessionService.create(session, targetForPage(normalized));
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/candidate-secure/{token}/{file}")
                .queryParamIfPresent("registered", java.util.Optional.ofNullable(registered))
                .queryParamIfPresent("fresh", java.util.Optional.ofNullable(fresh));
        URI location = builder.buildAndExpand(token, PAGE_FILES.get(normalized)).toUri();
        return ResponseEntity.status(302).location(location).build();
    }

    @GetMapping(value = "/candidate-secure/{token}/{fileName}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> securePage(
            @PathVariable String token,
            @PathVariable String fileName,
            @RequestParam Map<String, String> queryParams,
            HttpSession session
    ) {
        if (containsSensitiveQuery(queryParams)) {
            URI cleanLocation = UriComponentsBuilder
                    .fromPath("/candidate-secure/{token}/{file}")
                    .buildAndExpand(token, fileName)
                    .toUri();
            return ResponseEntity.status(302)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .location(cleanLocation)
                    .build();
        }
        String page;
        try {
            page = pageForFile(fileName);
        } catch (IllegalArgumentException exception) {
            return errorPageService.response("unknown-page", HttpStatus.NOT_FOUND);
        }
        CandidatePageTarget target = targetForPage(page);
        if (!sessionService.isAllowed(session, token, target)) {
            return errorPageService.response("invalid-link", HttpStatus.FORBIDDEN);
        }
        String resourceName = fileName.startsWith("candidate-registration")
                ? "candidate-registration.html"
                : fileName;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/" + resourceName));
    }

    @PostMapping("/candidate-entry/exam-launch")
    public ResponseEntity<?> directExamLaunch(
            @RequestParam String registrationId,
            @RequestParam String dateOfBirth
    ) {
        try {
            ExamViewerSessionResponse session = studentService.createExamViewerSession(new CandidateExamLoginRequest(registrationId, dateOfBirth));
            return ResponseEntity.status(302)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .location(URI.create(session.url()))
                    .build();
        } catch (IllegalArgumentException exception) {
            return errorPageService.response("auth-mismatch", HttpStatus.UNAUTHORIZED);
        } catch (IllegalStateException exception) {
            return errorPageService.response("exam-not-ready", HttpStatus.CONFLICT);
        }
    }

    @GetMapping("/candidate-secure-exam-window")
    public ResponseEntity<String> closedExamWindow() {
        return errorPageService.response("session-closed", HttpStatus.GONE);
    }

    @GetMapping({
            "/candidate-registration.html",
            "/candidate-registration-personal.html",
            "/candidate-registration-education.html",
            "/candidate-registration-uploads.html",
            "/candidate-registration-fee.html",
            "/candidate-registration-preview.html",
            "/candidate-dashboard.html",
            "/candidate-details.html",
            "/candidate-result.html",
            "/candidate-correction.html",
            "/candidate-responses.html",
            "/candidate-exam.html",
            "/candidate-exam-lobby.html"
    })
    public ResponseEntity<Void> legacyCandidatePage(
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String registered,
            @RequestParam(required = false) String fresh,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String page = legacyPageName(request.getRequestURI(), step);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/candidate-entry/{page}")
                .queryParamIfPresent("registered", java.util.Optional.ofNullable(registered))
                .queryParamIfPresent("fresh", java.util.Optional.ofNullable(fresh));
        return ResponseEntity.status(302).location(builder.buildAndExpand(page).toUri()).build();
    }

    private static String normalizePage(String page) {
        return PAGE_FILES.containsKey(page) ? page : null;
    }

    private static String pageForFile(String fileName) {
        return PAGE_FILES.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(fileName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown candidate page."));
    }

    private static CandidatePageTarget targetForPage(String page) {
        if (page.startsWith("registration")) {
            return CandidatePageTarget.REGISTRATION;
        }
        if ("details".equals(page) || "result".equals(page) || "correction".equals(page) || "responses".equals(page)) {
            return CandidatePageTarget.DETAILS;
        }
        if ("exam".equals(page) || "exam-lobby".equals(page)) {
            return CandidatePageTarget.EXAM;
        }
        return CandidatePageTarget.DASHBOARD;
    }

    private static String legacyPageName(String uri, String step) {
        if (uri.contains("candidate-registration")) {
            if ("education".equals(step)) return "registration-education";
            if ("uploads".equals(step)) return "registration-uploads";
            if ("fee".equals(step)) return "registration-fee";
            if ("preview".equals(step)) return "registration-preview";
            if (uri.contains("education")) return "registration-education";
            if (uri.contains("uploads")) return "registration-uploads";
            if (uri.contains("fee")) return "registration-fee";
            if (uri.contains("preview")) return "registration-preview";
            if (uri.contains("personal")) return "registration-personal";
            return "registration";
        }
        if (uri.contains("candidate-details")) return "details";
        if (uri.contains("candidate-result")) return "result";
        if (uri.contains("candidate-correction")) return "correction";
        if (uri.contains("candidate-responses")) return "responses";
        if (uri.contains("candidate-exam-lobby")) return "exam-lobby";
        if (uri.contains("candidate-exam")) return "exam";
        return "dashboard";
    }

    private static boolean containsSensitiveQuery(Map<String, String> queryParams) {
        return queryParams.keySet()
                .stream()
                .map(String::toLowerCase)
                .anyMatch(key -> key.equals("registrationid")
                        || key.equals("dateofbirth")
                        || key.equals("dob")
                        || key.equals("password")
                        || key.equals("passcode"));
    }
}
