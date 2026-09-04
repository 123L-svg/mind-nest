package com.ainote.module.file.store;

import com.ainote.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 对象存储（file.store=minio）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.store", havingValue = "minio")
public class MinioFileStore implements FileStore {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public void save(String key, InputStream stream, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("MinIO 上传失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName).object(key).build());
        } catch (Exception e) {
            log.warn("MinIO 删除对象失败，{}: {}", key, e.getMessage());
        }
    }

    /** 读取对象（供访问代理使用） */
    public InputStream get(String key) {
        return read(key);
    }

    /** 获取对象元信息（供访问代理设置 content-type） */
    public StatObjectResponse stat(String key) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName).object(key).build());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public InputStream read(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName).object(key).build());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String contentType(String key) {
        StatObjectResponse stat = stat(key);
        return stat == null ? null : stat.contentType();
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("已创建 MinIO bucket：{}", bucketName);
        }
    }
}