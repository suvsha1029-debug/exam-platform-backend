package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.exam.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CandidateCorrectionTests {
    @Autowired StudentService service;
    @Autowired StudentRepository students;
    @Autowired ExamRepository exams;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired com.shashwat.examplatformbackend.config.CandidateCsrfFilter csrf;
    @Autowired com.shashwat.examplatformbackend.config.CandidateSecurityTokenService tokens;
    @Value("${app.upload.dir}") String uploadDir;

    private Student fixture() {
        String code = "COR" + System.nanoTime();
        Exam exam = new Exam(code, "Correction test", ExamMode.PHYSICAL_CENTER, 180, Instant.now());
        ReflectionTestUtils.setField(exam,"registrationOpen",false);
        ReflectionTestUtils.setField(exam,"correctionsOpen",true);
        ReflectionTestUtils.setField(exam,"candidateCorrectionFieldsJson","{\"FULL_NAME\":\"true\",\"ADDRESS\":\"true\",\"PHOTO\":\"true\",\"ID_CARD\":\"true\"}");
        ReflectionTestUtils.setField(exam,"customFieldsJson","[{\"key\":\"note\",\"label\":\"Additional details\",\"type\":\"TEXT\",\"correctionAllowed\":true},{\"key\":\"proof\",\"label\":\"Proof\",\"type\":\"PDF_UPLOAD\",\"correctionAllowed\":true}]");
        exams.saveAndFlush(exam);
        Student student = new Student();
        ReflectionTestUtils.setField(student,"registrationId",code + "01");
        ReflectionTestUtils.setField(student,"examCode",code);
        ReflectionTestUtils.setField(student,"fullName","Sample Candidate");
        ReflectionTestUtils.setField(student,"dateOfBirth",LocalDate.of(2000,1,1));
        ReflectionTestUtils.setField(student,"createdAt",Instant.now());
        ReflectionTestUtils.setField(student,"passcodeHash","test-hash");
        return students.saveAndFlush(student);
    }
    private CandidateCorrectionRequest details(Student student, String name, String address) {
        return new CandidateCorrectionRequest(student.getRegistrationId(),null,name,null,null,null,null,address,null,null,null,null,null,null,null,null,null,null,null);
    }
    private MockMultipartFile image(String key) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(12,12,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
            return new MockMultipartFile(key,"sample.png","image/png",bytes.toByteArray());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private MockMultipartFile pdf(String key) {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var bytes = new java.io.ByteArrayOutputStream(); document.save(bytes);
            return new MockMultipartFile(key,"proof.pdf","application/pdf",bytes.toByteArray());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    @Test void fillsEmptyFieldsReplacesFilesAndAllowsRepeatedSubmissionAfterRegistrationCloses() {
        Student student = fixture(); String id = student.getRegistrationId();
        var form = new MockMultipartHttpServletRequest(); form.addFile(image("photo")); form.addFile(pdf("idCard")); form.addFile(pdf("customField_proof")); form.addParameter("customField_note","New information");
        var response = service.submitCandidateCorrection(details(student,null,"New address"),form,id);
        assertEquals("New address",response.address()); assertEquals("Sample Candidate",response.fullName()); assertTrue(response.photoUploaded());
        assertEquals("New information",response.customFieldValues().get("note"));
        String first = response.photoPath(); String document = response.idCardPath();
        assertTrue(service.candidateFile(new CandidateFileRequest(id,null,document),id).dataUrl().startsWith("data:application/pdf"));
        assertTrue(service.candidateFile(new CandidateFileRequest(id,null,response.customFilePaths().get("proof")),id).dataUrl().startsWith("data:application/pdf"));
        var replacement = new MockMultipartHttpServletRequest(); replacement.addFile(image("photo"));
        var second = service.submitCandidateCorrection(details(student,"Revised Candidate",null),replacement,id);
        assertNotEquals(first,second.photoPath()); assertEquals(document,second.idCardPath()); assertEquals("New address",second.address());
        assertThrows(IllegalArgumentException.class,() -> service.candidateFile(new CandidateFileRequest(id,null,first),id));
    }
    @Test void rejectsClosedWindowAndWrongCandidate() {
        Student student = fixture(); var form = new MockMultipartHttpServletRequest();
        assertThrows(RuntimeException.class,() -> service.submitCandidateCorrection(details(student,null,null),form,"OTHER"));
        ReflectionTestUtils.setField(exams.findByExamCode(student.getExamCode()).orElseThrow(),"correctionsOpen",false);
        assertThrows(IllegalStateException.class,() -> service.submitCandidateCorrection(details(student,null,null),form,student.getRegistrationId()));
    }
    @Test void rejectsUnpermittedFieldsAndInvalidFiles() {
        Student student = fixture();
        var denied = new MockMultipartHttpServletRequest(); denied.addFile(image("signature"));
        assertThrows(IllegalArgumentException.class,() -> service.submitCandidateCorrection(details(student,null,null),denied,student.getRegistrationId()));
        var invalid = new MockMultipartHttpServletRequest(); invalid.addFile(new MockMultipartFile("photo","bad.png","image/png",new byte[]{1,2,3}));
        assertThrows(IllegalArgumentException.class,() -> service.submitCandidateCorrection(details(student,null,null),invalid,student.getRegistrationId()));
        var deniedText = new CandidateCorrectionRequest(student.getRegistrationId(),null,null,null,"other@example.test",null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        assertThrows(IllegalArgumentException.class,() -> service.updateCandidateCorrection(deniedText,student.getRegistrationId()));
    }
    @Test void requiresEnabledMandatoryUploadsAndPreservesOmittedValues() {
        Student student = fixture();
        var exam = exams.findByExamCode(student.getExamCode()).orElseThrow();
        ReflectionTestUtils.setField(exam,"candidateRequiredFieldsJson","{\"PHOTO\":\"true\"}");
        assertThrows(IllegalArgumentException.class,() -> service.submitCandidateCorrection(details(student,null,null),new MockMultipartHttpServletRequest(),student.getRegistrationId()));
        assertThrows(IllegalArgumentException.class,() -> service.updateCandidateCorrection(details(student," ",null),student.getRegistrationId()));
    }
    @Test void rollbackRemovesOnlyNewUploads() throws Exception {
        Student student = fixture(); String id = student.getRegistrationId();
        Path dir = Path.of(uploadDir).toAbsolutePath().resolve(id); Files.createDirectories(dir);
        Path original = dir.resolve("original.txt"); Files.writeString(original,"Existing upload");
        var form = new MockMultipartHttpServletRequest(); form.addFile(image("photo"));
        service.submitCandidateCorrection(details(student,null,null),form,id);
        Path uploaded = Path.of(uploadDir).resolve(student.getPhotoPath()); assertTrue(Files.exists(uploaded));
        TestTransaction.flagForRollback(); TestTransaction.end();
        assertFalse(Files.exists(uploaded)); assertTrue(Files.exists(original));
        Files.delete(original); Files.delete(dir);
    }
    @Test void multipartEndpointRequiresCsrfAndBindsJsonAndFiles() throws Exception {
        Student student = fixture();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .addFilters(csrf).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("candidateRegistrationId", student.getRegistrationId());
        var details = new MockMultipartFile("details","blob","application/json",
                ("{\"registrationId\":\"" + student.getRegistrationId() + "\",\"address\":\"Corrected address\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/candidate/correction/submit")
                .file(details).file(image("photo")).session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/candidate/correction/submit")
                .file(details).file(image("photo")).session(session).header("X-Candidate-CSRF",tokens.token(session)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.address").value("Corrected address"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.photoUploaded").value(true));
    }
}
