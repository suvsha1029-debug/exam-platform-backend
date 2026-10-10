package com.shashwat.examplatformbackend.student;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CandidateCustomDataMapper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private CandidateCustomDataMapper() {
    }

    public static Map<String, String> toStringMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(rawJson, MAP_TYPE);
            Map<String, String> safe = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    safe.put(key, String.valueOf(value));
                }
            });
            return safe;
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
