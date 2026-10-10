package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.exam.*;
import com.shashwat.examplatformbackend.evaluation.SyllabusAnalysisService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ResultControlTests {
    @Autowired StudentRepository students;
    @Autowired ResultControlService controls;
    @Autowired StudentService studentService;
    @Autowired SyllabusAnalysisService analysis;
    @Autowired ExamRepository exams;
    @Autowired ExamService examService;
    @Autowired EntityManager entityManager;

    private Student candidate(String examCode) {
        Student student = new Student();
        ReflectionTestUtils.setField(student, "registrationId", "HOLD" + System.nanoTime());
        ReflectionTestUtils.setField(student, "examCode", examCode);
        ReflectionTestUtils.setField(student, "fullName", "Sample Candidate");
        ReflectionTestUtils.setField(student, "dateOfBirth", LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(student, "createdAt", Instant.now());
        ReflectionTestUtils.setField(student, "passcodeHash", "unused-test-hash");
        return students.saveAndFlush(student);
    }
    @Test
    void holdPersistsBlocksEveryResultRouteAndCanBeRestoredWithAudit() {
        String code = "RES" + System.nanoTime();
        Exam exam = new Exam(code, "Result Test", ExamMode.PHYSICAL_CENTER, 180, Instant.now());
        ReflectionTestUtils.setField(exam, "resultReleased", true);
        ReflectionTestUtils.setField(exam, "scoreCardPdfReleased", true);
        exams.saveAndFlush(exam);
        String id = candidate(code).getRegistrationId();
        assertFalse(controls.list(code, id, false, 0).content().getFirst().withheld());
        controls.decide(id, true, false, "Identity verification pending", "master@example.test");
        entityManager.flush(); entityManager.clear();
        assertTrue(students.findByRegistrationId(id).orElseThrow().isResultWithheld());
        var dashboard = studentService.candidateDashboardForSession(id);
        assertTrue(dashboard.resultWithheld());
        assertFalse(dashboard.resultReleased());
        assertFalse(dashboard.scoreCardPdfReleased());
        var request = new CandidateSessionRequest(id);
        assertTrue(assertThrows(IllegalStateException.class, () -> studentService.candidateResultCard(request, id)).getMessage().contains("withheld"));
        assertTrue(assertThrows(IllegalStateException.class, () -> studentService.releasedScoreCardPdf(request, id)).getMessage().contains("withheld"));
        assertTrue(assertThrows(IllegalStateException.class, () -> studentService.candidateScoreCardDocument(request, id)).getMessage().contains("withheld"));
        assertTrue(assertThrows(IllegalStateException.class, () -> analysis.analyzeCandidate(id, new MockMultipartFile("file", "syllabus.pdf", "application/pdf", new byte[]{1}))).getMessage().contains("withheld"));
        assertEquals(1, controls.list(code, "Sample", true, 0).totalElements());
        var event = controls.history(id, 0).getFirst();
        assertEquals("master@example.test", event.getActor());
        assertEquals("Identity verification pending", event.getReason());
        assertThrows(IllegalStateException.class, () -> controls.decide(id, false, false, "Outdated request", "master"));
        controls.decide(id, false, true, "Identity review completed", "master@example.test");
        assertFalse(studentService.candidateDashboardForSession(id).resultWithheld());
        assertTrue(studentService.candidateDashboardForSession(id).resultReleased());
        assertEquals(2, controls.history(id, 0).size());
        assertFalse(controls.history(id, 0).getFirst().isWithheld());
        assertEquals(0, controls.list(code, id, true, 0).totalElements());
    }
    @Test
    void invalidDecisionsDoNotChangeCandidateState() {
        String id = candidate("TEST").getRegistrationId();
        assertThrows(IllegalArgumentException.class, () -> controls.decide(id, true, false, " ", "master"));
        assertFalse(students.findByRegistrationId(id).orElseThrow().isResultWithheld());
        assertTrue(controls.history(id, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controls.decide("MISSING", true, false, "Valid reason", "master"));
    }
    @Test
    void publicationChangesOnlyPostExamFieldsAndValidatesDependencies() {
        String code = "PUB" + System.nanoTime();
        Exam exam = new Exam(code, "Publication Test", ExamMode.PHYSICAL_CENTER, 180, Instant.now());
        ReflectionTestUtils.setField(exam, "registrationOpen", true);
        exams.saveAndFlush(exam);
        var request = new PostExamPublicationRequest(true, true, true, false, false, false, null, null);
        var saved = examService.updatePostExamPublication(code, request);
        assertTrue(saved.resultReleased()); assertTrue(saved.scoreCardPdfReleased()); assertTrue(saved.registrationOpen());
        assertEquals("Publication Test", saved.name());
        assertThrows(IllegalArgumentException.class, () -> examService.updatePostExamPublication(code, new PostExamPublicationRequest(true, false, true, false, false, false, null, null)));
        assertThrows(IllegalArgumentException.class, () -> examService.updatePostExamPublication(code, new PostExamPublicationRequest(true, true, true, true, false, false, null, null)));
        assertThrows(IllegalArgumentException.class, () -> examService.updatePostExamPublication(code, new PostExamPublicationRequest(true, true, true, false, true, false, null, null)));
        assertThrows(IllegalArgumentException.class, () -> examService.updatePostExamPublication(code, new PostExamPublicationRequest(true, true, true, false, false, true, Instant.now(), Instant.now().minusSeconds(60))));
    }
}
