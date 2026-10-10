package com.shashwat.examplatformbackend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PdfExtractorService {
    private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path jobRoot;
    private final Path pythonScript;
    private final String pythonCommand;

    public PdfExtractorService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.extractor.python-script:tools/pdf_structured_extract.py}") String pythonScript,
            @Value("${app.extractor.python-command:python}") String pythonCommand
    ) {
        this.jobRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("pdf-extractor-jobs").normalize();
        this.pythonScript = resolveScript(pythonScript);
        this.pythonCommand = pythonCommand;
    }

    public PdfExtractorResponse extract(MultipartFile file, String format, String profile, String subject, int dpi) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }
        if (!Files.exists(pythonScript)) {
            throw new IllegalStateException("Extractor script is missing: " + pythonScript);
        }
        String safeFormat = ("pre-git".equalsIgnoreCase(format)) ? "pre-git" : "viewer";
        String safeProfile = (profile == null || profile.isBlank()) ? "auto" : profile.trim();
        String safeSubject = (subject == null || subject.isBlank()) ? "GEN" : subject.trim();
        int safeDpi = Math.max(120, Math.min(dpi, 300));

        try {
            Files.createDirectories(jobRoot);
            String jobId = UUID.randomUUID().toString().replace("-", "");
            Path jobDir = jobRoot.resolve(jobId).normalize();
            Files.createDirectories(jobDir);

            Path inputPdf = jobDir.resolve("input.pdf").normalize();
            Path outputJson = jobDir.resolve("output.json").normalize();
            if (!inputPdf.startsWith(jobRoot) || !outputJson.startsWith(jobRoot)) {
                throw new IllegalStateException("Invalid extractor job path.");
            }
            file.transferTo(inputPdf);
            runExtractor(inputPdf, outputJson, safeFormat, safeProfile, safeSubject, safeDpi);

            Object payload = objectMapper.readValue(outputJson.toFile(), Object.class);
            int questionCount = countQuestions(payload);
            return new PdfExtractorResponse(safeProfile, safeFormat, questionCount, payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save uploaded PDF for extraction.");
        }
    }

    private void runExtractor(Path inputPdf, Path outputJson, String format, String profile, String subject, int dpi) {
        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(pythonScript.toString());
        command.add("--pdf");
        command.add(inputPdf.toString());
        command.add("--out");
        command.add(outputJson.toString());
        command.add("--format");
        command.add(format);
        command.add("--profile");
        command.add(profile);
        command.add("--dpi");
        command.add(String.valueOf(dpi));
        command.add("--subject");
        command.add(subject);

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(EXTRACT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("PDF extraction timed out.");
            }
            if (process.exitValue() != 0 || !Files.exists(outputJson)) {
                throw new IllegalStateException("PDF extraction failed: " + output);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start PDF extractor.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PDF extraction was interrupted.");
        }
    }

    private static Path resolveScript(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().resolve(path).normalize();
    }

    private static int countQuestions(Object payload) {
        if (payload instanceof List<?> list) {
            return list.size();
        }
        if (payload instanceof Map<?, ?> map) {
            Object questions = map.get("qs");
            if (questions instanceof List<?> list) {
                return list.size();
            }
        }
        return 0;
    }
}
