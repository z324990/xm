package com.chat.controller;

import com.chat.dto.ApiResult;
import com.chat.dto.FileResponse;
import com.chat.service.DocumentGeneratorService;
import com.chat.service.FileService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final DocumentGeneratorService documentGenerator;

    public FileController(FileService fileService, DocumentGeneratorService documentGenerator) {
        this.fileService = fileService;
        this.documentGenerator = documentGenerator;
    }

    // ====== 文件上传 ======

    @PostMapping("/upload")
    public ApiResult<?> uploadFile(@RequestParam("file") MultipartFile file, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");

        try {
            FileResponse result = fileService.uploadFile(file);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("上传失败", e);
            return ApiResult.error(500, "上传失败: " + e.getMessage());
        }
    }

    // ====== 文件下载/预览 ======

    @GetMapping("/{datePath}/{fileName}")
    public ResponseEntity<Resource> getFile(@PathVariable String datePath,
                                             @PathVariable String fileName) {
        try {
            Resource resource = fileService.loadFile(datePath, fileName);
            String contentType = determineContentType(fileName);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ====== 图片分析（OCR + AI 描述） ======

    @PostMapping("/analyze-image")
    public ApiResult<?> analyzeImage(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");

        String filePath = body.get("filePath");
        String fileName = body.get("fileName");

        if (filePath == null || filePath.isBlank()) {
            return ApiResult.error(400, "缺少文件路径");
        }

        try {
            // 从文件路径提取日期和文件名
            // 格式: /api/files/20250624/uuid.jpg
            String cleanPath = filePath.replace("/api/files/", "");
            String[] parts = cleanPath.split("/");
            if (parts.length != 2) {
                return ApiResult.error(400, "无效文件路径");
            }

            Resource resource = fileService.loadFile(parts[0], parts[1]);
            if (!resource.exists()) {
                return ApiResult.error(404, "文件未找到");
            }

            // 读取图片信息
            long fileSize = resource.contentLength();
            String mimeType = determineContentType(fileName != null ? fileName : parts[1]);
            boolean isImage = mimeType.startsWith("image/");

            if (!isImage) {
                // 非图片文件，读取文本内容
                String text = readTextContent(resource);
                if (!text.isEmpty()) {
                    return ApiResult.success(Map.of(
                            "type", "text",
                            "content", text,
                            "fileName", fileName != null ? fileName : parts[1]
                    ));
                }
                return ApiResult.success(Map.of(
                        "type", "file",
                        "content", "已收到文件 (" + formatFileSize(fileSize) + ")",
                        "fileName", fileName != null ? fileName : parts[1]
                ));
            }

            // 图片文件，返回信息给 AI 处理
            String ext = fileName != null ? fileName.toLowerCase() : parts[1].toLowerCase();
            String imgInfo = "图片文件: " + (fileName != null ? fileName : parts[1])
                    + "\n大小: " + formatFileSize(fileSize)
                    + "\n路径: " + filePath
                    + "\n类型: " + (ext.endsWith(".png") ? "PNG图片" :
                      ext.endsWith(".gif") ? "GIF动图" :
                      ext.endsWith(".webp") ? "WebP图片" :
                      ext.endsWith(".bmp") ? "BMP位图" : "JPEG图片");

            return ApiResult.success(Map.of(
                    "type", "image",
                    "content", imgInfo,
                    "fileUrl", filePath,
                    "fileName", fileName != null ? fileName : parts[1]
            ));
        } catch (Exception e) {
            log.error("分析文件失败", e);
            return ApiResult.error(500, "分析失败: " + e.getMessage());
        }
    }

    // ====== 文档生成 ======

    @PostMapping("/generate-doc")
    public ApiResult<?> generateDocument(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");

        String type = body.getOrDefault("type", "txt");
        String title = body.getOrDefault("title", "文档");
        String content = body.getOrDefault("content", "");

        if (content.isBlank()) {
            return ApiResult.error(400, "内容不能为空");
        }

        try {
            String url = documentGenerator.generateDocument(type, title, content);
            String fileName = url.substring(url.lastIndexOf('/') + 1);

            return ApiResult.success(Map.of(
                    "fileUrl", url,
                    "fileName", fileName,
                    "type", type,
                    "downloadUrl", url
            ));
        } catch (Exception e) {
            log.error("文档生成失败", e);
            return ApiResult.error(500, "生成失败: " + e.getMessage());
        }
    }

    // ====== 文档格式转换 ======

    @PostMapping("/convert")
    public ApiResult<?> convertDocument(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");

        String filePath = body.get("filePath");
        String fileName = body.get("fileName");
        String targetType = body.getOrDefault("targetType", "txt");

        if (filePath == null || fileName == null) {
            return ApiResult.error(400, "缺少文件路径或文件名");
        }

        try {
            String relativePath = filePath.replace("/api/files/", "");
            String url = documentGenerator.convertDocument(relativePath, fileName, targetType);
            String newFileName = url.substring(url.lastIndexOf('/') + 1);

            return ApiResult.success(Map.of(
                    "fileUrl", url,
                    "fileName", newFileName,
                    "type", targetType,
                    "message", "转换成功"
            ));
        } catch (Exception e) {
            log.error("文档转换失败", e);
            return ApiResult.error(500, "转换失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private String determineContentType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".md")) return "text/markdown;charset=UTF-8";
        if (name.endsWith(".txt")) return "text/plain;charset=UTF-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String readTextContent(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            int maxLines = 100;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxLines) {
                content.append(line).append("\n");
                count++;
            }
            return content.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
