package com.chat.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentGeneratorService.class);

    private final FileService fileService;

    public DocumentGeneratorService(FileService fileService) {
        this.fileService = fileService;
    }

    public String generateDocument(String type, String title, String content) {
        try {
            Path outputDir = fileService.getUploadPath().resolve("generated");
            Files.createDirectories(outputDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeTitle.length() > 50) safeTitle = safeTitle.substring(0, 50);

            List<String> paragraphs = List.of(content.split("\n"));

            String url = switch (type.toLowerCase()) {
                case "docx", "word" -> generateDocx(outputDir, safeTitle, timestamp, paragraphs);
                case "pptx", "ppt" -> generatePptx(outputDir, safeTitle, timestamp, paragraphs);
                case "pdf" -> generatePdf(outputDir, safeTitle, timestamp, paragraphs);
                case "md", "markdown" -> generateMd(outputDir, safeTitle, timestamp, paragraphs);
                default -> generateTxt(outputDir, safeTitle, timestamp, paragraphs);
            };
            log.info("文档生成成功: {} ({})", url, type);
            return url;
        } catch (Exception e) {
            log.error("文档生成失败", e);
            throw new RuntimeException("文档生成失败: " + e.getMessage());
        }
    }

    // ==== Word (.docx) ====
    private String generateDocx(Path dir, String title, String ts, List<String> paragraphs) throws IOException {
        String fileName = title + "_" + ts + ".docx";
        Path path = dir.resolve(fileName);

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(22);
            titleRun.setFontFamily("微软雅黑");

            doc.createParagraph();

            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty()) { doc.createParagraph(); continue; }

                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.LEFT);
                p.setSpacingBetween(1.15);

                boolean isHeading = trimmed.startsWith("# ") || trimmed.startsWith("## ");

                XWPFRun run = p.createRun();
                run.setText(trimmed.replaceAll("^#+ ", "").replaceAll("^[-*] ", "• "));
                run.setFontFamily("微软雅黑");
                run.setFontSize(isHeading ? 16 : 11);
                run.setBold(isHeading);
            }

            try (FileOutputStream out = new FileOutputStream(path.toFile())) { doc.write(out); }
        }
        log.info("DOCX: {}", fileName);
        return "/api/files/generated/" + fileName;
    }

    // ==== PPT (.pptx) ====
    private String generatePptx(Path dir, String title, String ts, List<String> paragraphs) throws IOException {
        String fileName = title + "_" + ts + ".pptx";
        Path path = dir.resolve(fileName);

        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new java.awt.Dimension(1024, 768));

            // 封面
            XSLFSlide cover = ppt.createSlide();
            XSLFTextBox box = cover.createTextBox();
            box.setAnchor(new java.awt.Rectangle(100, 150, 800, 200));
            XSLFTextParagraph tp = box.addNewTextParagraph();
            XSLFTextRun tr = tp.addNewTextRun();
            tr.setText(title);
            tr.setFontSize(40.0);
            tr.setBold(true);

            XSLFTextBox subBox = cover.createTextBox();
            subBox.setAnchor(new java.awt.Rectangle(100, 380, 800, 80));
            XSLFTextParagraph sp = subBox.addNewTextParagraph();
            XSLFTextRun sr = sp.addNewTextRun();
            sr.setText("AI Chat Platform · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            sr.setFontSize(18.0);

            // 内容页
            StringBuilder pageContent = new StringBuilder();
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty() && !pageContent.isEmpty()) {
                    createPptSlide(ppt, pageContent.toString());
                    pageContent = new StringBuilder();
                    continue;
                }
                if (trimmed.startsWith("# ")) {
                    if (!pageContent.isEmpty()) createPptSlide(ppt, pageContent.toString());
                    pageContent = new StringBuilder(trimmed.substring(2).trim()).append("\n");
                    continue;
                }
                pageContent.append(trimmed).append("\n");
            }
            if (!pageContent.isEmpty()) createPptSlide(ppt, pageContent.toString());

            try (FileOutputStream out = new FileOutputStream(path.toFile())) { ppt.write(out); }
        }
        log.info("PPTX: {}", fileName);
        return "/api/files/generated/" + fileName;
    }

    private void createPptSlide(XMLSlideShow ppt, String content) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(new java.awt.Rectangle(60, 60, 900, 650));

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            XSLFTextParagraph tp = textBox.addNewTextParagraph();
            XSLFTextRun tr = tp.addNewTextRun();
            tr.setText(line.replaceAll("^[-*] ", "• "));

            boolean isTitle = line.length() < 40 && i == 0;
            tr.setFontSize(isTitle ? 28.0 : 18.0);
            tr.setBold(isTitle);
            tp.setBullet(!isTitle);
            tp.setSpaceAfter(6.0);
        }
    }

    // ==== PDF ====
    private String generatePdf(Path dir, String title, String ts, List<String> paragraphs) throws IOException {
        String fileName = title + "_" + ts + ".pdf";
        Path path = dir.resolve(fileName);

        try (PDDocument doc = new PDDocument()) {
            org.apache.pdfbox.pdmodel.font.PDFont font = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            org.apache.pdfbox.pdmodel.font.PDFont fontBold = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(fontBold, 20);
                cs.setLeading(28f);
                cs.newLineAtOffset(50, 760);
                cs.showText(title);
                cs.newLine();
                cs.setFont(font, 12);
                cs.setLeading(22f);
                cs.newLine();

                int lineCount = 2;
                for (String para : paragraphs) {
                    String t = para.trim();
                    if (t.isEmpty()) { cs.newLine(); lineCount++; continue; }
                    if (lineCount > 35) {
                        cs.endText();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        try (PDPageContentStream cs2 = new PDPageContentStream(doc, page)) {
                            cs2.beginText();
                            cs2.setFont(font, 12);
                            cs2.setLeading(22f);
                            cs2.newLineAtOffset(50, 760);
                            cs2.showText(t);
                            cs2.newLine();
                            cs2.endText();
                        }
                        lineCount = 0;
                        continue;
                    }
                    cs.showText(t.length() > 80 ? t.substring(0, 80) : t);
                    cs.newLine();
                    lineCount++;
                }
                cs.endText();
            }
            doc.save(path.toFile());
        }
        log.info("PDF: {}", fileName);
        return "/api/files/generated/" + fileName;
    }

    // ==== Markdown ====
    private String generateMd(Path dir, String title, String ts, List<String> paragraphs) throws IOException {
        String fileName = title + "_" + ts + ".md";
        Path path = dir.resolve(fileName);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        sb.append("---\n\n");
        for (String para : paragraphs) sb.append(para).append("\n\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        log.info("MD: {}", fileName);
        return "/api/files/generated/" + fileName;
    }

    // ==== TXT ====
    private String generateTxt(Path dir, String title, String ts, List<String> paragraphs) throws IOException {
        String fileName = title + "_" + ts + ".txt";
        Path path = dir.resolve(fileName);
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n");
        sb.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        for (String para : paragraphs) sb.append(para).append("\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        log.info("TXT: {}", fileName);
        return "/api/files/generated/" + fileName;
    }

    // ====== 文档格式转换 ======

    public String convertDocument(String sourcePath, String sourceFileName, String targetType) throws IOException {
        Path baseDir = fileService.getUploadPath();
        Path sourceFile = baseDir.resolve(sourcePath).normalize();
        if (!sourceFile.startsWith(baseDir)) throw new RuntimeException("非法路径");
        if (!Files.exists(sourceFile)) throw new RuntimeException("源文件不存在");

        String content = readFileContent(sourceFile, sourceFileName);

        String baseName = sourceFileName.contains(".")
                ? sourceFileName.substring(0, sourceFileName.lastIndexOf('.'))
                : sourceFileName;
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_") + "_converted_" + ts;
        List<String> paragraphs = List.of(content.split("\n"));

        return switch (targetType) {
            case "pdf" -> generatePdf(fileService.getUploadPath().resolve("generated"), safeName, "", paragraphs);
            case "docx", "word" -> generateDocx(fileService.getUploadPath().resolve("generated"), safeName, "", paragraphs);
            case "pptx", "ppt" -> generatePptx(fileService.getUploadPath().resolve("generated"), safeName, "", paragraphs);
            case "md", "markdown" -> generateMd(fileService.getUploadPath().resolve("generated"), safeName, "", paragraphs);
            default -> generateTxt(fileService.getUploadPath().resolve("generated"), safeName, "", paragraphs);
        };
    }

    private String readFileContent(Path file, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md"))
            return Files.readString(file, StandardCharsets.UTF_8);
        if (lower.endsWith(".docx")) {
            StringBuilder sb = new StringBuilder();
            try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(file))) {
                for (XWPFParagraph p : doc.getParagraphs()) {
                    String t = p.getText();
                    if (t != null && !t.isBlank()) sb.append(t).append("\n");
                }
            }
            return sb.toString();
        }
        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file.toFile())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                String t = stripper.getText(doc);
                return t != null ? t : "";
            }
        }
        if (lower.endsWith(".pptx")) {
            StringBuilder sb = new StringBuilder();
            try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(file))) {
                int n = 0;
                for (XSLFSlide slide : ppt.getSlides()) {
                    n++; sb.append("--- 第").append(n).append("页 ---\n");
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextBox tb) {
                            for (XSLFTextParagraph p : tb.getTextParagraphs()) {
                                String t = p.getText();
                                if (t != null && !t.isBlank()) sb.append(t).append("\n");
                            }
                        }
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
        throw new RuntimeException("不支持读取此格式: " + fileName);
    }
}
