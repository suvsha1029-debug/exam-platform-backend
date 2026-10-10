package com.shashwat.examplatformbackend.evaluation;

import com.shashwat.examplatformbackend.exam.ScoreCardAppearance;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReportCardPdfServiceTests {
    private ReportCardPreviewResponse fixture(String notes) throws Exception {
        ScoreCardAppearance appearance = new ScoreCardAppearance("SAMPLE INSTITUTE OF TECHNOLOGY", "#f5f8f9", "#153e50", "#967344", true, 6,
                "About this score", notes, sampleImage(false));
        return new ReportCardPreviewResponse(
                "EX2027DEMO", "Engineering Aptitude Examination 2027",
                "APP27-100384", "REG-2027-100384", "ALEXANDER BENNETT",
                "2002-08-19", "COMPUTER SCIENCE", 78.5, 100.0, 78.5,
                43, 12, 10, 18, 30210, "2027-02-14T10:15:30Z", "14 Feb 2027",
                sampleImage(true), "", sampleImage(false), sampleImage(false), "", "Examination Office", "Controller of Examinations",
                "Subject to the published result and examination rules.", Map.of("ScoreCardAppearance", appearance.toJson()));
    }

    @Test
    void previewPixelsAreRenderedFromTheExactDownloadPdf() throws Exception {
        ScoreCardDocumentResponse result = new ReportCardPdfService().createDocument(fixture(
                "This sample illustrates the released score, examination record and authority details.\nThe marks and rank shown here are demonstration data."), true);
        byte[] pdfBytes = bytes(result.pdfDataUrl());
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            long repeatedIds = new org.apache.pdfbox.pdfparser.PDFStreamParser(document.getPage(0)).parse().stream()
                    .filter(token -> token instanceof org.apache.pdfbox.cos.COSString value && value.getString().equals("APP27-100384"))
                    .count();
            assertTrue(repeatedIds >= 30, "Application watermark should repeat across the page");
            for (String value : new String[]{"SCORE CARD", "ALEXANDER BENNETT", "APP27-100384", "REG-2027-100384",
                    "78.50", "30210", "CORRECT", "INCORRECT", "UNANSWERED", "SAMPLE INSTITUTE OF TECHNOLOGY", "demonstration data"}) {
                assertTrue(text.contains(value), value);
            }
            BufferedImage expected = new PDFRenderer(document).renderImageWithDPI(0, 144, ImageType.RGB);
            BufferedImage actual = ImageIO.read(new ByteArrayInputStream(bytes(result.pages().getFirst())));
            assertEquals(expected.getWidth(), actual.getWidth());
            assertEquals(expected.getHeight(), actual.getHeight());
            assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                    actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth()));
        }
        String samplePath = System.getenv().getOrDefault("SCORE_CARD_SAMPLE_PATH", "");
        if (!samplePath.isBlank()) {
            Files.write(Path.of(samplePath), pdfBytes);
            Files.write(Path.of(samplePath.replace(".pdf", ".png")), bytes(result.pages().getFirst()));
            Files.writeString(Path.of(samplePath.replace(".pdf", ".json")), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
        }
    }

    @Test
    void previewDoesNotExposePdfBeforeDownloadRelease() throws Exception {
        ScoreCardDocumentResponse result = new ReportCardPdfService().createDocument(fixture(""), false);
        assertNull(result.pdfDataUrl());
        assertFalse(result.pages().isEmpty());
    }

    @Test
    void longClosingInformationContinuesWithoutLosingText() throws Exception {
        String notes = "The examination authority publishes score interpretation and eligibility information. ".repeat(65) + "END OF SCORE INFORMATION";
        byte[] pdf = new ReportCardPdfService().createPdf(fixture(notes));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("END OF SCORE INFORMATION"));
            assertEquals(65, text.split("eligibility", -1).length - 1);
        }
    }

    private static byte[] bytes(String dataUrl) { return Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)); }

    private static String sampleImage(boolean portrait) throws Exception {
        BufferedImage image = new BufferedImage(180, portrait ? 240 : 180, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(226, 233, 236)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(82, 106, 119));
        if (portrait) { g.fillOval(58, 30, 64, 78); g.fillRoundRect(27, 121, 126, 150, 58, 58); }
        else { g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 49)); g.drawString("SIT", 45, 100); }
        g.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
