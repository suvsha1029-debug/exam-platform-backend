package com.shashwat.examplatformbackend.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonMapperFactory {
    private static final int LARGE_JSON_STRING_LIMIT = 64_000_000;

    private JsonMapperFactory() {
    }

    public static ObjectMapper createLargeJsonMapper() {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(LARGE_JSON_STRING_LIMIT)
                        .build())
                .build();
        return new ObjectMapper(factory);
    }
}
