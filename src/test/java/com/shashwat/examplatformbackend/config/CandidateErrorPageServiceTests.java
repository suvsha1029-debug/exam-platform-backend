package com.shashwat.examplatformbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CandidateErrorPageServiceTests {
    private final CandidateErrorPageService service = new CandidateErrorPageService();

    @Test
    void preservesErrorStatusesAndSafeNavigation() {
        Map.of("invalid-link", 403, "auth-required", 401, "auth-mismatch", 401,
                "exam-not-ready", 409, "session-closed", 410, "unknown-page", 404)
                .forEach((reason, status) -> {
                    var response = service.response(reason);
                    assertEquals(status, response.getStatusCode().value());
                    assertEquals("no-store", response.getHeaders().getCacheControl());
                    assertTrue(response.getBody().contains("href=\"/candidate-entry/dashboard\""));
                    assertTrue(response.getBody().contains("href=\"/\""));
                    assertFalse(response.getBody().contains("{{"));
                    assertFalse(response.getBody().contains("animation:"));
                });
        assertEquals(HttpStatus.BAD_REQUEST, service.response("invalid-link", HttpStatus.BAD_REQUEST).getStatusCode());
    }

    @Test
    void unknownReasonsCannotInjectMarkup() {
        assertEquals(service.html("invalid-link"), service.html("<script>alert(1)</script>"));
        assertEquals(service.html("invalid-link"), service.html(null));
    }

    @Test
    void writesRenderedPreview() throws Exception {
        Path preview = Path.of("output", "error-page", "preview.html");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, service.html("invalid-link"));
    }
}
