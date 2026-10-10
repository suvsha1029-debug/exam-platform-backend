package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PortalNoticeTests {
    @Autowired ExamRepository repository;
    @Autowired ExamService service;
    @Autowired EntityManager entityManager;
    @Autowired Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void savesPerNoticeStatusAndDateAndOrdersNewBeforeOld() throws Exception {
        String code = "NOTICE" + System.nanoTime();
        repository.saveAndFlush(new Exam(code, "Notice Test", ExamMode.PHYSICAL_CENTER, 180, Instant.now()));
        var request = mapper.readValue("""
                {"streams":[],"enabledFields":[],"customFields":[],"portalNotices":[
                  {"text":"Archive","isNew":false,"publishedOn":"2026-10-07"},
                  {"text":"Previous new","isNew":true,"publishedOn":"2026-10-05"},
                  {"text":"Today","isNew":true,"publishedOn":"2026-10-07"},
                  {"text":"Same date","isNew":true,"publishedOn":"2026-10-07"}]}
                """, UpdateExamConfigRequest.class);
        var response = service.updateConfig(code, request);
        assertEquals(List.of("Today", "Same date", "Previous new", "Archive"), response.portalNotices().stream().map(PortalNotice::text).toList());
        assertEquals("Today\nSame date\nPrevious new\nArchive", response.portalNotifications());
        assertTrue(mapper.writeValueAsString(response.portalNotices()).contains("\"isNew\":true"));
        entityManager.flush(); entityManager.clear();
        var publicExam = service.publicList().stream().filter(exam -> exam.examCode().equals(code)).findFirst().orElseThrow();
        assertEquals(response.portalNotices(), publicExam.portalNotices());
        var omitted = mapper.readValue("{\"streams\":[],\"enabledFields\":[],\"customFields\":[]}", UpdateExamConfigRequest.class);
        assertEquals(response.portalNotices(), service.updateConfig(code, omitted).portalNotices());
        var cleared = mapper.readValue("{\"streams\":[],\"enabledFields\":[],\"customFields\":[],\"portalNotices\":[]}", UpdateExamConfigRequest.class);
        var empty = service.updateConfig(code, cleared);
        assertTrue(empty.portalNotices().isEmpty()); assertEquals("", empty.portalNotifications());
    }
    @Test void rejectsInvalidDatesBlankTextAndOversizedLists() {
        assertThrows(IllegalArgumentException.class, () -> new PortalNotice("Notice", true, "2026-02-30"));
        assertThrows(IllegalArgumentException.class, () -> new PortalNotice("Notice", true, "07/10/2026"));
        assertFalse(validator.validate(new PortalNotice(" ", true, "")).isEmpty());
        assertNull(PortalNotice.parse(null));
        Exam exam = new Exam("BOUNDS", "Bounds", ExamMode.PHYSICAL_CENTER, 180, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> exam.updatePortalNotices(java.util.Collections.nCopies(51, new PortalNotice("Notice", true, ""))));
        assertThrows(IllegalArgumentException.class, () -> exam.updatePortalNotices(java.util.Collections.nCopies(5, new PortalNotice("x".repeat(500), true, ""))));
    }
}
