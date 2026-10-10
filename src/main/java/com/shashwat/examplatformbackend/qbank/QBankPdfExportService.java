package com.shashwat.examplatformbackend.qbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.shashwat.examplatformbackend.exam.Exam;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QBankPdfExportService {
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 38f;
    private static final float HEADER_HEIGHT = 78f;
    private static final float FOOTER_HEIGHT = 26f;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - (MARGIN * 2f);

    private final Path uploadRoot;

    public QBankPdfExportService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public byte[] questionPaperPdf(Exam exam, JsonNode paper) {
        try {
            return renderPaper(exam, paper, false);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build selected question paper PDF.");
        }
    }

    public byte[] answerKeyPdf(Exam exam, JsonNode key) {
        try {
            return renderPaper(exam, key, true);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build selected answer key PDF.");
        }
    }

    private byte[] renderPaper(Exam exam, JsonNode root, boolean answerKey) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfCursor cursor = new PdfCursor(document, exam, root, answerKey);
            cursor.newPage();
            if (answerKey) {
                renderAnswerRows(cursor, root);
            } else {
                renderQuestionRows(cursor, root);
            }
            cursor.close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private void renderQuestionRows(PdfCursor cursor, JsonNode root) throws IOException {
        JsonNode questions = root.path("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            cursor.drawWrapped("No questions found in this selected paper.", MARGIN, cursor.cursorTop, CONTENT_WIDTH, 12f, REGULAR, Color.DARK_GRAY);
            return;
        }
        String currentSection = "";
        for (int index = 0; index < questions.size(); index++) {
            JsonNode question = questions.get(index);
            String section = firstText(question, "section", "section_name", "subject_name", "subject");
            if (!section.equalsIgnoreCase(currentSection)) {
                currentSection = section;
                cursor.ensure(54f);
                drawSectionHeader(cursor, currentSection.isBlank() ? "Section" : currentSection, sectionMarkSummary(questions, currentSection));
            }
            float needed = estimateQuestionHeight(cursor.document, question);
            cursor.ensure(needed);
            drawQuestionBlock(cursor, question, index + 1, needed);
            cursor.cursorTop += needed + 10f;
        }
    }

    private void renderAnswerRows(PdfCursor cursor, JsonNode root) throws IOException {
        JsonNode answers = root.path("answers");
        cursor.ensure(46f);
        cursor.stream.setNonStrokingColor(new Color(20, 20, 20));
        cursor.stream.addRect(MARGIN, y(cursor.cursorTop, 24f), CONTENT_WIDTH, 24f);
        cursor.stream.fill();
        drawText(cursor.stream, BOLD, 12f, Color.WHITE, MARGIN + 10f, baseline(cursor.cursorTop, 16f), "FINAL SELECTED ANSWER KEY");
        cursor.cursorTop += 34f;
        if (!answers.isArray() || answers.isEmpty()) {
            cursor.drawWrapped("No answers found in this selected answer key.", MARGIN, cursor.cursorTop, CONTENT_WIDTH, 12f, REGULAR, Color.DARK_GRAY);
            return;
        }
        drawKeyHeader(cursor);
        for (int index = 0; index < answers.size(); index++) {
            JsonNode answer = answers.get(index);
            cursor.ensure(28f);
            float rowTop = cursor.cursorTop;
            Color fill = index % 2 == 0 ? new Color(249, 251, 253) : Color.WHITE;
            cursor.stream.setNonStrokingColor(fill);
            cursor.stream.addRect(MARGIN, y(rowTop, 24f), CONTENT_WIDTH, 24f);
            cursor.stream.fill();
            cursor.stream.setStrokingColor(new Color(210, 218, 228));
            cursor.stream.addRect(MARGIN, y(rowTop, 24f), CONTENT_WIDTH, 24f);
            cursor.stream.stroke();
            String qNo = firstText(answer, "question_number", "questionNumber");
            if (qNo.isBlank()) qNo = "Q." + (index + 1);
            drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, MARGIN + 8f, baseline(rowTop, 15f), qNo);
            drawText(cursor.stream, REGULAR, 10.5f, Color.BLACK, MARGIN + 84f, baseline(rowTop, 15f), answerText(answer));
            drawText(cursor.stream, REGULAR, 10.5f, Color.BLACK, MARGIN + 300f, baseline(rowTop, 15f), firstText(answer, "marks", "positive_marks", "positiveMarks"));
            drawText(cursor.stream, REGULAR, 9.5f, Color.DARK_GRAY, MARGIN + 360f, baseline(rowTop, 15f), firstText(answer, "section", "topic"));
            cursor.cursorTop += 24f;
        }
    }

    private void drawKeyHeader(PdfCursor cursor) throws IOException {
        cursor.stream.setNonStrokingColor(new Color(230, 236, 245));
        cursor.stream.addRect(MARGIN, y(cursor.cursorTop, 24f), CONTENT_WIDTH, 24f);
        cursor.stream.fill();
        cursor.stream.setStrokingColor(new Color(170, 188, 210));
        cursor.stream.addRect(MARGIN, y(cursor.cursorTop, 24f), CONTENT_WIDTH, 24f);
        cursor.stream.stroke();
        drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, MARGIN + 8f, baseline(cursor.cursorTop, 15f), "Question");
        drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, MARGIN + 84f, baseline(cursor.cursorTop, 15f), "Correct Answer");
        drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, MARGIN + 300f, baseline(cursor.cursorTop, 15f), "Marks");
        drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, MARGIN + 360f, baseline(cursor.cursorTop, 15f), "Section / Topic");
        cursor.cursorTop += 24f;
    }

    private void drawSectionHeader(PdfCursor cursor, String section, String summary) throws IOException {
        cursor.stream.setNonStrokingColor(new Color(12, 20, 32));
        cursor.stream.addRect(MARGIN, y(cursor.cursorTop, 24f), CONTENT_WIDTH, 24f);
        cursor.stream.fill();
        drawText(cursor.stream, BOLD, 12f, Color.WHITE, MARGIN + 10f, baseline(cursor.cursorTop, 16f), section);
        cursor.cursorTop += 28f;
        if (!summary.isBlank()) {
            drawText(cursor.stream, ITALIC, 9.5f, new Color(45, 55, 72), MARGIN + 8f, baseline(cursor.cursorTop, 10f), summary);
            cursor.cursorTop += 18f;
        }
    }

    private void drawQuestionBlock(PdfCursor cursor, JsonNode question, int fallbackNumber, float height) throws IOException {
        float top = cursor.cursorTop;
        cursor.stream.setStrokingColor(Color.BLACK);
        cursor.stream.setLineWidth(0.7f);
        cursor.stream.addRect(MARGIN, y(top, height), CONTENT_WIDTH, height);
        cursor.stream.stroke();
        cursor.stream.moveTo(MARGIN + 54f, y(top, height));
        cursor.stream.lineTo(MARGIN + 54f, y(top, 0));
        cursor.stream.stroke();
        String qNo = firstText(question, "question_number", "questionNumber");
        if (qNo.isBlank()) qNo = "Q." + fallbackNumber;
        drawText(cursor.stream, BOLD, 11.5f, Color.BLACK, MARGIN + 10f, baseline(top, 18f), qNo);
        String meta = "Type: " + textOr(firstText(question, "question_type", "questionType", "type"), "MCQ")
                + "     Marks: +" + textOr(firstText(question, "marks", "positive_marks", "positiveMarks"), "0") + "M"
                + "     Negative: " + textOr(firstText(question, "negative_marks", "negativeMarks", "neg_marks"), "0") + "M";
        drawText(cursor.stream, ITALIC, 9.5f, new Color(40, 80, 55), MARGIN + 66f, baseline(top, 18f), meta);
        float innerX = MARGIN + 66f;
        float innerTop = top + 30f;
        float innerWidth = CONTENT_WIDTH - 78f;
        float imageBottom = drawQuestionContent(cursor, question, innerX, innerTop, innerWidth);
        float optionTop = Math.max(innerTop + 26f, imageBottom + 8f);
        drawQuestionOptions(cursor, question, innerX, optionTop, innerWidth);
    }

    private float drawQuestionContent(PdfCursor cursor, JsonNode question, float x, float top, float width) throws IOException {
        String imageSource = firstText(question,
                "questionPdfImage", "question_pdf_image", "pdfImage", "pdf_image", "image", "question_image", "questionImage",
                "questionBaseImage", "question_base_image", "baseImage", "viewerImage", "questionViewerImage", "question_viewer_image");
        PDImageXObject image = imageSource.isBlank() ? null : imageFromSource(cursor.document, imageSource);
        if (image != null) {
            float maxHeight = 250f;
            Size size = fit(image.getWidth(), image.getHeight(), width, maxHeight);
            cursor.stream.drawImage(image, x, y(top, size.height), size.width, size.height);
            return top + size.height;
        }
        String body = firstText(question, "question_text", "questionText", "body", "content", "text");
        if (body.isBlank()) body = "(question image unavailable)";
        return cursor.drawWrapped(body, x, top, width, 11f, REGULAR, Color.BLACK);
    }

    private void drawQuestionOptions(PdfCursor cursor, JsonNode question, float x, float top, float width) throws IOException {
        Map<String, JsonNode> options = optionMap(question.path("options"));
        if (options.isEmpty()) {
            String type = firstText(question, "question_type", "questionType", "type").toUpperCase(Locale.ROOT);
            if ("NAT".equals(type)) {
                drawText(cursor.stream, REGULAR, 10.5f, Color.BLACK, x, baseline(top, 12f), "Answer: ____________________");
            }
            return;
        }
        float rowTop = top;
        for (Map.Entry<String, JsonNode> entry : options.entrySet()) {
            JsonNode value = entry.getValue();
            String imageSource = optionImage(value);
            PDImageXObject image = imageSource.isBlank() ? null : imageFromSource(cursor.document, imageSource);
            float rowHeight = 24f;
            if (image != null) {
                Size size = fit(image.getWidth(), image.getHeight(), width - 34f, 82f);
                rowHeight = Math.max(24f, size.height + 8f);
                drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, x, baseline(rowTop, 15f), "(" + entry.getKey() + ")");
                cursor.stream.drawImage(image, x + 34f, y(rowTop + 4f, size.height), size.width, size.height);
            } else {
                String text = optionText(value);
                drawText(cursor.stream, BOLD, 10.5f, Color.BLACK, x, baseline(rowTop, 15f), "(" + entry.getKey() + ")");
                cursor.drawWrapped(text.isBlank() ? "(empty)" : text, x + 34f, rowTop + 4f, width - 34f, 10.5f, REGULAR, Color.BLACK);
            }
            rowTop += rowHeight;
        }
    }

    private float estimateQuestionHeight(PDDocument document, JsonNode question) {
        float height = 52f;
        try {
            String imageSource = firstText(question,
                    "questionPdfImage", "question_pdf_image", "pdfImage", "pdf_image", "image", "question_image", "questionImage",
                    "questionBaseImage", "question_base_image", "baseImage", "viewerImage", "questionViewerImage", "question_viewer_image");
            PDImageXObject image = imageSource.isBlank() ? null : imageFromSource(document, imageSource);
            height += image == null ? 42f : fit(image.getWidth(), image.getHeight(), CONTENT_WIDTH - 78f, 250f).height;
            Map<String, JsonNode> options = optionMap(question.path("options"));
            if (options.isEmpty()) return Math.max(height + 22f, 92f);
            for (JsonNode option : options.values()) {
                String source = optionImage(option);
                PDImageXObject optionImage = source.isBlank() ? null : imageFromSource(document, source);
                height += optionImage == null ? 28f : Math.max(24f, fit(optionImage.getWidth(), optionImage.getHeight(), CONTENT_WIDTH - 112f, 82f).height + 8f);
            }
        } catch (Exception ignored) {
            height += 120f;
        }
        return Math.min(Math.max(height + 20f, 112f), 430f);
    }

    private String sectionMarkSummary(JsonNode questions, String section) {
        int count = 0;
        double marks = 0;
        for (JsonNode question : questions) {
            if (!section.equalsIgnoreCase(firstText(question, "section", "section_name", "subject_name", "subject"))) continue;
            count++;
            marks += number(firstText(question, "marks", "positive_marks", "positiveMarks"));
        }
        if (count == 0) return "";
        return count + " question(s), " + trimNumber(marks) + " marks in this section";
    }

    private Map<String, JsonNode> optionMap(JsonNode options) {
        java.util.LinkedHashMap<String, JsonNode> out = new java.util.LinkedHashMap<>();
        if (options == null || options.isMissingNode() || options.isNull()) return out;
        if (options.isArray()) {
            for (int index = 0; index < options.size(); index++) {
                out.put(String.valueOf((char) ('A' + index)), options.get(index));
            }
            return out;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = options.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (key.matches("[A-Z]")) out.put(key, entry.getValue());
        }
        return out;
    }

    private String optionImage(JsonNode option) {
        if (option == null || option.isNull()) return "";
        if (option.isTextual()) {
            String value = option.asText("");
            return isImageSource(value) ? value : "";
        }
        return firstText(option, "optionPdfImage", "option_pdf_image", "pdfImage", "pdf_image", "image", "option_image",
                "optionImage", "optionBaseImage", "option_base_image", "baseImage", "viewerImage", "optionViewerImage", "option_viewer_image");
    }

    private String optionText(JsonNode option) {
        if (option == null || option.isNull()) return "";
        if (option.isTextual()) return option.asText("");
        return firstText(option, "text", "label", "value", "body", "content");
    }

    private String answerText(JsonNode answer) {
        String value = firstText(answer,
                "correct_answer", "correctAnswer", "answer", "selected_answer", "selectedAnswer",
                "correct_option", "correctOption", "correct_options", "correctOptions",
                "option", "options", "key");
        if (!value.isBlank()) return value;
        JsonNode array = answer.path("correct_options");
        if (array.isArray()) {
            List<String> values = new ArrayList<>();
            array.forEach(item -> values.add(item.asText("")));
            return String.join(", ", values);
        }
        return "-";
    }

    private PDImageXObject imageFromSource(PDDocument document, String source) {
        try {
            byte[] bytes = imageBytes(source);
            if (bytes == null || bytes.length == 0) return null;
            return PDImageXObject.createFromByteArray(document, bytes, "qbank-image");
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] imageBytes(String source) throws IOException {
        if (source == null || source.isBlank()) return null;
        String trimmed = source.trim();
        if (trimmed.startsWith("data:image/")) {
            int comma = trimmed.indexOf(',');
            if (comma < 0) return null;
            String meta = trimmed.substring(0, comma).toLowerCase(Locale.ROOT);
            String payload = trimmed.substring(comma + 1);
            if (meta.contains(";base64")) {
                return Base64.getDecoder().decode(payload);
            }
            return URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.ISO_8859_1);
        }
        String marker = "/api/public/exams/portal-banner/";
        int markerIndex = trimmed.indexOf(marker);
        if (markerIndex >= 0) {
            String tail = trimmed.substring(markerIndex + marker.length());
            String[] parts = tail.split("/", 2);
            if (parts.length == 2) {
                Path file = uploadRoot.resolve("portal-branding").resolve(parts[0]).resolve(parts[1]).normalize();
                if (file.startsWith(uploadRoot) && Files.exists(file)) return Files.readAllBytes(file);
            }
        }
        return null;
    }

    private boolean isImageSource(String value) {
        return value != null && value.trim().startsWith("data:image/");
    }

    private Size fit(float sourceWidth, float sourceHeight, float maxWidth, float maxHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return new Size(maxWidth, Math.min(maxHeight, 80f));
        float scale = Math.min(maxWidth / sourceWidth, maxHeight / sourceHeight);
        scale = Math.min(scale, 1f);
        return new Size(sourceWidth * scale, sourceHeight * scale);
    }

    private static float y(float top, float height) {
        return PAGE.getHeight() - top - height;
    }

    private static float baseline(float top, float offset) {
        return PAGE.getHeight() - top - offset;
    }

    private static void drawText(PDPageContentStream stream, PDType1Font font, float size, Color color, float x, float baseline, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(safePdfText(text));
        stream.endText();
    }

    private static String safePdfText(String text) {
        String value = text == null ? "" : text;
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\n' || ch == '\r' || ch == '\t') out.append(' ');
            else if (ch >= 32 && ch <= 255) out.append(ch);
            else out.append('?');
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull() && !value.asText("").trim().isBlank()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value == null || value.isBlank() ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) return String.valueOf((long) Math.rint(value));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Size(float width, float height) {
    }

    private final class PdfCursor {
        private final PDDocument document;
        private final Exam exam;
        private final JsonNode root;
        private final boolean answerKey;
        private PDPageContentStream stream;
        private int pageNumber;
        private float cursorTop;

        private PdfCursor(PDDocument document, Exam exam, JsonNode root, boolean answerKey) {
            this.document = document;
            this.exam = exam;
            this.root = root;
            this.answerKey = answerKey;
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            pageNumber++;
            stream = new PDPageContentStream(document, page);
            drawWatermark();
            drawHeader();
            cursorTop = MARGIN + HEADER_HEIGHT + 12f;
        }

        private void close() throws IOException {
            if (stream == null) return;
            drawFooter();
            stream.close();
            stream = null;
        }

        private void ensure(float neededHeight) throws IOException {
            if (cursorTop + neededHeight > PAGE.getHeight() - MARGIN - FOOTER_HEIGHT) {
                newPage();
            }
        }

        private float drawWrapped(String text, float x, float top, float width, float size, PDType1Font font, Color color) throws IOException {
            List<String> lines = wrap(text, font, size, width);
            float lineTop = top;
            for (String line : lines) {
                drawText(stream, font, size, color, x, baseline(lineTop, size), line);
                lineTop += size + 4f;
            }
            return lineTop;
        }

        private List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
            String safe = safePdfText(text);
            if (safe.isBlank()) return List.of("");
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : safe.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                float candidateWidth = font.getStringWidth(candidate) / 1000f * size;
                if (candidateWidth > width && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        }

        private void drawHeader() throws IOException {
            String title = textOr(exam.getExamUiHeaderTitle(), textOr(firstText(root, "exam_name", "exam", "project_name"), exam.getName()));
            String subtitle = textOr(exam.getExamUiHeaderSubtitle(), firstText(root, "paper_subject", "subject", "section"));
            PDImageXObject logo = imageFromSource(document, exam.getExamUiBannerImageUrl());
            if (logo != null) {
                Size logoSize = fit(logo.getWidth(), logo.getHeight(), 56f, 56f);
                stream.drawImage(logo, MARGIN, y(MARGIN + 6f, logoSize.height), logoSize.width, logoSize.height);
            }
            drawCentered(BOLD, 16f, Color.BLACK, title, MARGIN + 70f, PAGE.getWidth() - MARGIN - 70f, MARGIN + 18f);
            if (!subtitle.isBlank()) {
                drawCentered(REGULAR, 10.5f, Color.DARK_GRAY, subtitle, MARGIN + 70f, PAGE.getWidth() - MARGIN - 70f, MARGIN + 36f);
            }
            String role = answerKey ? "Answer Key" : "Question Paper";
            String meta = role + " | " + textOr(firstText(root, "paper_subject", "subject", "subject_name"), textOr(exam.getName(), "Exam"))
                    + " | Total: " + textOr(firstText(root, "total_marks"), "-") + "M";
            drawCentered(ITALIC, 9.5f, new Color(58, 71, 88), meta, MARGIN + 70f, PAGE.getWidth() - MARGIN - 70f, MARGIN + 54f);
            stream.setStrokingColor(Color.BLACK);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGIN, y(MARGIN + HEADER_HEIGHT, 0));
            stream.lineTo(PAGE.getWidth() - MARGIN, y(MARGIN + HEADER_HEIGHT, 0));
            stream.stroke();
        }

        private void drawWatermark() throws IOException {
            String text = textOr(exam.getExamUiHeaderTitle(), "");
            if (text.isBlank()) return;
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.setNonStrokingAlphaConstant(0.055f);
            stream.saveGraphicsState();
            stream.setGraphicsStateParameters(state);
            stream.setNonStrokingColor(new Color(30, 30, 30));
            stream.transform(Matrix.getRotateInstance(Math.toRadians(-38), PAGE.getWidth() * 0.22f, PAGE.getHeight() * 0.25f));
            stream.beginText();
            stream.setFont(BOLD, 60f);
            stream.newLineAtOffset(0, 0);
            stream.showText(safePdfText(text));
            stream.endText();
            stream.restoreGraphicsState();
        }

        private void drawFooter() throws IOException {
            drawText(stream, REGULAR, 8.5f, new Color(90, 90, 90), PAGE.getWidth() / 2f - 18f, 22f, "Page " + pageNumber);
        }

        private void drawCentered(PDType1Font font, float size, Color color, String text, float left, float right, float top) throws IOException {
            String safe = safePdfText(text);
            float width = font.getStringWidth(safe) / 1000f * size;
            float x = left + ((right - left - width) / 2f);
            drawText(stream, font, size, color, Math.max(left, x), baseline(top, size), safe);
        }
    }
}
