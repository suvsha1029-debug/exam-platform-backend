package com.shashwat.examplatformbackend.evaluation;

import com.shashwat.examplatformbackend.exam.ScoreCardAppearance;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** The candidate preview is rasterized from these same PDF pages. */
final class OfficialScoreCardRenderer {
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font MONO = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
    private static final Color INK = new Color(25, 34, 42);
    private static final Color MUTED = new Color(78, 91, 101);
    private static final float LEFT = 36, WIDTH = PDRectangle.A4.getWidth() - 72;
    private PDDocument document;
    private ReportCardPreviewResponse data;
    private ScoreCardAppearance appearance;
    private Color accent;
    private Color strip;

    byte[] create(ReportCardPreviewResponse record) {
        data = record;
        appearance = ScoreCardAppearance.fromJson(record.placeholders() == null ? null : record.placeholders().get("ScoreCardAppearance"));
        accent = color(appearance.accentColor(), "#153e50");
        strip = color(appearance.stripColor(), "#967344");
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document = pdf;
            pdf.getDocumentInformation().setTitle(value(data.examName()) + " - Score Card - " + value(data.applicationNumber()));
            pdf.getDocumentInformation().setSubject("Released candidate score card");
            PDPage page = addPage();
            boolean notesOnExtraPage;
            try (PDPageContentStream s = new PDPageContentStream(pdf, page)) {
                background(s);
                image(s, data.instituteLogoDataUrl(), 38, 737, 61, 65, false);
                float headerX = validImage(data.instituteLogoDataUrl()) ? 115 : LEFT;
                block(s, BOLD, 12, accent, headerX, 793, LEFT + WIDTH - headerX, 2,
                        blank(appearance.instituteName()) ? "EXAMINATION AUTHORITY" : appearance.instituteName());
                block(s, BOLD, 16, INK, headerX, 756, LEFT + WIDTH - headerX, 2, value(data.examName()));
                text(s, BOLD, 21, accent, LEFT, 703, "SCORE CARD");
                block(s, MONO, 9, MUTED, 360, 718, 199, 2, value(data.examCode()));
                line(s, LEFT, 689, LEFT + WIDTH, 689, accent, 1);

                section(s, 672, "01", "CANDIDATE RECORD");
                photoStamp(s, 118, 582);
                field(s, 211, 651, 347, "CANDIDATE NAME", data.candidateName(), 14, 3);
                field(s, 211, 598, 162, "APPLICATION NUMBER", data.applicationNumber(), 10, 2);
                field(s, 390, 598, 169, "REGISTRATION ID", data.registrationId(), 10, 2);
                field(s, 211, 552, 162, "DATE OF BIRTH", date(data.dateOfBirth()), 11, 2);
                field(s, 390, 552, 169, "STREAM / PAPER", data.streamCode(), 10, 2);

                section(s, 494, "02", "SCORE SUMMARY");
                float cell = WIDTH / 4;
                String[] labels = {"MARKS OBTAINED", "MAXIMUM MARKS", "PERCENTAGE", "RANK"};
                String[] values = {decimal(data.score()), decimal(data.maxMarks()),
                        data.percentage() == null ? "-" : decimal(data.percentage()) + "%", value(data.rank())};
                for (int i = 0; i < 4; i++) {
                    float x = LEFT + cell * i;
                    rect(s, x, 412, cell, 66, null, accent);
                    text(s, BOLD, 7.5f, MUTED, x + 10, 461, labels[i]);
                    block(s, BOLD, 23, accent, x + 10, 437, cell - 20, 1, values[i]);
                }
                section(s, 393, "03", "EXAMINATION DETAILS");
                field(s, LEFT + 3, 374, 157, "EXAM CONDUCTED ON", value(data.examConductedOn()), 10, 2);
                field(s, LEFT + 180, 374, 164, "RESPONSE SUBMITTED", timestamp(data.submittedAt()), 9.5f, 2);
                field(s, LEFT + 365, 374, 154, "CANDIDATES EVALUATED", value(data.candidateCount()), 11, 2);
                section(s, 330, "04", "RESPONSE SUMMARY");
                String[] counts = {value(data.correctCount()), value(data.wrongCount()), value(data.blankCount())};
                String[] countLabels = {"CORRECT", "INCORRECT", "UNANSWERED"};
                for (int i = 0; i < 3; i++) {
                    float x = LEFT + WIDTH * i / 3;
                    rect(s, x, 265, WIDTH / 3, 51, null, strip);
                    text(s, BOLD, 8, MUTED, x + 10, 299, countLabels[i]);
                    text(s, BOLD, 17, INK, x + 10, 278, counts[i]);
                }
                if (!blank(data.validityText())) block(s, REGULAR, 8.5f, INK, LEFT, 248, WIDTH, 2, "Validity: " + data.validityText());
                float candidateSignatureBottom = signature(s, LEFT + 4, data.signatureDataUrl(), "CANDIDATE SIGNATURE", "", "");
                float authoritySignatureBottom = signature(s, LEFT + WIDTH - 204, data.authoritySignatureDataUrl(), "AUTHORISED SIGNATORY",
                        data.authorityName(), data.authorityDesignation());

                boolean hasNotes = !blank(appearance.notesText()) || !blank(appearance.notesImageDataUrl());
                float notesTop = Math.min(120, Math.min(candidateSignatureBottom, authoritySignatureBottom) - 16);
                float notesHeight = notesTop - 19 - 58;
                float notesWidth = WIDTH - (validImage(appearance.notesImageDataUrl()) ? 98 : 0);
                notesOnExtraPage = hasNotes && (lines(appearance.notesText(), REGULAR, 8.5f, notesWidth).size() * 11.05f > notesHeight
                        || (validImage(appearance.notesImageDataUrl()) && notesHeight < 32));
                if (hasNotes && !notesOnExtraPage) {
                    section(s, notesTop, "05", value(appearance.notesTitle()).toUpperCase(Locale.ROOT));
                    float x = LEFT;
                    if (validImage(appearance.notesImageDataUrl())) {
                        image(s, appearance.notesImageDataUrl(), LEFT, 59, 86, Math.min(47, notesHeight), false);
                        x += 98;
                    }
                    paragraph(s, REGULAR, 8.5f, INK, x, notesTop - 19, notesWidth, appearance.notesText());
                }
            }
            if (notesOnExtraPage) appendNotes();
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                try (PDPageContentStream s = new PDPageContentStream(pdf, pdf.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    line(s, LEFT, 48, LEFT + WIDTH, 48, strip, 0.6f);
                    block(s, MONO, 8.5f, INK, LEFT, 35, WIDTH - 58, 1, value(data.applicationNumber()) + " / " + value(data.examCode()));
                    text(s, REGULAR, 8, MUTED, LEFT + WIDTH - 42, 35, (i + 1) + " / " + pdf.getNumberOfPages());
                    text(s, REGULAR, 6.5f, MUTED, LEFT, 23, "Computer-generated score card. Subject to the examination authority's published rules.");
                }
            }
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build score card PDF.", exception);
        }
    }

    private PDPage addPage() {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    private void background(PDPageContentStream s) throws IOException {
        rect(s, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(), color(appearance.backgroundColor(), "#ffffff"), null);
        if (appearance.stripsEnabled()) {
            rect(s, LEFT, 817, WIDTH * .73f, 3, accent, null);
            rect(s, LEFT + WIDTH * .73f, 817, WIDTH * .27f, 3, strip, null);
            rect(s, 22, 63, 2, 730, strip, null);
        }
        s.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(Math.max(.02f, Math.min(.15f, appearance.watermarkOpacity() / 100f)));
        s.setGraphicsStateParameters(state);
        String app = blank(data.applicationNumber()) ? value(data.registrationId()) : data.applicationNumber();
        float watermarkSize = Math.min(12, 170 / Math.max(1, width(MONO, 1, app)));
        for (float y = 80; y < 790; y += 72) {
            for (float x = 38; x < 550; x += 180) {
                s.saveGraphicsState();
                s.transform(Matrix.getRotateInstance(Math.toRadians(26), x, y));
                block(s, MONO, watermarkSize, accent, 0, 0, 172, 1, app);
                s.restoreGraphicsState();
            }
        }
        s.restoreGraphicsState();
        s.saveGraphicsState();
        state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(.055f);
        s.setGraphicsStateParameters(state);
        image(s, blank(data.watermarkLogoDataUrl()) ? data.instituteLogoDataUrl() : data.watermarkLogoDataUrl(), 158, 303, 280, 280, false);
        s.restoreGraphicsState();
    }

    private void photoStamp(PDPageContentStream s, float cx, float cy) throws IOException {
        s.setStrokingColor(accent);
        s.setLineWidth(.45f);
        circle(s, cx, cy, 75);
        circle(s, cx, cy, 63);
        for (int i = 0; i < 120; i++) {
            double angle = i * Math.PI * 2 / 120;
            float r = i % 5 == 0 ? 61 : 62;
            line(s, cx + (float)Math.cos(angle) * r, cy + (float)Math.sin(angle) * r,
                    cx + (float)Math.cos(angle) * 64, cy + (float)Math.sin(angle) * 64, accent, i % 3 == 0 ? .7f : .25f);
        }
        String inscription = (value(data.applicationNumber()) + "  /  CANDIDATE RECORD  /  ").repeat(2);
        float size = Math.min(5.4f, (float)(2 * Math.PI * 70) / inscription.length() / .64f);
        for (int i = 0; i < inscription.length(); i++) {
            double angle = Math.PI / 2 - i * Math.PI * 2 / inscription.length();
            s.saveGraphicsState();
            s.transform(Matrix.getRotateInstance(angle - Math.PI / 2,
                    cx + (float)Math.cos(angle) * 69, cy + (float)Math.sin(angle) * 69));
            text(s, MONO, size, accent, 0, 0, inscription.substring(i, i + 1));
            s.restoreGraphicsState();
        }
        rect(s, cx - 35, cy - 46, 70, 92, color(appearance.backgroundColor(), "#ffffff"), accent);
        if (!image(s, data.photoDataUrl(), cx - 33, cy - 44, 66, 88, true)) {
            text(s, BOLD, 7, MUTED, cx - 27, cy + 2, "PHOTO NOT");
            text(s, BOLD, 7, MUTED, cx - 19, cy - 10, "ON FILE");
        }
    }

    private void appendNotes() throws IOException {
        List<String> remaining = lines(appearance.notesText(), REGULAR, 10, WIDTH);
        int index = 0;
        boolean first = true;
        do {
            PDPage page = addPage();
            try (PDPageContentStream s = new PDPageContentStream(document, page)) {
                background(s);
                block(s, BOLD, 12, accent, LEFT, 790, WIDTH, 2, value(data.examName()));
                section(s, 749, "05", value(appearance.notesTitle()).toUpperCase(Locale.ROOT) + (first ? "" : " / CONTINUED"));
                float y = 723;
                while (index < remaining.size() && y >= 75) {
                    text(s, REGULAR, 10, INK, LEFT, y, remaining.get(index++));
                    y -= 14;
                }
                if (index == remaining.size() && validImage(appearance.notesImageDataUrl())) {
                    float imageHeight = Math.min(150, y - 76);
                    if (imageHeight >= 65) {
                        image(s, appearance.notesImageDataUrl(), LEFT, y - imageHeight - 10, WIDTH, imageHeight, false);
                        break;
                    }
                    remaining.add("");
                }
            }
            first = false;
        } while (index < remaining.size());
    }

    private float signature(PDPageContentStream s, float x, String source, String title, String name, String designation) throws IOException {
        boolean rendered = image(s, source, x + 3, 180, 190, 40, false);
        if (!rendered && (!blank(name) || !blank(designation))) text(s, REGULAR, 7, MUTED, x, 185, "Signature not supplied");
        line(s, x, 177, x + 200, 177, INK, .55f);
        text(s, BOLD, 8, INK, x, 164, title);
        float cursor = 151;
        if (!blank(name)) cursor -= block(s, BOLD, 8.5f, INK, x, cursor, 200, 2, name) + 2;
        if (!blank(designation)) cursor -= block(s, REGULAR, 8, MUTED, x, cursor, 200, 2, designation);
        if (blank(name) && blank(designation) && !rendered) { text(s, REGULAR, 8, MUTED, x, 151, "Not on file"); cursor -= 10; }
        return cursor + 6;
    }

    private void section(PDPageContentStream s, float y, String number, String title) throws IOException {
        text(s, MONO, 8, strip, LEFT, y, number);
        block(s, BOLD, 9, accent, LEFT + 25, y, WIDTH - 25, 1, title);
        line(s, LEFT, y - 7, LEFT + WIDTH, y - 7, strip, .4f);
    }

    private void field(PDPageContentStream s, float x, float top, float w, String label, Object content, float size, int rows) throws IOException {
        text(s, BOLD, 7.2f, MUTED, x, top, label);
        block(s, BOLD, size, INK, x, top - 17, w, rows, value(content));
    }

    private float block(PDPageContentStream s, PDType1Font font, float size, Color color, float x, float top, float w, int rows, String content) throws IOException {
        List<String> wrapped = lines(content, font, size, w);
        while (wrapped.size() > rows && size > 5) {
            size -= .25f;
            wrapped = lines(content, font, size, w);
        }
        float y = top;
        for (String row : wrapped) {
            text(s, font, size, color, x, y, row);
            y -= size * 1.25f;
        }
        return top - y;
    }

    private void paragraph(PDPageContentStream s, PDType1Font font, float size, Color color, float x, float top, float w, String content) throws IOException {
        for (String row : lines(content, font, size, w)) {
            text(s, font, size, color, x, top, row);
            top -= size * 1.3f;
        }
    }

    private static List<String> lines(String content, PDType1Font font, float size, float w) throws IOException {
        List<String> result = new ArrayList<>();
        for (String paragraph : (content == null ? "" : content).replace('\r', ' ').split("\n", -1)) {
            String line = "";
            for (String word : paragraph.trim().split("\\s+")) {
                String next = line.isBlank() ? word : line + " " + word;
                if (width(font, size, next) <= w) { line = next; continue; }
                if (!line.isBlank()) result.add(line);
                line = "";
                for (int point : word.codePoints().toArray()) {
                    String character = new String(Character.toChars(point));
                    if (!line.isEmpty() && width(font, size, line + character) > w) { result.add(line); line = ""; }
                    line += character;
                }
            }
            result.add(line);
        }
        return result;
    }

    private static float width(PDType1Font font, float size, String text) throws IOException {
        try { return font.getStringWidth(text) * size / 1000; }
        catch (IllegalArgumentException unsupported) {
            return (float)awt(font, size).getStringBounds(text, new java.awt.font.FontRenderContext(null, true, true)).getWidth();
        }
    }

    private void text(PDPageContentStream s, PDType1Font font, float size, Color color, float x, float y, String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        Color paper = color(appearance.backgroundColor(), "#ffffff");
        if (contrast(color, paper) < 4.5) color = contrast(Color.WHITE, paper) > contrast(Color.BLACK, paper) ? Color.WHITE : Color.BLACK;
        try { font.encode(text); }
        catch (IllegalArgumentException unsupported) {
            // Preserve names outside WinAnsi instead of replacing their characters with '?'.
            Font fallback = awt(font, size * 3);
            BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gm = measure.createGraphics();
            gm.setFont(fallback);
            var metrics = gm.getFontMetrics();
            int w = Math.max(1, metrics.stringWidth(text) + 6), h = metrics.getHeight() + 4, ascent = metrics.getAscent();
            gm.dispose();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(fallback); g.setColor(color); g.drawString(text, 0, ascent); g.dispose();
            s.drawImage(LosslessFactory.createFromImage(document, image), x, y - (h - ascent) / 3f, w / 3f, h / 3f);
            return;
        }
        s.beginText(); s.setFont(font, size); s.setNonStrokingColor(color); s.newLineAtOffset(x, y); s.showText(text); s.endText();
    }

    private static Font awt(PDType1Font font, float size) {
        return new Font(font == MONO ? Font.MONOSPACED : Font.SANS_SERIF, font == REGULAR ? Font.PLAIN : Font.BOLD, 1).deriveFont(size);
    }

    private boolean image(PDPageContentStream s, String source, float x, float y, float w, float h, boolean crop) throws IOException {
        if (!validImage(source)) return false;
        PDImageXObject image;
        try { image = PDImageXObject.createFromByteArray(document, Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1)), "score-card-image"); }
        catch (IOException | IllegalArgumentException invalid) { return false; }
        float ratio = crop ? Math.max(w / image.getWidth(), h / image.getHeight()) : Math.min(w / image.getWidth(), h / image.getHeight());
        float iw = image.getWidth() * ratio, ih = image.getHeight() * ratio;
        s.saveGraphicsState(); s.addRect(x, y, w, h); s.clip();
        s.drawImage(image, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih); s.restoreGraphicsState();
        return true;
    }

    private static boolean validImage(String source) { return source != null && source.startsWith("data:image/") && source.contains(";base64,"); }
    private static void line(PDPageContentStream s, float x1, float y1, float x2, float y2, Color color, float thickness) throws IOException {
        s.setStrokingColor(color); s.setLineWidth(thickness); s.moveTo(x1, y1); s.lineTo(x2, y2); s.stroke();
    }
    private static void rect(PDPageContentStream s, float x, float y, float w, float h, Color fill, Color border) throws IOException {
        if (fill != null) { s.setNonStrokingColor(fill); s.addRect(x, y, w, h); s.fill(); }
        if (border != null) { s.setStrokingColor(border); s.setLineWidth(.6f); s.addRect(x, y, w, h); s.stroke(); }
    }
    private static void circle(PDPageContentStream s, float x, float y, float r) throws IOException {
        float c = r * .55228475f;
        s.moveTo(x + r, y); s.curveTo(x + r, y + c, x + c, y + r, x, y + r);
        s.curveTo(x - c, y + r, x - r, y + c, x - r, y);
        s.curveTo(x - r, y - c, x - c, y - r, x, y - r);
        s.curveTo(x + c, y - r, x + r, y - c, x + r, y); s.closePath(); s.stroke();
    }
    private static Color color(String hex, String fallback) { try { return Color.decode(hex); } catch (RuntimeException bad) { return Color.decode(fallback); } }
    private static double luminance(Color color) {
        double[] rgb = { color.getRed() / 255d, color.getGreen() / 255d, color.getBlue() / 255d };
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
    }
    private static double contrast(Color foreground, Color background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String value(Object value) { return value == null || value.toString().isBlank() ? "-" : value.toString().trim(); }
    private static String decimal(Double value) { return value == null ? "-" : String.format(Locale.ROOT, "%.2f", value); }
    private static String date(String value) {
        try { return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)); }
        catch (RuntimeException ignored) { return value(value); }
    }
    private static String timestamp(String value) {
        try { return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata")).format(Instant.parse(value)); }
        catch (RuntimeException ignored) { return value(value); }
    }
}
