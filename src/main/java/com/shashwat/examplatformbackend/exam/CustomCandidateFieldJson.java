package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public final class CustomCandidateFieldJson {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<CustomCandidateField>> FIELD_LIST = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private CustomCandidateFieldJson() {
    }

    public static String stringify(List<CustomCandidateField> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields == null ? List.of() : fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not store custom candidate fields.");
        }
    }

    public static List<CustomCandidateField> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, FIELD_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public static String stringifyStringList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not store country restrictions.");
        }
    }

    public static List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public static String stringifyStringMap(Map<String, String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? Map.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not store candidate field step configuration.");
        }
    }

    public static Map<String, String> parseStringMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_MAP);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }
}
