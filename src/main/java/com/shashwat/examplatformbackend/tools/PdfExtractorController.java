package com.shashwat.examplatformbackend.tools;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf-extractor")
public class PdfExtractorController {
    private final PdfExtractorService pdfExtractorService;

    public PdfExtractorController(PdfExtractorService pdfExtractorService) {
        this.pdfExtractorService = pdfExtractorService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PdfExtractorResponse extract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "viewer") String format,
            @RequestParam(defaultValue = "auto") String profile,
            @RequestParam(defaultValue = "GEN") String subject,
            @RequestParam(defaultValue = "200") int dpi
    ) {
        return pdfExtractorService.extract(file, format, profile, subject, dpi);
    }
}
