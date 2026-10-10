package com.shashwat.examplatformbackend.config;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CandidateErrorPageService {
    private static final Map<String, ErrorCopy> COPY = Map.of(
            "invalid-link", new ErrorCopy(HttpStatus.FORBIDDEN, "403", "Invalid Or Expired Link", "This candidate link could not be verified.", "Open the candidate login again and use a fresh secure link."),
            "auth-required", new ErrorCopy(HttpStatus.UNAUTHORIZED, "401", "Candidate Login Required", "This page needs a verified candidate session.", "Please log in again before opening candidate-only pages."),
            "auth-mismatch", new ErrorCopy(HttpStatus.UNAUTHORIZED, "401", "Candidate Details Do Not Match", "The registration ID and date of birth did not match our records.", "Check the details and try again from the candidate login page."),
            "exam-not-ready", new ErrorCopy(HttpStatus.CONFLICT, "409", "Exam Is Not Ready Yet", "The exam lobby, paper, or launch switch is not enabled.", "Please wait for the administrator to open the exam."),
            "session-closed", new ErrorCopy(HttpStatus.GONE, "410", "Exam Session Closed", "This secure exam window cannot be refreshed, reused, or opened again.", "If you believe this is unexpected, contact the exam administrator."),
            "unknown-page", new ErrorCopy(HttpStatus.NOT_FOUND, "404", "Candidate Page Not Found", "The candidate page you tried to open does not exist.", "Use one of the safe links below to continue.")
    );

    public ResponseEntity<String> response(String reason) {
        ErrorCopy copy = copy(reason);
        return response(reason, copy.status());
    }

    public ResponseEntity<String> response(String reason, HttpStatus status) {
        ErrorCopy copy = copy(reason);
        return ResponseEntity.status(status == null ? copy.status() : status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.TEXT_HTML)
                .body(html(reason));
    }

    public String html(String reason) {
        ErrorCopy copy = copy(reason);
        return TEMPLATE
                .replace("{{code}}", escape(copy.code()))
                .replace("{{title}}", escape(copy.title()))
                .replace("{{message}}", escape(copy.message()))
                .replace("{{detail}}", escape(copy.detail()))
                .replace("{{reason}}", escape(normalize(reason)));
    }

    private static ErrorCopy copy(String reason) {
        return COPY.getOrDefault(normalize(reason), COPY.get("invalid-link"));
    }

    private static String normalize(String reason) {
        String value = reason == null ? "" : reason.trim().toLowerCase();
        return COPY.containsKey(value) ? value : "invalid-link";
    }

    private static String escape(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record ErrorCopy(HttpStatus status, String code, String title, String message, String detail) {
    }

    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="robots" content="noindex">
              <title>{{title}}</title>
              <style>
                * { box-sizing: border-box; }
                body {
                  margin: 0; min-height: 100vh; min-height: 100svh;
                  display: grid; place-items: center; padding: 28px;
                  background: #fff; color: #242424;
                  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Helvetica, Arial, sans-serif;
                  font-size: 14px; line-height: 1.6; letter-spacing: 0;
                }
                main { width: 100%; max-width: 420px; border-top: 2px solid #a52b33; padding-top: 20px; }
                .status { margin: 0 0 10px; color: #a52b33; font-size: 12px; font-weight: 600; }
                h1 { margin: 0 0 10px; font-size: 24px; line-height: 1.25; font-weight: 600; overflow-wrap: anywhere; }
                .message, .detail { margin: 0; color: #626262; overflow-wrap: anywhere; }
                .detail { margin-top: 6px; }
                nav { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; margin-top: 24px; }
                a { color: #454545; text-underline-offset: 4px; }
                .primary { display: inline-flex; align-items: center; min-height: 44px; padding: 9px 16px; background: #242424; color: #fff; text-decoration: none; font-weight: 600; }
                .primary:hover { background: #444; }
                a:focus-visible { outline: 2px solid #a52b33; outline-offset: 4px; }
              </style>
            </head>
            <body>
              <main data-reason="{{reason}}" aria-labelledby="error-title">
                <p class="status">Error {{code}}</p>
                <h1 id="error-title">{{title}}</h1>
                <p class="message">{{message}}</p>
                <p class="detail">{{detail}}</p>
                <nav aria-label="Continue to the portal">
                  <a class="primary" href="/candidate-entry/dashboard">Go to login</a>
                  <a href="/">Return home</a>
                </nav>
              </main>
            </body>
            </html>
            """;
}
