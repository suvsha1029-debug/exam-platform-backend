package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamMode;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.response.ServerExamDtos.AnswerRequest;
import com.shashwat.examplatformbackend.response.ServerExamDtos.PaperResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.TokenRequest;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionRepository;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionService;
import com.shashwat.examplatformbackend.student.ExamAttemptToken;
import com.shashwat.examplatformbackend.student.RegisterStudentRequest;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:server-driven-submit-pipeline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload.dir=${java.io.tmpdir}/exam-platform-submit-pipeline-test"
})
@ActiveProfiles("test")
class ServerDrivenExamSubmissionPipelineIntegrationTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path USER_QPAPER = Path.of("C:/Users/LENOVO/Downloads/QS_Project_2026-06-29_17-05_qspaper (3).json");

    @Autowired private ExamRepository examRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ExamAttemptSessionRepository attemptSessionRepository;
    @Autowired private ExamAttemptSessionService attemptSessionService;
    @Autowired private QuestionPaperStorageService questionPaperStorageService;
    @Autowired private ServerDrivenExamService serverDrivenExamService;
    @Autowired private ExamResponseSubmissionRepository submissionRepository;

    @Test
    void submitsUploadedQpaperAndPersistsMcqMsqNatAnswers() throws Exception {
        assumeTrue(Files.exists(USER_QPAPER), "Uploaded qspaper JSON is not present on this machine.");

        String examCode = "PIPE" + System.nanoTime();
        Exam exam = new Exam(examCode, "Pipeline Submit Test", ExamMode.PHYSICAL_CENTER, 30, Instant.now().minusSeconds(60));
        ReflectionTestUtils.setField(exam, "examLobbyEnabled", true);
        ReflectionTestUtils.setField(exam, "examLaunchEnabled", true);
        ReflectionTestUtils.setField(exam, "candidateSubmitEnabled", true);
        ReflectionTestUtils.setField(exam, "optionRandomizationEnabled", false);
        var storedPaper = questionPaperStorageService.store(examCode, new MockMultipartFile(
                "file",
                USER_QPAPER.getFileName().toString(),
                "application/json",
                Files.readAllBytes(USER_QPAPER)
        ));
        exam.attachQuestionPaper(storedPaper.path(), storedPaper.originalName(), storedPaper.questionCount());
        examRepository.saveAndFlush(exam);

        String registrationId = "PIPE" + System.nanoTime();
        Student student = new Student(
                registrationId,
                examCode,
                "EC",
                new RegisterStudentRequest(
                        examCode,
                        "EC",
                        "Pipeline Candidate",
                        LocalDate.of(2000, 1, 1),
                        "pipeline@example.test",
                        null,
                        "9999999999",
                        null,
                        null,
                        null,
                        "IN",
                        "NA",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "StrongPass@123"
                ),
                "{noop}StrongPass@123"
        );
        studentRepository.saveAndFlush(student);

        ExamAttemptToken token = attemptSessionService.issue(student, exam, 1800);
        TokenRequest tokenRequest = new TokenRequest(token.token());

        PaperResponse paper = serverDrivenExamService.paper(tokenRequest);
        assertEquals(21, paper.questionCount());

        QuestionResponse mcq = firstQuestionOfType(paper, "MCQ");
        QuestionResponse msq = firstQuestionOfType(paper, "MSQ");
        QuestionResponse nat = firstQuestionOfType(paper, "NAT");

        serverDrivenExamService.answer(new AnswerRequest(
                token.token(),
                mcq.displayIndex(),
                List.of("B"),
                "",
                "answered",
                false,
                1100,
                Map.of("test", "mcq")
        ));
        var msqAnswer = serverDrivenExamService.answer(new AnswerRequest(
                token.token(),
                msq.displayIndex(),
                List.of("A", "C", "D"),
                "",
                "answered",
                false,
                2200,
                Map.of("test", "msq")
        ));
        assertEquals(List.of("A", "C", "D"), msqAnswer.state().markedOptionIds(), "Answer echo must stay in display letters for the UI.");
        serverDrivenExamService.answer(new AnswerRequest(
                token.token(),
                nat.displayIndex(),
                List.of(),
                "3.14",
                "answered",
                false,
                3300,
                Map.of("test", "nat")
        ));

        ExamSubmitResponse submit = serverDrivenExamService.submit(tokenRequest);
        assertEquals(registrationId, submit.registrationId());
        assertEquals(examCode, submit.examCode());
        assertEquals(21, submit.questionCount());

        ExamResponseSubmission saved = submissionRepository.findByExamCodeAndRegistrationId(examCode, registrationId)
                .orElseThrow();
        assertEquals(21, saved.getQuestionCount());
        assertNotNull(saved.getSubmittedAt());
        assertFalse(attemptSessionRepository.findAllByExamCodeAndRegistrationIdOrderByIssuedAtDesc(examCode, registrationId).getFirst().isUsableAt(Instant.now()));

        JsonNode answers = MAPPER.readTree(saved.getAnswersJson());
        JsonNode savedMcq = answerByQuestionId(answers, mcq.questionId());
        JsonNode savedMsq = answerByQuestionId(answers, msq.questionId());
        JsonNode savedNat = answerByQuestionId(answers, nat.questionId());

        assertEquals(1, savedMcq.path("markedOptionIds").size());
        assertEquals("3.14", savedNat.path("natAnswer").asText());
        assertEquals(3, savedMsq.path("markedOptionIds").size(), savedMsq.toPrettyString());
        assertTrue(savedMsq.path("markedOptionIds").findValuesAsText("").stream().allMatch(value -> value.contains("_OP")), savedMsq.toPrettyString());
    }

    private static QuestionResponse firstQuestionOfType(PaperResponse paper, String type) {
        return paper.paper().stream()
                .filter(question -> question.question().toString().contains("question_type=" + type)
                        || question.question().toString().contains("\"question_type\":\"" + type + "\"")
                        || question.question().toString().contains("question_type\":\"" + type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " question found in paper."));
    }

    private static JsonNode answerByQuestionId(JsonNode answers, String questionId) {
        for (JsonNode answer : answers) {
            if (questionId.equals(answer.path("questionId").asText())) {
                return answer;
            }
        }
        throw new AssertionError("No saved answer for question " + questionId + " in " + answers.toPrettyString());
    }
}
