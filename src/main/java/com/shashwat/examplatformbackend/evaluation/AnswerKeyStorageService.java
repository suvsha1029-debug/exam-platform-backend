package com.shashwat.examplatformbackend.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.common.JsonMapperFactory;
import com.shashwat.examplatformbackend.exam.PaperIdentityNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class AnswerKeyStorageService {
    private static final long MAX_ANSWER_KEY_BYTES = 2L * 1024L * 1024L;

    private final Path root;
    private final ObjectMapper objectMapper;

    public AnswerKeyStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.root = Path.of(uploadDir).resolve("answer-keys").toAbsolutePath().normalize();
        this.objectMapper = JsonMapperFactory.createLargeJsonMapper();
    }

    public StoredAnswerKey store(String examCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload an answer key JSON file.");
        }
        if (file.getSize() > MAX_ANSWER_KEY_BYTES) {
            throw new IllegalArgumentException("Answer key JSON must be 2 MB or smaller.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "answer-key.json" : file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Answer key must be a .json file.");
        }
        try {
            JsonNode key;
            try (InputStream input = file.getInputStream()) {
                key = objectMapper.readTree(input);
            }
            JsonNode answers = key.get("answers");
            if (answers == null || !answers.isArray() || answers.isEmpty()) {
                throw new IllegalArgumentException("Answer key JSON must contain a non-empty answers array.");
            }
            double totalMarks = key.path("total_marks").asDouble(sumMarks(answers));
            Path examFolder = root.resolve(examCode).normalize();
            Files.createDirectories(examFolder);
            Path storedPath = examFolder.resolve("answer-key-" + UUID.randomUUID() + ".json").normalize();
            if (!storedPath.startsWith(root)) {
                throw new IllegalArgumentException("Invalid answer key path.");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredAnswerKey(storedPath.toString(), originalName, answers.size(), totalMarks);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read or store answer key JSON.");
        }
    }

    public StoredAnswerKey storeGenerated(String examCode, JsonNode key, String originalName) {
        JsonNode answers = key == null ? null : key.get("answers");
        if (answers == null || !answers.isArray() || answers.isEmpty()) {
            throw new IllegalArgumentException("Generated answer key JSON must contain a non-empty answers array.");
        }
        String safeName = StringUtils.cleanPath((originalName == null || originalName.isBlank()) ? "selected-answer-key.json" : originalName);
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            safeName += ".json";
        }
        double totalMarks = key.path("total_marks").asDouble(sumMarks(answers));
        try {
            Path examFolder = root.resolve(examCode).normalize();
            Files.createDirectories(examFolder);
            Path storedPath = examFolder.resolve("answer-key-" + UUID.randomUUID() + ".json").normalize();
            if (!storedPath.startsWith(root)) {
                throw new IllegalArgumentException("Invalid answer key path.");
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storedPath.toFile(), key);
            return new StoredAnswerKey(storedPath.toString(), safeName, answers.size(), totalMarks);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not store generated answer key JSON.");
        }
    }

    public JsonNode read(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalStateException("Answer key is not uploaded for this exam.");
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Stored answer key path is invalid.");
        }
        try {
            return PaperIdentityNormalizer.normalizeAnswerKey(objectMapper.readTree(path.toFile()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load stored answer key.");
        }
    }

    public void deleteStored(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return;
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Stored answer key path is invalid.");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete stored answer key.");
        }
    }

    private static double sumMarks(JsonNode answers) {
        double total = 0;
        for (JsonNode answer : answers) {
            total += answer.path("marks").asDouble(0);
        }
        return total;
    }

    public record StoredAnswerKey(String path, String originalName, int answerCount, double totalMarks) {
    }
}
