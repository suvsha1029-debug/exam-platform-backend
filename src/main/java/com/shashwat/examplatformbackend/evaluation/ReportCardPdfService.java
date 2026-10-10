package com.shashwat.examplatformbackend.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportCardPdfService {
    private static final float PDF_SAFE_MARGIN = 18f;
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final PDType1Font FONT_BOLD_ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE);
    private static final float DESIGN_WIDTH = 794f;
    private static final float DESIGN_HEIGHT = 1123f;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t([dh])([^>]*)>(.*?)</t\\1>");
    private static final Pattern STYLE_ATTR_PATTERN = Pattern.compile("(?is)style\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("(?is)<img[^>]*src\\s*=\\s*\"([^\"]+)\"");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] createPdf(ReportCardPreviewResponse data) {
        return new OfficialScoreCardRenderer().create(data);
    }

    public ScoreCardDocumentResponse createDocument(ReportCardPreviewResponse data, boolean downloadReleased) {
        byte[] bytes = createPdf(data);
        List<String> pages = new ArrayList<>();
        try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            for (int index = 0; index < pdf.getNumberOfPages(); index++) {
                BufferedImage page = renderer.renderImageWithDPI(index, 144, org.apache.pdfbox.rendering.ImageType.RGB);
                ByteArrayOutputStream image = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(page, "png", image);
                pages.add("data:image/png;base64," + Base64.getEncoder().encodeToString(image.toByteArray()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render score-card preview.", exception);
        }
        return new ScoreCardDocumentResponse(data.registrationId() + "_ScoreCard.pdf",
                downloadReleased ? "data:application/pdf;base64," + Base64.getEncoder().encodeToString(bytes) : null,
                List.copyOf(pages), data.candidateName(), data.applicationNumber());
    }

    public byte[] createPdf(ReportCardPreviewResponse data, String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            return createPdf(data);
        }
        try {
            List<TemplateElement> elements = parseTemplate(templateJson);
            if (elements.isEmpty()) {
                return createPdf(data);
            }
            return createTemplatePdf(data, elements);
        } catch (Exception ignored) {
            return createPdf(data);
        }
    }

    private List<TemplateElement> parseTemplate(String templateJson) throws IOException {
        JsonNode root = objectMapper.readTree(templateJson);
        JsonNode elementsNode = root.path("elements");
        if (!elementsNode.isArray()) {
            return List.of();
        }
        List<TemplateElement> elements = new ArrayList<>();
        for (JsonNode node : elementsNode) {
            elements.add(new TemplateElement(
                    text(node.path("id").asText(""), ""),
                    text(node.path("type").asText(""), ""),
                    node.path("x").asDouble(0),
                    node.path("y").asDouble(0),
                    node.path("w").asDouble(0),
                    node.path("h").asDouble(0),
                    node.path("z").asInt(1),
                    text(node.path("style").asText(""), ""),
                    text(node.path("innerHTML").asText(""), ""),
                    text(node.path("imgSrc").asText(""), ""),
                    text(node.path("mergeRole").asText(""), "")
            ));
        }
        elements.sort(Comparator.comparingInt(TemplateElement::z));
        return elements;
    }

    private byte[] createTemplatePdf(ReportCardPreviewResponse data, List<TemplateElement> elements) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float scaleX = (pageWidth - (PDF_SAFE_MARGIN * 2f)) / DESIGN_WIDTH;
            float scaleY = (pageHeight - (PDF_SAFE_MARGIN * 2f)) / DESIGN_HEIGHT;
            for (TemplateElement element : elements) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    renderElement(stream, document, data, element, pageHeight, scaleX, scaleY);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private void renderElement(PDPageContentStream stream, PDDocument document, ReportCardPreviewResponse data, TemplateElement element,
                               float pageHeight, float scaleX, float scaleY) throws IOException {
        Style style = Style.parse(element.style());
        float designX = (float) Math.max(0, Math.min(element.x(), DESIGN_WIDTH));
        float designY = (float) Math.max(0, Math.min(element.y(), DESIGN_HEIGHT));
        float designWidth = (float) Math.max(0, Math.min(element.w(), DESIGN_WIDTH - designX));
        float designHeight = (float) Math.max(0, Math.min(element.h(), DESIGN_HEIGHT - designY));
        if (designWidth <= 0 || designHeight <= 0) return;
        float x = PDF_SAFE_MARGIN + (designX * scaleX);
        float width = designWidth * scaleX;
        float height = designHeight * scaleY;
        float y = pageHeight - PDF_SAFE_MARGIN - (designY * scaleY) - height;
        switch (element.type()) {
            case "rct" -> drawRect(stream, x, y, width, height, style);
            case "cir" -> drawEllipse(stream, x, y, width, height, style);
            case "lin" -> drawLine(stream, x, y + (height / 2f), width, style);
            case "wm" -> drawTemplateWatermark(stream, x, y, width, height, scaleX, style, element.innerHTML());
            case "img", "photo" -> drawImageElement(stream, document, data, x, y, width, height, style, element);
            case "tbl" -> drawTableElement(stream, document, x, y, width, height, scaleX, style, element.innerHTML(), data.placeholders());
            case "txt" -> drawTextElement(stream, document, x, y, width, height, scaleX, style, element.innerHTML(), data.placeholders());
            default -> {
            }
        }
    }

    private void drawRect(PDPageContentStream stream, float x, float y, float width, float height, Style style) throws IOException {
        Color fill = style.fillColor();
        if (fill != null) {
            stream.setNonStrokingColor(fill);
            stream.addRect(x, y, width, height);
            stream.fill();
        }
        if (style.borderWidth() > 0 && style.borderColor() != null) {
            stream.setStrokingColor(style.borderColor());
            stream.setLineWidth(style.borderWidth());
            stream.addRect(x, y, width, height);
            stream.stroke();
        }
    }

    private void drawEllipse(PDPageContentStream stream, float x, float y, float width, float height, Style style) throws IOException {
        stream.saveGraphicsState();
        if (style.fillColor() != null) {
            stream.setNonStrokingColor(style.fillColor());
            addEllipsePath(stream, x, y, width, height);
            stream.fill();
        }
        if (style.borderWidth() > 0 && style.borderColor() != null) {
            stream.setStrokingColor(style.borderColor());
            stream.setLineWidth(style.borderWidth());
            addEllipsePath(stream, x, y, width, height);
            stream.stroke();
        }
        stream.restoreGraphicsState();
    }

    private void drawLine(PDPageContentStream stream, float x, float centerY, float width, Style style) throws IOException {
        stream.setStrokingColor(style.fillColor() != null ? style.fillColor() : colorOrDefault(style.borderColor(), Color.DARK_GRAY));
        stream.setLineWidth(Math.max(style.borderWidth(), Math.max(style.sourceHeight() * 0.75f, 1f)));
        stream.moveTo(x, centerY);
        stream.lineTo(x + width, centerY);
        stream.stroke();
    }

    private void drawTemplateWatermark(PDPageContentStream stream, float x, float y, float width, float height, float scaleX, Style style, String html) throws IOException {
        String text = plainText(applyPlaceholders(cleanInnerHtml(html), Map.of()));
        if (text.isBlank()) {
            text = "WATERMARK";
        }
        float fontSize = Math.max(style.fontSize(scaleX), 24f);
        float rotation = style.rotationDegrees();
        if (rotation == 0) {
            rotation = -30f;
        }
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(Math.max(0.05f, Math.min(style.opacity(), 0.35f)));
        stream.saveGraphicsState();
        stream.setGraphicsStateParameters(gs);
        stream.beginText();
        stream.setFont(resolveFont(style), fontSize);
        stream.setNonStrokingColor(colorOrDefault(style.textColor(), new Color(170, 170, 170)));
        stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rotation), x + (width / 2f), y + (height / 2f)));
        stream.showText(limitText(text, 120));
        stream.endText();
        stream.restoreGraphicsState();
    }

    private void drawImageElement(PDPageContentStream stream, PDDocument document, ReportCardPreviewResponse data,
                                  float x, float y, float width, float height, Style style, TemplateElement element) throws IOException {
        String role = text(element.mergeRole(), "").toLowerCase(Locale.ROOT);
        String dataUrl = switch (role) {
            case "photo" -> data.photoDataUrl();
            case "signature" -> data.signatureDataUrl();
            case "institute-logo" -> data.instituteLogoDataUrl();
            case "watermark-logo" -> data.watermarkLogoDataUrl();
            case "authority-signature" -> data.authoritySignatureDataUrl();
            default -> {
                String explicit = text(element.imgSrc(), "");
                if (!explicit.isBlank()) {
                    yield explicit;
                }
                yield extractImageSrc(element.innerHTML());
            }
        };
        drawRect(stream, x, y, width, height, style);
        PDImageXObject image = fromDataUrl(document, dataUrl);
        if (image == null) {
            if ("photo".equals(element.type()) || "photo".equals(role) || "signature".equals(role)) {
                drawCentered(stream, FONT_BOLD, 10f, new Color(120, 120, 120), x + (width / 2f), y + (height / 2f), "IMAGE");
            }
            return;
        }
        float inset = Math.max(2f, style.borderWidth());
        float boxWidth = Math.max(1f, width - (inset * 2));
        float boxHeight = Math.max(1f, height - (inset * 2));
        boolean fillSlot = "photo".equals(role) || "signature".equals(role) || "photo".equals(element.type());
        float scale = fillSlot
                ? Math.max(boxWidth / image.getWidth(), boxHeight / image.getHeight())
                : Math.min(boxWidth / image.getWidth(), boxHeight / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;
        float drawX = x + inset + ((boxWidth - drawWidth) / 2f);
        float drawY = y + inset + ((boxHeight - drawHeight) / 2f);
        if (fillSlot) {
            stream.saveGraphicsState();
            stream.addRect(x + inset, y + inset, boxWidth, boxHeight);
            stream.clip();
            stream.drawImage(image, drawX, drawY, drawWidth, drawHeight);
            stream.restoreGraphicsState();
            return;
        }
        stream.drawImage(image, drawX, drawY, drawWidth, drawHeight);
    }

    private void drawTextElement(PDPageContentStream stream, PDDocument document, float x, float y, float width, float height, float scaleX,
                                 Style style, String html, Map<String, String> placeholders) throws IOException {
        boolean containsPlaceholder = PLACEHOLDER_PATTERN.matcher(text(html, "")).find();
        String content = plainText(applyPlaceholders(cleanInnerHtml(html), placeholders));
        boolean hasBoxVisual = style.fillColor() != null || (style.borderWidth() > 0 && style.borderColor() != null);
        if (hasBoxVisual) {
            stream.saveGraphicsState();
            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(style.opacity());
            gs.setStrokingAlphaConstant(style.opacity());
            stream.setGraphicsStateParameters(gs);
            drawRect(stream, x, y, width, height, style);
            stream.restoreGraphicsState();
        }
        if (content.isBlank()) {
            return;
        }
        if (!containsPlaceholder) {
            drawStaticTextAsImage(stream, document, x, y, width, height, scaleX, style, content);
            return;
        }
        PDType1Font font = resolveFont(style);
        float fontSize = style.fontSize(scaleX);
        float lineHeight = Math.max(fontSize * style.lineHeight(), fontSize + 2f);
        float paddingX = Math.max(2f, style.paddingX() * scaleX);
        float paddingY = Math.max(2f, style.paddingY() * scaleX);
        float usableWidth = Math.max(10f, width - (paddingX * 2));
        List<String> lines = wrapLines(content, font, fontSize, usableWidth);
        float cursorY = y + height - paddingY - fontSize;
        Color color = colorOrDefault(style.textColor(), Color.BLACK);
        stream.saveGraphicsState();
        PDExtendedGraphicsState textGs = new PDExtendedGraphicsState();
        textGs.setNonStrokingAlphaConstant(style.opacity());
        stream.setGraphicsStateParameters(textGs);
        for (String line : lines) {
            if (cursorY < y + 2f) {
                break;
            }
            float lineWidth = font.getStringWidth(line) / 1000f * fontSize;
            float textX = x + paddingX;
            if ("center".equals(style.textAlign())) {
                textX = x + ((width - lineWidth) / 2f);
            } else if ("right".equals(style.textAlign())) {
                textX = x + width - paddingX - lineWidth;
            }
            drawText(stream, font, fontSize, color, textX, cursorY, line);
            cursorY -= lineHeight;
        }
        stream.restoreGraphicsState();
    }

    private void drawStaticTextAsImage(PDPageContentStream stream, PDDocument document, float x, float y, float width, float height, float scaleX,
                                       Style style, String content) throws IOException {
        int imageWidth = Math.max(1, Math.round(width * 2f));
        int imageHeight = Math.max(1, Math.round(height * 2f));
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setBackground(new Color(255, 255, 255, 0));
            graphics.clearRect(0, 0, imageWidth, imageHeight);
            Font awtFont = resolveAwtFont(style, Math.max(8f, style.fontSize(scaleX) * 2f));
            graphics.setFont(awtFont);
            graphics.setColor(withAlpha(colorOrDefault(style.textColor(), Color.BLACK), style.opacity()));
            FontMetrics metrics = graphics.getFontMetrics();
            float paddingX = Math.max(4f, style.paddingX() * scaleX * 2f);
            float paddingY = Math.max(4f, style.paddingY() * scaleX * 2f);
            float usableWidth = Math.max(10f, imageWidth - (paddingX * 2f));
            float lineHeight = Math.max(metrics.getHeight(), metrics.getHeight() * style.lineHeight());
            List<String> lines = wrapAwtLines(content, metrics, usableWidth);
            float cursorY = paddingY + metrics.getAscent();
            for (String line : lines) {
                if (cursorY > imageHeight - 2f) {
                    break;
                }
                float textX = paddingX;
                int lineWidth = metrics.stringWidth(line);
                if ("center".equals(style.textAlign())) {
                    textX = (imageWidth - lineWidth) / 2f;
                } else if ("right".equals(style.textAlign())) {
                    textX = imageWidth - paddingX - lineWidth;
                }
                graphics.drawString(line, textX, cursorY);
                cursorY += lineHeight;
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        PDImageXObject textImage = PDImageXObject.createFromByteArray(document, output.toByteArray(), "static-text");
        stream.drawImage(textImage, x, y, width, height);
    }

    private void drawTableElement(PDPageContentStream stream, PDDocument document, float x, float y, float width, float height, float scaleX,
                                  Style style, String html, Map<String, String> placeholders) throws IOException {
        String tableHtml = applyPlaceholders(cleanInnerHtml(html), placeholders);
        List<TableRow> rows = parseTableRows(tableHtml);
        if (rows.isEmpty()) {
            drawRect(stream, x, y, width, height, style);
            return;
        }
        float rowHeight = height / rows.size();
        float cursorY = y + height;
        for (TableRow row : rows) {
            int columns = Math.max(1, row.cells().size());
            float columnWidth = width / columns;
            float cursorX = x;
            cursorY -= rowHeight;
            for (TableCell cell : row.cells()) {
                Color fill = cell.style().fillColor();
                if (fill != null) {
                    stream.setNonStrokingColor(fill);
                    stream.addRect(cursorX, cursorY, columnWidth, rowHeight);
                    stream.fill();
                }
                stream.setStrokingColor(colorOrDefault(cell.style().borderColor(), new Color(120, 120, 120)));
                stream.setLineWidth(Math.max(0.7f, cell.style().borderWidth()));
                stream.addRect(cursorX, cursorY, columnWidth, rowHeight);
                stream.stroke();
                drawTextElement(stream, document, cursorX, cursorY, columnWidth, rowHeight, scaleX,
                        cell.style().withFallback(style), cell.html(), Map.of());
                cursorX += columnWidth;
            }
        }
    }

    private List<TableRow> parseTableRows(String html) {
        List<TableRow> rows = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<TableCell> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
            while (cellMatcher.find()) {
                String attributes = cellMatcher.group(2);
                String cellHtml = cellMatcher.group(3);
                Matcher styleMatcher = STYLE_ATTR_PATTERN.matcher(attributes);
                Style cellStyle = styleMatcher.find() ? Style.parse(styleMatcher.group(1)) : Style.empty();
                cells.add(new TableCell(cellHtml, cellStyle));
            }
            if (!cells.isEmpty()) {
                rows.add(new TableRow(cells));
            }
        }
        return rows;
    }

    private static List<String> wrapLines(String content, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : content.split("\\n", -1)) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = trimmed.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (font.getStringWidth(word) / 1000f * fontSize > maxWidth) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    List<String> chunks = splitPdfWord(word, font, fontSize, maxWidth);
                    for (int index = 0; index < chunks.size() - 1; index++) lines.add(chunks.get(index));
                    line.append(chunks.getLast());
                    continue;
                }
                String next = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(next) / 1000f * fontSize > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private static List<String> splitPdfWord(String word, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String candidate = chunk + character;
            if (!chunk.isEmpty() && font.getStringWidth(candidate) / 1000f * fontSize > maxWidth) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(character);
            offset += Character.charCount(codePoint);
        }
        if (!chunk.isEmpty()) chunks.add(chunk.toString());
        if (chunks.isEmpty()) chunks.add("");
        return chunks;
    }

    private static List<String> wrapAwtLines(String content, FontMetrics metrics, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : content.split("\\n", -1)) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = trimmed.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (metrics.stringWidth(word) > maxWidth) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    List<String> chunks = splitAwtWord(word, metrics, maxWidth);
                    for (int index = 0; index < chunks.size() - 1; index++) lines.add(chunks.get(index));
                    line.append(chunks.getLast());
                    continue;
                }
                String next = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(next) > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private static List<String> splitAwtWord(String word, FontMetrics metrics, float maxWidth) {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String candidate = chunk + character;
            if (!chunk.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(character);
            offset += Character.charCount(codePoint);
        }
        if (!chunk.isEmpty()) chunks.add(chunk.toString());
        if (chunks.isEmpty()) chunks.add("");
        return chunks;
    }

    private static String cleanInnerHtml(String html) {
        String cleaned = text(html, "");
        cleaned = cleaned.replaceAll("(?is)<div[^>]*class=\"rt-toolbar\"[^>]*>.*?</div>", "");
        cleaned = cleaned.replaceAll("(?is)<div[^>]*class=\"[^\"]*\\b(?:rh|mh)\\b[^\"]*\"[^>]*>.*?</div>", "");
        cleaned = cleaned.replaceAll("(?is)<div[^>]*class=\"[^\"]*\\b(?:rh|mh)\\b[^\"]*\"[^>]*/>", "");
        return cleaned;
    }

    private static String applyPlaceholders(String html, Map<String, String> placeholders) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(html);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = placeholders.getOrDefault(key, matcher.group());
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String plainText(String html) {
        String text = html == null ? "" : html;
        text = text.replaceAll("(?is)<br\\s*/?>", "\n");
        text = text.replaceAll("(?is)</div\\s*>", "\n");
        text = text.replaceAll("(?is)</p\\s*>", "\n");
        text = text.replaceAll("(?is)</tr\\s*>", "\n");
        text = text.replaceAll("(?is)<[^>]+>", "");
        return decodeHtmlEntities(text).replace("\r", "").trim();
    }

    private static String decodeHtmlEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static String extractImageSrc(String html) {
        Matcher matcher = IMG_SRC_PATTERN.matcher(text(html, ""));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static PDType1Font resolveFont(Style style) {
        boolean bold = style.bold();
        boolean italic = style.italic();
        String family = style.fontFamily().toLowerCase(Locale.ROOT);
        if (family.contains("times") || family.contains("georgia") || family.contains("serif")) {
            if (bold && italic) return new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC);
            if (bold) return new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            if (italic) return new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
            return new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        }
        if (family.contains("courier") || family.contains("mono")) {
            if (bold && italic) return new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE);
            if (bold) return new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            if (italic) return new PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE);
            return new PDType1Font(Standard14Fonts.FontName.COURIER);
        }
        if (bold && italic) return FONT_BOLD_ITALIC;
        if (bold) return FONT_BOLD;
        if (italic) return FONT_ITALIC;
        return FONT_REGULAR;
    }

    private static void addEllipsePath(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        float k = 0.552284749831f;
        float ox = (width / 2f) * k;
        float oy = (height / 2f) * k;
        float xe = x + width;
        float ye = y + height;
        float xm = x + (width / 2f);
        float ym = y + (height / 2f);
        stream.moveTo(x, ym);
        stream.curveTo(x, ym + oy, xm - ox, ye, xm, ye);
        stream.curveTo(xm + ox, ye, xe, ym + oy, xe, ym);
        stream.curveTo(xe, ym - oy, xm + ox, y, xm, y);
        stream.curveTo(xm - ox, y, x, ym - oy, x, ym);
        stream.closePath();
    }


    private static PDImageXObject fromDataUrl(PDDocument document, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
            return PDImageXObject.createFromByteArray(document, bytes, "embedded-image");
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }


    private static void drawText(PDPageContentStream stream, PDType1Font font, float size, Color color, float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(limitText(safe(text, "-"), 2000));
        stream.endText();
    }

    private static void drawCentered(PDPageContentStream stream, PDType1Font font, float size, Color color, float centerX, float baselineY, String text) throws IOException {
        String safeText = safe(text, "-");
        float width = font.getStringWidth(safeText) / 1000f * size;
        drawText(stream, font, size, color, centerX - (width / 2f), baselineY, safeText);
    }

    private static Color colorOrDefault(Color color, Color fallback) {
        return color == null ? fallback : color;
    }

    private static Color withAlpha(Color color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Font resolveAwtFont(Style style, float size) {
        String family = style.fontFamily().toLowerCase(Locale.ROOT);
        String awtFamily = family.contains("courier") || family.contains("mono")
                ? "Monospaced"
                : (family.contains("times") || family.contains("georgia") || family.contains("serif") ? "Serif" : "SansSerif");
        int fontStyle = Font.PLAIN;
        if (style.bold()) {
            fontStyle |= Font.BOLD;
        }
        if (style.italic()) {
            fontStyle |= Font.ITALIC;
        }
        return new Font(awtFamily, fontStyle, Math.max(1, Math.round(size)));
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private static String text(String value, String fallback) {
        return value == null ? fallback : value;
    }


    private static String limitText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record TemplateElement(
            String id,
            String type,
            double x,
            double y,
            double w,
            double h,
            int z,
            String style,
            String innerHTML,
            String imgSrc,
            String mergeRole
    ) {
    }

    private record TableRow(List<TableCell> cells) {
    }

    private record TableCell(String html, Style style) {
    }

    private record Style(
            String raw,
            Color fillColor,
            Color borderColor,
            float borderWidth,
            Color textColor,
            String fontFamily,
            boolean bold,
            boolean italic,
            String textAlign,
            float sourceFontSize,
            float lineHeight,
            float paddingX,
            float paddingY,
            float opacity,
            float rotationDegrees,
            float sourceHeight
    ) {
        static Style empty() {
            return parse("");
        }

        static Style parse(String raw) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String entry : text(raw, "").split(";")) {
                int index = entry.indexOf(':');
                if (index <= 0) {
                    continue;
                }
                map.put(entry.substring(0, index).trim().toLowerCase(Locale.ROOT), entry.substring(index + 1).trim());
            }
            String border = map.getOrDefault("border", "");
            float borderWidth = number(map.get("border-width"), number(border, 0));
            Color borderColor = parseColor(map.get("border-color"));
            if (borderColor == null && border.contains("rgb")) {
                borderColor = parseColor(border.substring(border.indexOf("rgb")));
            }
            if (borderColor == null && border.contains("#")) {
                borderColor = parseColor(border.substring(border.indexOf('#')));
            }
            if (borderColor == null && !border.isBlank()) {
                borderColor = new Color(130, 130, 130);
            }
            Color fill = parseColor(firstNonBlank(map.get("background"), map.get("background-color")));
            Color textColor = parseColor(firstNonBlank(map.get("color"), "#000000"));
            String fontFamily = firstNonBlank(map.get("font-family"), "Arial, sans-serif");
            boolean bold = firstNonBlank(map.get("font-weight"), "").contains("bold")
                    || firstNonBlank(map.get("font-weight"), "").contains("700");
            boolean italic = firstNonBlank(map.get("font-style"), "").contains("italic")
                    || firstNonBlank(map.get("font-style"), "").contains("oblique");
            String textAlign = firstNonBlank(map.get("text-align"), "left").toLowerCase(Locale.ROOT);
            float opacity = Math.max(0.05f, Math.min(1f, number(map.get("opacity"), 1f)));
            float rotation = rotationDegrees(map.get("transform"));
            return new Style(
                    raw,
                    fill,
                    borderColor,
                    borderWidth <= 0 ? 0 : borderWidth,
                    textColor,
                    fontFamily,
                    bold,
                    italic,
                    textAlign,
                    Math.max(10f, number(map.get("font-size"), 14f)),
                    Math.max(1.1f, number(map.get("line-height"), 1.25f)),
                    Math.max(2f, number(map.get("padding-left"), number(map.get("padding"), 6f))),
                    Math.max(2f, number(map.get("padding-top"), number(map.get("padding"), 4f))),
                    opacity,
                    rotation,
                    Math.max(1f, number(map.get("height"), 0f))
            );
        }

        Style withFallback(Style fallback) {
            return new Style(
                    raw,
                    fillColor != null ? fillColor : fallback.fillColor,
                    borderColor != null ? borderColor : fallback.borderColor,
                    borderWidth > 0 ? borderWidth : fallback.borderWidth,
                    textColor != null ? textColor : fallback.textColor,
                    !fontFamily.isBlank() ? fontFamily : fallback.fontFamily,
                    bold || fallback.bold,
                    italic || fallback.italic,
                    !textAlign.isBlank() ? textAlign : fallback.textAlign,
                    sourceFontSize > 0 ? sourceFontSize : fallback.sourceFontSize,
                    lineHeight > 0 ? lineHeight : fallback.lineHeight,
                    paddingX > 0 ? paddingX : fallback.paddingX,
                    paddingY > 0 ? paddingY : fallback.paddingY,
                    opacity > 0 ? opacity : fallback.opacity,
                    rotationDegrees != 0 ? rotationDegrees : fallback.rotationDegrees,
                    sourceHeight > 0 ? sourceHeight : fallback.sourceHeight
            );
        }

        float fontSize(float scaleX) {
            return Math.max(6f, sourceFontSize * 0.92f * scaleX);
        }

        private static float rotationDegrees(String transform) {
            if (transform == null || transform.isBlank()) {
                return 0f;
            }
            Matcher matcher = Pattern.compile("rotate\\(([-0-9.]+)deg\\)", Pattern.CASE_INSENSITIVE).matcher(transform);
            return matcher.find() ? number(matcher.group(1), 0f) : 0f;
        }

        private static float number(String value, float fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            Matcher matcher = Pattern.compile("[-0-9.]+").matcher(value);
            if (!matcher.find()) {
                return fallback;
            }
            try {
                return Float.parseFloat(matcher.group());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static Color parseColor(String value) {
            if (value == null || value.isBlank() || "transparent".equalsIgnoreCase(value)) {
                return null;
            }
            String color = value.trim();
            try {
                if (color.startsWith("#")) {
                    String hex = color.substring(1);
                    if (hex.length() == 3) {
                        hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                    }
                    int rgb = Integer.parseInt(hex.substring(0, Math.min(6, hex.length())), 16);
                    return new Color(rgb);
                }
                if (color.toLowerCase(Locale.ROOT).startsWith("rgb")) {
                    Matcher matcher = Pattern.compile("([0-9.]+)").matcher(color);
                    List<Integer> parts = new ArrayList<>();
                    while (matcher.find()) {
                        parts.add(Math.max(0, Math.min(255, Math.round(Float.parseFloat(matcher.group(1))))));
                    }
                    if (parts.size() >= 3) {
                        return new Color(parts.get(0), parts.get(1), parts.get(2));
                    }
                }
            } catch (Exception ignored) {
                return null;
            }
            return switch (color.toLowerCase(Locale.ROOT)) {
                case "white" -> Color.WHITE;
                case "black" -> Color.BLACK;
                case "gray", "grey" -> Color.GRAY;
                case "lightgray", "lightgrey" -> Color.LIGHT_GRAY;
                case "darkgray", "darkgrey" -> Color.DARK_GRAY;
                case "red" -> Color.RED;
                case "blue" -> Color.BLUE;
                case "green" -> Color.GREEN;
                case "yellow" -> Color.YELLOW;
                default -> null;
            };
        }

        private static String firstNonBlank(String first, String second) {
            return (first != null && !first.isBlank()) ? first : Objects.requireNonNullElse(second, "");
        }
    }
}
