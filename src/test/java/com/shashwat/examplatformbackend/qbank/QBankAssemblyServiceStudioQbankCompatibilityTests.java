package com.shashwat.examplatformbackend.qbank;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QBankAssemblyServiceStudioQbankCompatibilityTests {

    @Test
    void answersArrayBuildsCompatibilityAnswersFromStudioQs() throws Exception {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode qs = root.putArray("qs");

        ObjectNode mcq = qs.addObject();
        mcq.put("qid", "EC2605002");
        mcq.put("type", "MCQ");
        mcq.put("marks", 2);
        mcq.put("negMarks", -0.67);
        mcq.put("subject", "EC-Electronics & Communication");
        ArrayNode mcqOptions = mcq.putArray("options");
        mcqOptions.addObject().put("oid", "EC2605002011").put("correct", true);
        mcqOptions.addObject().put("oid", "EC2605002022").put("correct", false);
        mcqOptions.addObject().put("oid", "EC2605002033").put("correct", false);
        mcqOptions.addObject().put("oid", "EC2605002044").put("correct", false);
        ArrayNode explicitCorrectIds = mcq.putArray("correctOptionIds");
        explicitCorrectIds.add("EC2605002011");

        ObjectNode nat = qs.addObject();
        nat.put("qid", "EC2605003");
        nat.put("type", "NAT");
        nat.put("marks", 2);
        nat.put("negMarks", -0.67);
        nat.put("subject", "EC-Electronics & Communication");
        nat.put("natAnswer", "-0.90");

        Method method = QBankAssemblyService.class.getDeclaredMethod("answersArray", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        ArrayNode answers = (ArrayNode) method.invoke(null, root);

        assertNotNull(answers);
        assertEquals(2, answers.size());

        ObjectNode mcqAnswer = (ObjectNode) answers.get(0);
        assertEquals("EC2605002", mcqAnswer.path("question_id").asText());
        assertEquals("EC2605002", mcqAnswer.path("bank_question_id").asText());
        assertEquals("A", mcqAnswer.path("correct_options").get(0).asText());
        assertEquals("EC2605002011", mcqAnswer.path("correct_option_ids").get(0).asText());

        ObjectNode natAnswer = (ObjectNode) answers.get(1);
        assertEquals("EC2605003", natAnswer.path("question_id").asText());
        assertEquals("-0.90", natAnswer.path("correct_answer").asText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shuffledOptionsMapSourceAnswerToServedLetterAndServedOptionId() throws Exception {
        ObjectNode sourceAnswer = JsonNodeFactory.instance.objectNode();
        sourceAnswer.putArray("correct_options").add("B");
        List<String> optionOrder = List.of("C", "A", "D", "B");

        Method displayMethod = QBankAssemblyService.class.getDeclaredMethod(
                "displayCorrectOptions",
                com.fasterxml.jackson.databind.JsonNode.class,
                List.class
        );
        displayMethod.setAccessible(true);
        List<String> displayedLetters = (List<String>) displayMethod.invoke(null, sourceAnswer, optionOrder);

        assertEquals(List.of("D"), displayedLetters,
                "Source option B must follow its shuffled content to displayed option D");

        ObjectNode reconstructedAnswer = sourceAnswer.deepCopy();
        reconstructedAnswer.set("correct_options", JsonNodeFactory.instance.arrayNode().addAll(
                displayedLetters.stream().map(JsonNodeFactory.instance::textNode).toList()
        ));
        Map<String, String> servedOptionIds = new LinkedHashMap<>();
        servedOptionIds.put("A", "EC1234567890QS1_OPA1");
        servedOptionIds.put("B", "EC1234567890QS1_OPB2");
        servedOptionIds.put("C", "EC1234567890QS1_OPC3");
        servedOptionIds.put("D", "EC1234567890QS1_OPD4");

        Method idsMethod = QBankAssemblyService.class.getDeclaredMethod(
                "correctOptionIds",
                com.fasterxml.jackson.databind.JsonNode.class,
                Map.class
        );
        idsMethod.setAccessible(true);
        List<String> servedCorrectIds = (List<String>) idsMethod.invoke(null, reconstructedAnswer, servedOptionIds);

        assertEquals(List.of("EC1234567890QS1_OPD4"), servedCorrectIds,
                "Evaluation must use the served option ID, never the original letter or display number");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sourceOptionIdsRecoverCorrectLetterWhenMasterKeyHasNoCorrectOptions() throws Exception {
        ObjectNode sourceAnswer = JsonNodeFactory.instance.objectNode();
        sourceAnswer.putObject("source_option_ids")
                .put("A", "SOURCE-A")
                .put("B", "SOURCE-B")
                .put("C", "SOURCE-C")
                .put("D", "SOURCE-D");
        sourceAnswer.putArray("source_correct_option_ids").add("SOURCE-B");

        Method sourceMethod = QBankAssemblyService.class.getDeclaredMethod(
                "sourceCorrectOptions",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        sourceMethod.setAccessible(true);
        List<String> sourceLetters = (List<String>) sourceMethod.invoke(null, sourceAnswer);

        assertEquals(List.of("B"), sourceLetters);

        Method displayMethod = QBankAssemblyService.class.getDeclaredMethod(
                "displayCorrectOptions",
                List.class,
                List.class
        );
        displayMethod.setAccessible(true);
        List<String> displayedLetters = (List<String>) displayMethod.invoke(
                null,
                sourceLetters,
                List.of("C", "A", "D", "B")
        );

        assertEquals(List.of("D"), displayedLetters);
    }

    @Test
    void placeholderNatAnswerIsRejectedAsUnset() throws Exception {
        Method method = QBankAssemblyService.class.getDeclaredMethod(
                "hasUsableNatAnswer",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);

        ObjectNode unset = JsonNodeFactory.instance.objectNode().put("correct_answer", "(not set)");
        ObjectNode valid = JsonNodeFactory.instance.objectNode().put("correct_answer", "-0.90");

        assertFalse((Boolean) method.invoke(null, unset));
        assertTrue((Boolean) method.invoke(null, valid));
    }

    @Test
    void candidatePaperStripsEveryAnswerAndSolutionField() throws Exception {
        ObjectNode question = JsonNodeFactory.instance.objectNode();
        question.put("answer", "A");
        question.put("correct_answer", "12.5");
        question.putArray("correct_options").add("B");
        question.putArray("correct_option_ids").add("EC1234567890QS1_OPB2");
        question.put("solution", "private solution");
        question.put("explanation", "private explanation");
        question.put("question_text", "Visible candidate content");

        Method method = QBankAssemblyService.class.getDeclaredMethod(
                "removeAnswerFields",
                ObjectNode.class
        );
        method.setAccessible(true);
        method.invoke(null, question);

        assertEquals("Visible candidate content", question.path("question_text").asText());
        assertFalse(question.has("answer"));
        assertFalse(question.has("correct_answer"));
        assertFalse(question.has("correct_options"));
        assertFalse(question.has("correct_option_ids"));
        assertFalse(question.has("solution"));
        assertFalse(question.has("explanation"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateBankIdsKeepEachServedQuestionMappingInOccurrenceOrder() throws Exception {
        Method method = QBankAssemblyService.class.getDeclaredMethod(
                "takeNextBySourceId",
                Map.class,
                Map.class,
                String.class
        );
        method.setAccessible(true);

        Map<String, List<String>> mappings = Map.of(
                "EC2607004", List.of("served-Q1", "served-Q17")
        );
        Map<String, Integer> occurrences = new LinkedHashMap<>();

        assertEquals("served-Q1", method.invoke(null, mappings, occurrences, "EC2607004"));
        assertEquals("served-Q17", method.invoke(null, mappings, occurrences, "EC2607004"));
        assertNull(method.invoke(null, mappings, occurrences, "EC2607004"));
    }
}
