package com.shashwat.examplatformbackend.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class SecureConsoleController {
    @GetMapping(value = SecureConsolePaths.ADMIN, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> admin() {
        return html("static/admin-7xk92q-portal.html");
    }

    @GetMapping(value = SecureConsolePaths.MASTER_ADMIN, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> masterAdmin() {
        return html("static/master-admin.html");
    }

    @GetMapping(value = SecureConsolePaths.PLATFORM, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> platform() {
        return html("static/platform-control.html");
    }

    @GetMapping(value = SecureConsolePaths.MASTER_ADMIN_SETUP, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> masterAdminSetup() {
        return html("static/master-admin-setup.html");
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login() {
        return html("static/ops-login.html");
    }

    @GetMapping({"/admin.html", "/admin-7xk92q-portal.html", "/master-admin.html", "/platform-control.html"})
    public ResponseEntity<String> retiredConsolePath() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_PLAIN)
                .body("Not found");
    }

    private static ResponseEntity<String> html(String classpathLocation) {
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(classpathLocation).getInputStream(),
                    StandardCharsets.UTF_8
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.TEXT_HTML)
                    .body(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Console page is not available.");
        }
    }
}
