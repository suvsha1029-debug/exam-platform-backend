package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PaperIdentityNormalizer {
    private PaperIdentityNormalizer() {
    }

    public static JsonNode normalizeQuestionPaper(JsonNode root) {
        if (!(root instanceof ObjectNode source)) {
            return root;
        }
        ObjectNode paper = source.deepCopy();
        ArrayNode questions = array(paper.get("questions"));
        if (questions == null) {
            return paper;
        }
        List<String> identityParts = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            JsonNode node = questions.get(index);
            if (!(node instanceof ObjectNode question)) {
                continue;
            }
            String canonicalQuestionId = canonicalQuestionId(question, index + 1);
            setAliasIfBlank(question, "question_id", canonicalQuestionId);
            setAliasIfBlank(question, "bank_question_id", canonicalQuestionId);
            setAliasIfBlank(question, "source_question_id", canonicalQuestionId);
            setAliasIfBlank(question, "question_number", "Q." + (index + 1));
            ensureOptionIds(question, canonicalQuestionId);
            identityParts.add(canonicalQuestionId);
        }
        stampPaperKeys(paper, identityParts, "paper");
        return paper;
    }

    public static JsonNode normalizeAnswerKey(JsonNode root) {
        if (!(root instanceof ObjectNode source)) {
            return root;
        }
        ObjectNode key = source.deepCopy();
        ArrayNode answers = array(key.get("answers"));
        if (answers == null) {
            return key;
        }
        List<String> identityParts = new ArrayList<>();
        for (int index = 0; index < answers.size(); index++) {
            JsonNode node = answers.get(index);
            if (!(node instanceof ObjectNode answer)) {
                continue;
            }
            String canonicalQuestionId = canonicalQuestionId(answer, index + 1);
            setAliasIfBlank(answer, "question_id", canonicalQuestionId);
            setAliasIfBlank(answer, "bank_question_id", canonicalQuestionId);
            setAliasIfBlank(answer, "source_question_id", canonicalQuestionId);
            setAliasIfBlank(answer, "question_number", "Q." + (index + 1));
            Map<String, String> optionIds = ensureOptionIds(answer, canonicalQuestionId);
            ensureCorrectOptionAliases(answer, optionIds);
            identityParts.add(canonicalQuestionId);
        }
        stampPaperKeys(key, identityParts, "answer_key");
        setAliasIfBlank(key, "source_license_key", text(key, "license_key"));
        setAliasIfBlank(key, "answer_key_instance_key", derivedAnswerKeyInstanceKey(text(key, "license_key")));
        return key;
    }

    private static void ensureCorrectOptionAliases(ObjectNode answer, Map<String, String> optionIds) {
        ArrayNode correctOptions = array(answer.get("correct_options"));
        ArrayNode correctOptionIds = array(answer.get("correct_option_ids"));
        if ((correctOptions == null || correctOptions.isEmpty()) && correctOptionIds != null && !correctOptionIds.isEmpty()) {
            ArrayNode inferred = JsonNodeFactory.instance.arrayNode();
            for (JsonNode optionIdNode : correctOptionIds) {
                String optionId = clean(optionIdNode.asText(""));
                if (optionId.isBlank()) {
                    continue;
                }
                optionIds.entrySet().stream()
                        .filter(entry -> optionId.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .ifPresent(inferred::add);
            }
            if (!inferred.isEmpty()) {
                answer.set("correct_options", inferred);
                correctOptions = inferred;
            }
        }
        if ((correctOptionIds == null || correctOptionIds.isEmpty()) && correctOptions != null && !correctOptions.isEmpty()) {
            ArrayNode inferred = JsonNodeFactory.instance.arrayNode();
            for (JsonNode optionNode : correctOptions) {
                String optionLetter = clean(optionNode.asText("")).toUpperCase(Locale.ROOT);
                String optionId = optionIds.get(optionLetter);
                if (!optionId.isBlank()) {
                    inferred.add(optionId);
                }
            }
            if (!inferred.isEmpty()) {
                answer.set("correct_option_ids", inferred);
            }
        }
    }

    private static void stampPaperKeys(ObjectNode root, List<String> identityParts, String role) {
        String existingLicense = firstNonBlank(
                text(root, "license_key"),
                text(root, "paper_license_key"),
                text(root, "licenseKey"),
                text(root, "paperLicenseKey")
        );
        String license = existingLicense.isBlank() ? derivedLicenseKey(identityParts) : existingLicense;
        if (license.isBlank()) {
            return;
        }
        setAliasIfBlank(root, "license_key", license);
        setAliasIfBlank(root, "paper_license_key", license);
        if (!root.has("license_auth_required")) {
            root.put("license_auth_required", true);
        }
        String createdMicros = firstNonBlank(
                text(root, "license_created_epoch_micros"),
                text(root, "paper_created_epoch_micros"),
                text(root, "created_epoch_micros")
        );
        if (createdMicros.isBlank()) {
            root.put("license_created_epoch_micros", compatibilityMicros(identityParts));
        }
        if ("answer_key".equals(role)) {
            setAliasIfBlank(root, "source_license_key", license);
            setAliasIfBlank(root, "answer_key_instance_key", derivedAnswerKeyInstanceKey(license));
        }
    }

    private static long compatibilityMicros(List<String> identityParts) {
        String hash = sha256Hex(String.join("|", identityParts));
        if (hash.length() < 15) {
            return 0L;
        }
        try {
            return Long.parseLong(hash.substring(0, 15), 16);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String derivedLicenseKey(List<String> identityParts) {
        String hash = sha256Hex(String.join("|", identityParts));
        if (hash.length() < 20) {
            return "";
        }
        return hash.substring(0, 4)
                + "-" + hash.substring(4, 8)
                + "-" + hash.substring(8, 12)
                + "-" + hash.substring(12, 16)
                + "-" + hash.substring(16, 20);
    }

    private static String derivedAnswerKeyInstanceKey(String license) {
        if (license == null || license.isBlank()) {
            return "";
        }
        String suffix = sha256Hex(license + "|answer-key").substring(0, 7).toUpperCase(Locale.ROOT);
        return license + "-" + suffix;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                out.append(String.format("%02x", current));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 support is unavailable.");
        }
    }

    private static Map<String, String> ensureOptionIds(ObjectNode node, String canonicalQuestionId) {
        Map<String, String> existing = existingOptionIds(node);
        List<String> letters = detectedOptionLetters(node, existing);
        if (letters.isEmpty()) {
            return existing;
        }
        if (hasSequentialOptionIds(existing, letters.size())) {
            return existing;
        }
        ObjectNode optionIdsNode = JsonNodeFactory.instance.objectNode();
        Map<String, String> generated = new LinkedHashMap<>();
        for (int index = 0; index < letters.size(); index++) {
            String displayLetter = String.valueOf((char) ('A' + index));
            String current = firstNonBlank(existing.get(displayLetter), canonicalQuestionId + "_OP" + displayLetter + (index + 1));
            optionIdsNode.put(displayLetter, current);
            generated.put(displayLetter, current);
        }
        node.set("option_ids", optionIdsNode);
        return generated;
    }

    private static boolean hasSequentialOptionIds(Map<String, String> existing, int count) {
        if (existing.size() != count) {
            return false;
        }
        for (int index = 0; index < count; index++) {
            String displayLetter = String.valueOf((char) ('A' + index));
            if (clean(existing.get(displayLetter)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> existingOptionIds(ObjectNode node) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode optionIds = node.get("option_ids");
        if (optionIds != null && optionIds.isObject()) {
            optionIds.fields().forEachRemaining(entry -> {
                String key = clean(entry.getKey()).toUpperCase(Locale.ROOT);
                String value = clean(entry.getValue().asText(""));
                if (key.matches("^[A-Z]$") && !value.isBlank()) {
                    out.put(key, value);
                }
            });
        }
        return out;
    }

    private static List<String> detectedOptionLetters(ObjectNode node, Map<String, String> existing) {
        Set<String> letters = new LinkedHashSet<>(existing.keySet());
        JsonNode options = node.get("options");
        if (options != null) {
            if (options.isArray()) {
                for (int index = 0; index < options.size(); index++) {
                    letters.add(String.valueOf((char) ('A' + index)));
                }
            } else if (options.isObject()) {
                options.fieldNames().forEachRemaining(field -> {
                    String letter = clean(field).toUpperCase(Locale.ROOT);
                    if (letter.matches("^[A-Z]$")) {
                        letters.add(letter);
                    }
                });
            }
        }
        for (String letter : List.of("A", "B", "C", "D", "E", "F", "G", "H")) {
            String lower = letter.toLowerCase(Locale.ROOT);
            if (!text(node, "option_" + lower).isBlank()
                    || !text(node, "option" + letter).isBlank()
                    || !text(node, lower).isBlank()
                    || !text(node, letter).isBlank()) {
                letters.add(letter);
            }
        }
        return new ArrayList<>(letters);
    }

    private static String canonicalQuestionId(ObjectNode node, int index) {
        String questionId = firstNonBlank(
                text(node, "question_id"),
                text(node, "questionId"),
                text(node, "id"),
                text(node, "bank_question_id"),
                text(node, "bankQuestionId"),
                text(node, "source_question_id"),
                text(node, "sourceQuestionId")
        );
        if (!questionId.isBlank()) {
            return questionId;
        }
        String prefix = canonicalPrefix(node);
        String fingerprint = sha256Hex(prefix + "|" + index + "|" + firstNonBlank(
                text(node, "question_text"),
                text(node, "questionText"),
                text(node, "body"),
                text(node, "text"),
                text(node, "question")
        )).substring(0, 10);
        return prefix + fingerprint + "QS" + index;
    }

    private static String canonicalPrefix(ObjectNode node) {
        String raw = firstNonBlank(
                text(node, "subject_short"),
                text(node, "subject_code"),
                text(node, "subjectCode"),
                text(node, "section_code"),
                text(node, "sectionCode"),
                text(node, "subject"),
                text(node, "section")
        ).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (raw.isBlank()) {
            raw = "GEN";
        }
        return raw.length() > 6 ? raw.substring(0, 6) : raw;
    }

    private static ArrayNode array(JsonNode node) {
        return node instanceof ArrayNode arrayNode ? arrayNode : null;
    }

    private static String text(ObjectNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return "";
        }
        return clean(value.asText(""));
    }

    private static void setAliasIfBlank(ObjectNode node, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        JsonNode current = node.get(key);
        if (current == null || current.isNull() || current.asText("").trim().isBlank()) {
            node.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!clean(value).isBlank()) {
                return clean(value);
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
