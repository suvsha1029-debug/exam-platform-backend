package com.shashwat.examplatformbackend.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamMode;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationServiceManualKeyValidationTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void acceptsExactObjectiveIdsAndSignedNatRangeWithoutLicenceMetadata() throws Exception {
        Harness harness = harness();
        JsonNode key = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ",
                   "option_ids":{"A":"EC0001QS1_OPA1","B":"EC0001QS1_OPB2"},
                   "correct_option_ids":["EC0001QS1_OPB2"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"-36 to -34"}
                ]}
                """);

        assertDoesNotThrow(() -> validate(harness, key));
    }

    @Test
    void rejectsMissingActiveQuestionButAllowsExtraMasterKeyRows() throws Exception {
        Harness harness = harness();
        JsonNode missing = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["EC0001QS1_OPA1"]},
                  {"question_id":"EC9999QS9","question_type":"NAT","correct_answer":"1"}
                ]}
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validate(harness, missing));
        assertTrue(error.getMessage().toLowerCase().contains("missing"));

        JsonNode fullMasterKey = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["EC0001QS1_OPA1"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"0.10"},
                  {"question_id":"EC9999QS9","question_type":"NAT","correct_answer":"1"}
                ]}
                """);
        assertDoesNotThrow(() -> validate(harness, fullMasterKey));
    }

    @Test
    void acceptsSourceLicensedMasterKeyForAssembledPaperAndShuffledOptions() throws Exception {
        Path paperPath = tempDir.resolve("question-papers").resolve("ASSEMBLED").resolve("paper.json");
        Files.createDirectories(paperPath.getParent());
        Files.writeString(paperPath, """
                {"license_key":"served-license","source_paper_license_key":"master-license",
                 "paired_paper_license_key":"master-license","paired_answer_key_license_key":"master-key-instance",
                 "questions":[
                  {"question_id":"EC-SERVED-Q1","source_question_id":"EC-MASTER-Q1","question_type":"MCQ",
                   "question_number":"Q.7","marks":3.5,"negative_marks":-1,
                   "option_ids":{"A":"SERVED-A","B":"SERVED-B"},
                   "source_option_ids":{"A":"MASTER-B","B":"MASTER-A"}}
                ]}
                """);
        Exam exam = new Exam("ASSEMBLED", "Assembled evaluation test", ExamMode.ONLINE_REMOTE, 180, Instant.now());
        exam.attachQuestionPaper(paperPath.toString(), "paper.json", 1);
        QuestionPaperStorageService paperStorage = new QuestionPaperStorageService(tempDir.toString());
        EvaluationService service = new EvaluationService(null, null, null, null, paperStorage, null, tempDir.toString());
        Method validator = EvaluationService.class.getDeclaredMethod("validateManualKeyAgainstActivePaper", Exam.class, JsonNode.class);
        validator.setAccessible(true);
        Harness harness = new Harness(service, exam, validator);
        JsonNode key = MAPPER.readTree("""
                {"license_key":"master-license","answer_key_license_key":"master-key-instance","answers":[
                  {"question_id":"EC-MASTER-Q1","question_type":"MCQ",
                   "marks":2,"negative_marks":-0.67,
                   "option_ids":{"A":"MASTER-A","B":"MASTER-B"},"correct_option_ids":["MASTER-A"]},
                  {"question_id":"UNUSED-MASTER-Q2","question_type":"NAT","marks":100,"correct_answer":"5"}
                ]}
                """);

        assertDoesNotThrow(() -> validate(harness, key));

        Object parsed = invokePrivate(service, "parseAnswerKey", new Class<?>[]{Exam.class, JsonNode.class}, exam, key);
        List<?> ordered = (List<?>) invokeAccessor(parsed, "ordered");
        assertEquals(1, ordered.size(), "Only questions actually served may enter evaluation.");
        assertEquals(3.5, (double) invokeAccessor(parsed, "maxMarks"));
        Object parsedQuestion = ordered.getFirst();
        assertEquals("EC-SERVED-Q1", invokeAccessor(parsedQuestion, "questionId"));
        assertEquals("Q.7", invokeAccessor(parsedQuestion, "questionNumber"));
        assertEquals(3.5, (double) invokeAccessor(parsedQuestion, "marks"));
        assertEquals(-1.0, (double) invokeAccessor(parsedQuestion, "negativeMarks"));
        assertEquals(List.of("B"), invokeAccessor(parsedQuestion, "correctOptions"),
                "The master option ID must translate to its shuffled displayed letter.");
    }

    @Test
    void bypassesUnrelatedLicenceOnlyWhenStableQuestionAndOptionIdsMatch() throws Exception {
        Harness harness = harness();
        JsonNode key = MAPPER.readTree("""
                {"license_key":"unrelated-license","answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ",
                   "option_ids":{"A":"EC0001QS1_OPA1","B":"EC0001QS1_OPB2"},
                   "correct_option_ids":["EC0001QS1_OPA1"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"0.10"}
                ]}
                """);
        // The legacy fixture intentionally has no paper licence, so add provenance to its stored paper.
        Path paper = Path.of(harness.exam().getQuestionPaperJsonPath());
        JsonNode stored = MAPPER.readTree(Files.readString(paper));
        ((com.fasterxml.jackson.databind.node.ObjectNode) stored).put("license_key", "expected-license");
        Files.writeString(paper, MAPPER.writeValueAsString(stored));

        assertDoesNotThrow(() -> validate(harness, key));
    }

    @Test
    void rejectsLetterOnlyObjectiveAnswersWithoutStableOptionIds() throws Exception {
        Harness harness = harness();
        JsonNode key = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_options":["A"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"0.10"}
                ]}
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validate(harness, key));
        assertTrue(error.getMessage().contains("Correct option IDs"));
    }

    @Test
    void rejectsDuplicateQuestionIdsAndForeignOptionIds() throws Exception {
        Harness harness = harness();
        JsonNode duplicate = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["EC0001QS1_OPA1"]},
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["EC0001QS1_OPB2"]}
                ]}
                """);
        IllegalArgumentException duplicateError = assertThrows(IllegalArgumentException.class, () -> validate(harness, duplicate));
        assertTrue(duplicateError.getMessage().contains("Duplicate question ID"));

        JsonNode foreignOption = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["FOREIGN_OPTION"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"0.10"}
                ]}
                """);
        IllegalArgumentException optionError = assertThrows(IllegalArgumentException.class, () -> validate(harness, foreignOption));
        assertTrue(optionError.getMessage().contains("Correct option IDs"));
    }

    @Test
    void acceptsDistinctServedIdsThatShareASecondaryBankLineageId() throws Exception {
        Path paperPath = tempDir.resolve("question-papers").resolve("DUPLICATE-LINEAGE").resolve("paper.json");
        Files.createDirectories(paperPath.getParent());
        Files.writeString(paperPath, """
                {"questions":[
                  {"question_id":"SERVED-Q1","bank_question_id":"BANK-DUPLICATE","question_type":"MCQ",
                   "option_ids":{"A":"SERVED-Q1-A","B":"SERVED-Q1-B"}},
                  {"question_id":"SERVED-Q2","bank_question_id":"BANK-DUPLICATE","question_type":"NAT"}
                ]}
                """);
        Exam exam = new Exam("DUPLICATE-LINEAGE", "Duplicate lineage test", ExamMode.ONLINE_REMOTE, 180, Instant.now());
        exam.attachQuestionPaper(paperPath.toString(), "paper.json", 2);
        QuestionPaperStorageService paperStorage = new QuestionPaperStorageService(tempDir.toString());
        EvaluationService service = new EvaluationService(null, null, null, null, paperStorage, null, tempDir.toString());
        Method validator = EvaluationService.class.getDeclaredMethod("validateManualKeyAgainstActivePaper", Exam.class, JsonNode.class);
        validator.setAccessible(true);
        JsonNode key = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"SERVED-Q1","bank_question_id":"BANK-DUPLICATE","question_type":"MCQ",
                   "correct_option_ids":["SERVED-Q1-A"]},
                  {"question_id":"SERVED-Q2","bank_question_id":"BANK-DUPLICATE","question_type":"NAT",
                   "correct_answer":"2.5"}
                ]}
                """);

        assertDoesNotThrow(() -> validate(new Harness(service, exam, validator), key));
    }

    @Test
    void rejectsMalformedNatAnswer() throws Exception {
        Harness harness = harness();
        JsonNode key = MAPPER.readTree("""
                {"answers":[
                  {"question_id":"EC0001QS1","question_type":"MCQ","correct_option_ids":["EC0001QS1_OPA1"]},
                  {"question_id":"EC0001QS2","question_type":"NAT","correct_answer":"about five"}
                ]}
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validate(harness, key));
        assertTrue(error.getMessage().contains("exact signed number or an inclusive range"));
    }

    private Harness harness() throws Exception {
        Path paperPath = tempDir.resolve("question-papers").resolve("EXAM01").resolve("paper.json");
        Files.createDirectories(paperPath.getParent());
        Files.writeString(paperPath, """
                {"questions":[
                  {"question_id":"EC0001QS1","question_type":"MCQ",
                   "option_ids":{"A":"EC0001QS1_OPA1","B":"EC0001QS1_OPB2"},
                   "options":{"A":"First","B":"Second"}},
                  {"question_id":"EC0001QS2","question_type":"NAT"}
                ]}
                """);
        Exam exam = new Exam("EXAM01", "Manual evaluation test", ExamMode.ONLINE_REMOTE, 180, Instant.now());
        exam.attachQuestionPaper(paperPath.toString(), "paper.json", 2);
        QuestionPaperStorageService paperStorage = new QuestionPaperStorageService(tempDir.toString());
        EvaluationService service = new EvaluationService(null, null, null, null, paperStorage, null, tempDir.toString());
        Method validator = EvaluationService.class.getDeclaredMethod("validateManualKeyAgainstActivePaper", Exam.class, JsonNode.class);
        validator.setAccessible(true);
        return new Harness(service, exam, validator);
    }

    private static void validate(Harness harness, JsonNode key) {
        try {
            harness.validator().invoke(harness.service(), harness.exam(), key);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Object invokePrivate(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Object invokeAccessor(Object target, String name) {
        return invokePrivate(target, name, new Class<?>[0]);
    }

    private record Harness(EvaluationService service, Exam exam, Method validator) {
    }
}
