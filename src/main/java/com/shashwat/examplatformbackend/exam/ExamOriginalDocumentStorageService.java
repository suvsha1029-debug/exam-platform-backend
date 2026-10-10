package com.shashwat.examplatformbackend.exam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExamOriginalDocumentStorageService {
    private static final long MAX_PDF_BYTES = 20L * 1024L * 1024L;

    private final Path root;

    public ExamOriginalDocumentStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.root = Path.of(uploadDir).resolve("original-documents").toAbsolutePath().normalize();
    }

    public StoredDocument storePdf(String examCode, String slot, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a PDF file.");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("PDF must be 20 MB or smaller.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? slot + ".pdf" : file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed here.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Only PDF files are allowed here.");
        }
        try {
            Path examFolder = root.resolve(examCode).normalize();
            Files.createDirectories(examFolder);
            Path storedPath = examFolder.resolve(slot + "-" + UUID.randomUUID() + ".pdf").normalize();
            if (!storedPath.startsWith(root)) {
                throw new IllegalArgumentException("Invalid PDF path.");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredDocument(storedPath.toString(), originalName);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not store PDF file.");
        }
    }

    public StoredDocumentData readPdf(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalStateException("Document is not uploaded for this exam.");
        }
        try {
            Path path = Path.of(pathText).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.exists(path)) {
                throw new IllegalStateException("Stored document path is invalid.");
            }
            String contentType = Files.probeContentType(path);
            if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType)) {
                contentType = "application/pdf";
            }
            return new StoredDocumentData(contentType, Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open stored PDF.");
        }
    }

    public record StoredDocument(String path, String originalName) {
    }

    public record StoredDocumentData(String contentType, String base64) {
        public String dataUrl() {
            return "data:" + contentType + ";base64," + base64;
        }
    }
}
