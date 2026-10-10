package com.shashwat.examplatformbackend.student;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

@Service
public class CandidateFileStorageService {
    private static final long IMAGE_LIMIT_BYTES = 100L * 1024L;
    private static final long DOCUMENT_LIMIT_BYTES = 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> PNG_EXTENSIONS = Set.of(".png");
    private static final Set<String> JPEG_EXTENSIONS = Set.of(".jpg", ".jpeg");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path uploadRoot;

    public CandidateFileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredCandidateFiles store(String registrationId, CandidateRegistrationForm form) {
        try {
            Path candidateDir = uploadRoot.resolve(registrationId).normalize();
            if (!candidateDir.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid candidate upload path.");
            }
            Files.createDirectories(candidateDir);
            return new StoredCandidateFiles(
                    storeFile(candidateDir, "photo", form.getPhoto(), IMAGE_LIMIT_BYTES, IMAGE_TYPES),
                    storeFile(candidateDir, "signature", form.getSignature(), IMAGE_LIMIT_BYTES, IMAGE_TYPES),
                    storeFile(candidateDir, "id-card", form.getIdCard(), DOCUMENT_LIMIT_BYTES, DOCUMENT_TYPES),
                    storeFile(candidateDir, "class-10-marksheet", form.getClass10Marksheet(), DOCUMENT_LIMIT_BYTES, DOCUMENT_TYPES),
                    storeFile(candidateDir, "class-12-marksheet", form.getClass12Marksheet(), DOCUMENT_LIMIT_BYTES, DOCUMENT_TYPES),
                    storeFile(candidateDir, "ug-document", form.getUgDocument(), DOCUMENT_LIMIT_BYTES, DOCUMENT_TYPES),
                    storeFile(candidateDir, "pg-document", form.getPgDocument(), DOCUMENT_LIMIT_BYTES, DOCUMENT_TYPES)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store uploaded files.");
        }
    }

    public void deleteCandidateFolder(String registrationId) {
        Path candidateDir = uploadRoot.resolve(registrationId).normalize();
        if (!candidateDir.startsWith(uploadRoot) || !Files.exists(candidateDir)) {
            return;
        }
        try (var paths = Files.walk(candidateDir)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // File cleanup should not block DB cleanup in this development panel.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    public String storeCustomFile(String registrationId, String key, MultipartFile file, long maxBytes, Set<String> allowedTypes) {
        try {
            Path candidateDir = uploadRoot.resolve(registrationId).normalize();
            if (!candidateDir.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid candidate upload path.");
            }
            Files.createDirectories(candidateDir);
            return storeFile(candidateDir, "custom-" + sanitizeKey(key), file, maxBytes, allowedTypes);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store custom uploaded file.");
        }
    }

    public void deleteStoredFile(String relativePath) {
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot) || target.equals(uploadRoot)) return;
        try { Files.deleteIfExists(target); }
        catch (IOException ignored) { /* A failed cleanup must not hide the original transaction failure. */ }
    }

    private String storeFile(Path candidateDir, String label, MultipartFile file, long maxBytes, Set<String> allowedTypes) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(label + " exceeds allowed size.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? label : file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String normalizedType = normalizeContentType(contentType, originalName);
        if (!allowedTypes.contains(normalizedType)) {
            throw new IllegalArgumentException(label + " must be PDF, JPG, or PNG as allowed for that field.");
        }
        String extension = extension(originalName, normalizedType);
        String storedName = label + "-" + randomSuffix() + extension;
        Path target = candidateDir.resolve(storedName).normalize();
        if (!target.startsWith(candidateDir)) {
            throw new IllegalArgumentException("Invalid file path.");
        }
        try (var stream = file.getInputStream()) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            Files.deleteIfExists(target);
            throw error;
        }
        return uploadRoot.relativize(target).toString().replace('\\', '/');
    }

    private static String normalizeContentType(String contentType, String originalName) {
        if (DOCUMENT_TYPES.contains(contentType) || IMAGE_TYPES.contains(contentType)) {
            return contentType;
        }
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (PDF_EXTENSIONS.stream().anyMatch(lowerName::endsWith)) {
            return "application/pdf";
        }
        if (PNG_EXTENSIONS.stream().anyMatch(lowerName::endsWith)) {
            return "image/png";
        }
        if (JPEG_EXTENSIONS.stream().anyMatch(lowerName::endsWith)) {
            return "image/jpeg";
        }
        return contentType;
    }

    private static String extension(String originalName, String contentType) {
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            return originalName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        }
        if ("application/pdf".equals(contentType)) {
            return ".pdf";
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        return ".jpg";
    }

    private static String randomSuffix() {
        return Long.toUnsignedString(RANDOM.nextLong(), 36);
    }

    private static String sanitizeKey(String value) {
        return String.valueOf(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
