package com.shashwat.examplatformbackend.exam;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/exams")
public class PublicExamController {
    private final ExamService examService;
    private final PortalBrandingStorageService portalBrandingStorageService;

    public PublicExamController(ExamService examService, PortalBrandingStorageService portalBrandingStorageService) {
        this.examService = examService;
        this.portalBrandingStorageService = portalBrandingStorageService;
    }

    @GetMapping("/open")
    public ResponseEntity<List<ExamPublicResponse>> openExams() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(examService.openRegistrations());
    }

    @GetMapping
    public ResponseEntity<List<ExamPublicResponse>> publicExams() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(examService.publicList());
    }

    @GetMapping("/portal-banner/{examCode}/{filename}")
    public ResponseEntity<Resource> portalBanner(@PathVariable String examCode, @PathVariable String filename) {
        Resource resource = portalBrandingStorageService.loadPortalBanner(examCode, filename);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .contentType(bannerContentType(filename))
                .body(resource);
    }

    private static MediaType bannerContentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping("/{examCode}/notice-document.pdf")
    public ResponseEntity<byte[]> noticeDocument(@PathVariable String examCode) {
        ExamOriginalDocumentStorageService.StoredDocumentData document = examService.portalNoticePdf(examCode);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .body(Base64.getDecoder().decode(document.base64()));
    }
}
