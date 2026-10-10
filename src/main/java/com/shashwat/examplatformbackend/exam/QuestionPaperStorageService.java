package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shashwat.examplatformbackend.common.JsonMapperFactory;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Service
public class QuestionPaperStorageService {
    private static final String[] TOP_LEVEL_SECRET_FIELDS = {
            "answer_key", "answerKey", "answers", "key", "solution", "solutions"
    };
    private static final String[] QUESTION_SECRET_FIELDS = {
            "correct", "correct_answer", "correctAnswer", "answer", "answers",
            "answer_key", "answerKey", "correct_options", "correctOptions",
            "solution", "solutions", "explanation"
    };

    private final Path root;
    private final ObjectMapper objectMapper;

    public QuestionPaperStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.root = Path.of(uploadDir).resolve("question-papers").toAbsolutePath().normalize();
        this.objectMapper = JsonMapperFactory.createLargeJsonMapper();
    }

    public StoredQuestionPaper store(String examCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a question paper JSON file.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "question-paper.json" : file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Question paper must be a .json file exported from the question generator.");
        }
        try {
            JsonNode rootNode;
            try (InputStream input = file.getInputStream()) {
                rootNode = objectMapper.readTree(input);
            }
            JsonNode questions = rootNode.get("questions");
            if (!(questions instanceof ArrayNode arrayNode) || arrayNode.isEmpty()) {
                throw new IllegalArgumentException("Question paper JSON must contain a non-empty questions array.");
            }
            Path examFolder = root.resolve(examCode).normalize();
            Files.createDirectories(examFolder);
            Path storedPath = examFolder.resolve("paper-" + UUID.randomUUID() + ".json").normalize();
            if (!storedPath.startsWith(root)) {
                throw new IllegalArgumentException("Invalid question paper path.");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredQuestionPaper(storedPath.toString(), originalName, arrayNode.size());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read or store question paper JSON.");
        }
    }

    public StoredQuestionPaper storeGenerated(String examCode, JsonNode paper, String originalName) {
        JsonNode questions = paper == null ? null : paper.get("questions");
        if (!(questions instanceof ArrayNode arrayNode) || arrayNode.isEmpty()) {
            throw new IllegalArgumentException("Generated question paper must contain a non-empty questions array.");
        }
        String safeName = StringUtils.cleanPath((originalName == null || originalName.isBlank()) ? "selected-question-paper.json" : originalName);
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            safeName += ".json";
        }
        try {
            Path examFolder = root.resolve(examCode).normalize();
            Files.createDirectories(examFolder);
            Path storedPath = examFolder.resolve("paper-" + UUID.randomUUID() + ".json").normalize();
            if (!storedPath.startsWith(root)) {
                throw new IllegalArgumentException("Invalid question paper path.");
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storedPath.toFile(), paper);
            return new StoredQuestionPaper(storedPath.toString(), safeName, arrayNode.size());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not store generated question paper JSON.");
        }
    }

    public JsonNode read(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalStateException("Question paper is not uploaded for this exam.");
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Stored question paper path is invalid.");
        }
        try {
            return PaperIdentityNormalizer.normalizeQuestionPaper(objectMapper.readTree(path.toFile()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load stored question paper.");
        }
    }

    public void deleteStored(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return;
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Stored question paper path is invalid.");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete stored question paper.");
        }
    }

    public JsonNode sanitizedPaper(Exam exam) {
        if (exam.getQuestionPaperJsonPath() == null) {
            throw new IllegalStateException("Question paper is not uploaded for this exam.");
        }
        Path path = Path.of(exam.getQuestionPaperJsonPath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Stored question paper path is invalid.");
        }
        try {
            JsonNode node = PaperIdentityNormalizer.normalizeQuestionPaper(objectMapper.readTree(path.toFile()));
            if (node instanceof ObjectNode objectNode) {
                removeFields(objectNode, TOP_LEVEL_SECRET_FIELDS);
                JsonNode questions = objectNode.get("questions");
                if (questions instanceof ArrayNode questionArray) {
                    questionArray.forEach(question -> {
                        if (question instanceof ObjectNode questionObject) {
                            removeFields(questionObject, QUESTION_SECRET_FIELDS);
                        }
                    });
                }
            }
            return node;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load stored question paper.");
        }
    }

    public QuestionPaperMetadataResponse metadata(Exam exam) {
        JsonNode node = sanitizedPaper(exam);
        JsonNode questions = node.get("questions");
        Map<String, SectionMetadataBuilder> metadataBySection = new LinkedHashMap<>();
        if (questions instanceof ArrayNode questionArray) {
            questionArray.forEach(question -> {
                String section = detectSection(question);
                if (section.isBlank()) {
                    section = "Section 1";
                }
                String marks = detectMarks(question);
                SectionMetadataBuilder builder = metadataBySection.computeIfAbsent(section, ignored -> new SectionMetadataBuilder());
                builder.questionCount++;
                if (!marks.isBlank() && !builder.marks.contains(marks)) {
                    builder.marks.add(marks);
                }
            });
        }
        List<QuestionPaperMetadataResponse.SectionMarks> sections = metadataBySection.entrySet().stream()
                .map(entry -> {
                    List<String> marks = new ArrayList<>(entry.getValue().marks);
                    marks.sort(markComparator());
                    return new QuestionPaperMetadataResponse.SectionMarks(entry.getKey(), entry.getValue().questionCount, marks);
                })
                .toList();
        return new QuestionPaperMetadataResponse(sections);
    }

    private static void removeFields(ObjectNode node, String[] fields) {
        for (String field : fields) {
            node.remove(field);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String detectSection(JsonNode node) {
        String direct = firstText(node,
                "section", "section_name", "sectionName",
                "subject", "subject_name", "subjectName",
                "section_code", "sectionCode",
                "section_title", "sectionTitle",
                "subject_code", "subjectCode",
                "subject_title", "subjectTitle");
        if (!direct.isBlank()) {
            return direct;
        }
        return deepText(node, 0,
                "section", "section_name", "sectionName",
                "subject", "subject_name", "subjectName",
                "section_code", "sectionCode",
                "section_title", "sectionTitle",
                "subject_code", "subjectCode",
                "subject_title", "subjectTitle");
    }

    private static String detectMarks(JsonNode node) {
        String direct = cleanMarkValue(firstText(node,
                "marks", "max_marks", "maxMarks",
                "positive_marks", "positiveMarks",
                "positive_mark", "positiveMark",
                "question_marks", "questionMarks",
                "maximum_marks", "maximumMarks",
                "weight", "weightage", "score",
                "mark"));
        if (!direct.isBlank()) {
            return direct;
        }
        return cleanMarkValue(deepText(node, 0,
                "marks", "max_marks", "maxMarks",
                "positive_marks", "positiveMarks",
                "positive_mark", "positiveMark",
                "question_marks", "questionMarks",
                "maximum_marks", "maximumMarks",
                "weight", "weightage", "score",
                "mark"));
    }

    private static String deepText(JsonNode node, int depth, String... fields) {
        if (node == null || node.isNull() || depth > 4) {
            return "";
        }
        String direct = firstText(node, fields);
        if (!direct.isBlank()) {
            return direct;
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                String nested = deepText(child, depth + 1, fields);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }
        return "";
    }

    private static String cleanMarkValue(String text) {
        String cleaned = String.valueOf(text == null ? "" : text).trim();
        if (cleaned.isBlank()) {
            return "";
        }
        cleaned = cleaned.replace("Marks:", "")
                .replace("marks:", "")
                .replace("Mark:", "")
                .replace("mark:", "")
                .replace("+", "")
                .replace("M", "")
                .replace("m", "")
                .trim();
        if (cleaned.equalsIgnoreCase("none")
                || cleaned.equalsIgnoreCase("nil")
                || cleaned.equalsIgnoreCase("na")
                || cleaned.equalsIgnoreCase("n/a")) {
            return "0";
        }
        try {
            double value = Double.parseDouble(cleaned);
            if (value == Math.rint(value)) {
                return Integer.toString((int) value);
            }
            return Double.toString(value).replaceAll("\\.?0+$", "");
        } catch (NumberFormatException ignored) {
            return cleaned;
        }
    }

    private static Comparator<String> markComparator() {
        return (left, right) -> {
            Double leftNumber = parseNumber(left);
            Double rightNumber = parseNumber(right);
            if (leftNumber != null && rightNumber != null) {
                int compare = Double.compare(leftNumber, rightNumber);
                if (compare != 0) {
                    return compare;
                }
            } else if (leftNumber != null) {
                return -1;
            } else if (rightNumber != null) {
                return 1;
            }
            return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
        };
    }

    private static Double parseNumber(String value) {
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class SectionMetadataBuilder {
        private int questionCount = 0;
        private final List<String> marks = new ArrayList<>();
    }

    public record StoredQuestionPaper(String path, String originalName, int questionCount) {
    }
}
