package com.shashwat.examplatformbackend.exam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

@Service
public class PortalBrandingStorageService {
    private static final long BANNER_LIMIT_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SAFE_PATH_PART = "[A-Za-z0-9._-]+";

    private final Path uploadRoot;

    public PortalBrandingStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storePortalImage(String examCode, String placement, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a portal image first.");
        }
        if (file.getSize() > BANNER_LIMIT_BYTES) {
            throw new IllegalArgumentException("Portal image must be 2 MB or smaller.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Portal image must be JPG, PNG, or WEBP.");
        }
        try {
            Path bannerDir = uploadRoot.resolve("portal-branding").resolve(examCode).normalize();
            if (!bannerDir.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid exam code for banner upload.");
            }
            Files.createDirectories(bannerDir);
            String safePlacement = placement.replaceAll("[^a-z-]", "");
            String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? safePlacement : file.getOriginalFilename());
            String storedName = safePlacement + "-" + randomSuffix() + extension(originalName, contentType);
            Path target = bannerDir.resolve(storedName).normalize();
            if (!target.startsWith(bannerDir)) {
                throw new IllegalArgumentException("Invalid banner file path.");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/api/public/exams/portal-banner/" + examCode + "/" + storedName;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store portal banner image.");
        }
    }

    public Resource loadPortalBanner(String examCode, String filename) {
        try {
            String safeExamCode = safePathPart(examCode);
            String safeFilename = safePathPart(filename);
            Path bannerDir = uploadRoot.resolve("portal-branding").resolve(safeExamCode).normalize();
            Path target = bannerDir.resolve(safeFilename).normalize();
            if (!bannerDir.startsWith(uploadRoot) || !target.startsWith(bannerDir) || !Files.exists(target)) {
                throw new IllegalArgumentException("Portal banner image not found.");
            }
            return new UrlResource(target.toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Portal banner image not found.");
        }
    }

    private static String extension(String originalName, String contentType) {
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            return originalName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String randomSuffix() {
        return Long.toUnsignedString(RANDOM.nextLong(), 36);
    }

    private static String safePathPart(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (!text.matches(SAFE_PATH_PART) || text.contains("..")) {
            throw new IllegalArgumentException("Portal banner image not found.");
        }
        return text;
    }
}
