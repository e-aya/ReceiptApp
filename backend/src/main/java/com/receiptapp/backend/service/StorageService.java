package com.receiptapp.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    public String save(MultipartFile file) throws IOException {
        // アップロードフォルダがなければ作成
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // ユニークなファイル名を生成
        String extension = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(fileName);

        // 保存
        Files.copy(file.getInputStream(), filePath,
                StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}