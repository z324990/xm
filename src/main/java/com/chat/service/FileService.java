package com.chat.service;

import com.chat.dto.FileResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("上传目录: {}", uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录", e);
        }
    }

    /**
     * 上传文件
     */
    public FileResponse uploadFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "unnamed";
        }

        // 生成唯一文件名
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) ext = originalName.substring(dot);

        String uniqueName = UUID.randomUUID().toString() + ext;
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            Path targetDir = uploadPath.resolve(datePath);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(uniqueName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/files/" + datePath + "/" + uniqueName;

            log.info("文件上传成功: {} -> {}", originalName, fileUrl);
            return new FileResponse(originalName, fileUrl, file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件资源
     */
    public Resource loadFile(String datePath, String fileName) {
        try {
            Path filePath = uploadPath.resolve(datePath).resolve(fileName).normalize();
            if (!filePath.startsWith(uploadPath)) {
                throw new RuntimeException("非法路径");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("文件不存在: " + fileName);
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    /**
     * 获取文件保存目录
     */
    public Path getUploadPath() {
        return uploadPath;
    }
}
