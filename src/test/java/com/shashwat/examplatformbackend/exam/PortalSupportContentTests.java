package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PortalSupportContentTests {
    @Autowired private ExamRepository repository;
    @Autowired private ExamService service;
    @Autowired private EntityManager entityManager;
    @Autowired private Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void savedHelpAndFaqReachAdminAndPublicResponsesAndSurviveLegacyUpdates() throws Exception {
        String code = "SUP" + System.nanoTime();
        repository.saveAndFlush(new Exam(code, "Support Test", ExamMode.PHYSICAL_CENTER, 180, Instant.now()));
        String json = """
                {"streams":[],"enabledFields":[],"customFields":[],
                 "portalSupportContent":{"faqs":[{"question":" How do I log in? ","answer":" Use your application number. "}],
                 "helpEmail":"help@example.test","helpPhone":"+91 44 1234 5678",
                 "helpHours":"09:00-17:00 IST","helpUrl":"https://example.test/help"}}
                """;
        var request = mapper.readValue(json, UpdateExamConfigRequest.class);
        assertTrue(validator.validate(request).isEmpty());
        var expected = request.portalSupportContent();
        assertEquals(expected, service.updateConfig(code, request).portalSupportContent());
        entityManager.flush();
        entityManager.clear();
        assertEquals(expected, PortalSupportContent.fromJson(repository.findByExamCode(code).orElseThrow().getPortalSupportContentJson()));
        assertEquals(expected, service.publicList().stream().filter(exam -> code.equals(exam.examCode())).findFirst().orElseThrow().portalSupportContent());

        service.updateConfig(code, mapper.readValue("{\"streams\":[],\"enabledFields\":[],\"customFields\":[]}", UpdateExamConfigRequest.class));
        assertEquals(expected, ExamResponse.from(repository.findByExamCode(code).orElseThrow()).portalSupportContent());
        service.updateConfig(code, mapper.readValue("{\"streams\":[],\"enabledFields\":[],\"customFields\":[],\"portalSupportContent\":{}}", UpdateExamConfigRequest.class));
        assertEquals(PortalSupportContent.empty(), ExamPublicResponse.from(repository.findByExamCode(code).orElseThrow()).portalSupportContent());
    }

    @Test
    void validationBoundsFaqsAndRejectsInvalidContacts() {
        var faq = new PortalSupportContent.Faq("Question", "Answer");
        assertFalse(validator.validate(new PortalSupportContent(Collections.nCopies(26, faq), null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new PortalSupportContent(List.of(new PortalSupportContent.Faq("", "Answer")), null, null, null, null)).isEmpty());
        for (String url : List.of("javascript:alert(1)", "file:///local", "ftp://example.test", "https://invalid url")) {
            assertFalse(validator.validate(new PortalSupportContent(null, null, null, null, url)).isEmpty());
        }
        assertFalse(validator.validate(new PortalSupportContent(null, "bad-email", null, null, null)).isEmpty());
        assertFalse(validator.validate(new PortalSupportContent(null, null, "javascript:alert(1)", null, null)).isEmpty());
        assertTrue(validator.validate(new PortalSupportContent(List.of(faq), "", "+91 (44) 1234-5678", "", "https://example.test/help")).isEmpty());
        assertEquals(PortalSupportContent.empty(), PortalSupportContent.fromJson(null));
        assertEquals(PortalSupportContent.empty(), PortalSupportContent.fromJson("{broken"));
    }
}
