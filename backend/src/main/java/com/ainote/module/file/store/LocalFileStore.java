package com.ainote.module.file.store;

import cn.hutool.core.io.FileUtil;
import com.ainote.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储（file.store=local，默认）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file.store", havingValue = "local", matchIfMissing = true)
public class LocalFileStore implements FileStore {

    @Value("${file.upload-dir:./upload}")
    private String uploadDir;

    private Path root() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, InputStream stream, long size, String contentType) {
        Path file = root().resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("本地文件保存失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        File file = root().resolve(key).toFile();
        if (file.exists()) {
            FileUtil.del(file);
        }
    }

    @Override
    public InputStream read(String key) {
        File file = root().resolve(key).toFile();
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try {
            return Files.newInputStream(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String contentType(String key) {
        String ext = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1).toLowerCase() : "";
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> null;
        };
    }
}