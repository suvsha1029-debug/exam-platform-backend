package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerDrivenExamServiceMsqNormalizationTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void detectsMsqFromNestedAssemblyMetadata() throws Exception {
        JsonNode question = MAPPER.readTree("""
                {
                  "question_type": "MCQ",
                  "selection_meta": { "question_type": "MSQ" },
                  "options": { "A": "One", "B": "Two", "C": "Three", "D": "Four" }
                }
                """);

        assertTrue(isMsq(question));
    }

    @Test
    void detectsMsqFromMultipleCorrectOptionHints() throws Exception {
        JsonNode question = MAPPER.readTree("""
                {
                  "question_type": "QUESTION",
                  "correct_options": ["B", "D"],
                  "options": { "A": "One", "B": "Two", "C": "Three", "D": "Four" }
                }
                """);

        assertTrue(isMsq(question));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsMultipleLetterSelectionsForArrayOptions() throws Exception {
        JsonNode question = MAPPER.readTree("""
                {
                  "question_type": "MSQ",
                  "options": [
                    { "id": "OP_A", "text": "One" },
                    { "id": "OP_B", "text": "Two" },
                    { "id": "OP_C", "text": "Three" },
                    { "id": "OP_D", "text": "Four" }
                  ]
                }
                """);

        Object normalized = normalizeAnswer(question, List.of("A", "C"));
        Method markedAccessor = normalized.getClass().getDeclaredMethod("markedOptionIds");
        markedAccessor.setAccessible(true);

        assertEquals(List.of("OP_A", "OP_C"), (List<String>) markedAccessor.invoke(normalized));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsMultipleMappedSelectionsForRandomizedDisplayOptions() throws Exception {
        JsonNode question = MAPPER.readTree("""
                {
                  "question_type": "MSQ",
                  "options": { "A": "Two", "B": "Four", "C": "One", "D": "Three" },
                  "option_ids": { "A": "OP_B", "B": "OP_D", "C": "OP_A", "D": "OP_C" }
                }
                """);

        Object normalized = normalizeAnswer(question, List.of("A", "B", "D"));
        Method markedAccessor = normalized.getClass().getDeclaredMethod("markedOptionIds");
        markedAccessor.setAccessible(true);

        assertEquals(List.of("OP_B", "OP_D", "OP_C"), (List<String>) markedAccessor.invoke(normalized));
    }

    private static boolean isMsq(JsonNode question) throws Exception {
        Method method = ServerDrivenExamService.class.getDeclaredMethod("isMsqQuestion", JsonNode.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, question);
    }

    private static Object normalizeAnswer(JsonNode question, List<String> markedOptionIds) throws Exception {
        Method method = ServerDrivenExamService.class.getDeclaredMethod(
                "normalizeAnswer",
                JsonNode.class,
                List.class,
                String.class,
                String.class,
                boolean.class
        );
        method.setAccessible(true);
        return method.invoke(null, question, markedOptionIds, "", "answered", false);
    }
}
