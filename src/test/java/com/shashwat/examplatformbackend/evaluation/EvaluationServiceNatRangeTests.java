package com.shashwat.examplatformbackend.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.response.ExamAnswerRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationServiceNatRangeTests {
    private static boolean natMatches(String submitted, String correct) throws Exception {
        Method method = EvaluationService.class.getDeclaredMethod("natMatches", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, submitted, correct);
    }

    @Test
    void signedNatRangesAcceptEitherBoundOrder() throws Exception {
        assertTrue(natMatches("-190", "-189 to -192"));
        assertTrue(natMatches("-190", "-192 to -189"));
        assertTrue(natMatches("-190", "-192--189"));
        assertFalse(natMatches("-188.99", "-189 to -192"));
    }

    @Test
    void singleNegativeNatAnswerIsNotParsedAsRange() throws Exception {
        assertTrue(natMatches("-190", "-190"));
        assertFalse(natMatches("-189", "-190"));
    }

    @Test
    void natRangesAreInclusiveAndSupportPlainHyphenNotation() throws Exception {
        assertTrue(natMatches("412", "412-414"));
        assertTrue(natMatches("414", "412-414"));
        assertTrue(natMatches("413", "414-412"));
        assertFalse(natMatches("414.01", "412-414"));
    }

    @Test
    void natDecimalsCompareByNumericValueNotTextShape() throws Exception {
        assertTrue(natMatches("0.1", "0.10"));
        assertTrue(natMatches("4.100", "4.10-4.50"));
        assertTrue(natMatches("-21", "-21 to -25"));
        assertFalse(natMatches("-25.01", "-21 to -25"));
    }

    @Test
    void signedNatRangesCanAwardMarksByMagnitudeIrrespectiveOfSign() throws Exception {
        assertTrue(natMatches("35", "-36 to -34"));
        assertTrue(natMatches("-35", "34 to 36"));
        assertTrue(natMatches("35", "36 to 34"));
        assertTrue(natMatches("-35", "-34 to -36"));
        assertFalse(natMatches("33.99", "-36 to -34"));
        assertFalse(natMatches("-36.01", "34 to 36"));
    }

    @Test
    void negativeNatAnswerSurvivesResponseJsonStorage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExamAnswerRequest answer = new ExamAnswerRequest(
                "EC",
                "Q1",
                "Q.1",
                "NAT",
                List.of(),
                "-190",
                "answered",
                false,
                Map.of(),
                "",
                "",
                Map.of()
        );

        String json = mapper.writeValueAsString(List.of(answer));
        List<Map<String, Object>> restored = mapper.readValue(json, new TypeReference<>() {
        });

        assertEquals("-190", restored.getFirst().get("natAnswer"));
    }
}
