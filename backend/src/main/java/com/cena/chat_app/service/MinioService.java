package com.cena.chat_app.service;

import com.cena.chat_app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .build()
                );
                log.info("Created MinIO bucket: {}", minioProperties.getBucketName());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize MinIO bucket (MinIO may not be available): {}", e.getMessage());
        }
    }

    public String generatePresignedUploadUrl(String fileName, String contentType) {
        try {
            String objectName = generateObjectName(fileName);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(minioProperties.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL - fileName={}, error={}", fileName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    public String getObjectUrl(String objectKey) {
        return String.format("%s/%s/%s", minioProperties.getEndpoint(), minioProperties.getBucketName(), objectKey);
    }

    public String extractObjectKeyFromUrl(String presignedUrl) {
        String baseUrl = String.format("%s/%s/", minioProperties.getEndpoint(), minioProperties.getBucketName());
        if (presignedUrl.startsWith(baseUrl)) {
            String urlWithParams = presignedUrl.substring(baseUrl.length());
            int queryIndex = urlWithParams.indexOf('?');
            if (queryIndex > 0) {
                return urlWithParams.substring(0, queryIndex);
            }
            return urlWithParams;
        }
        throw new IllegalArgumentException("Invalid presigned URL format");
    }

    private String generateObjectName(String originalFileName) {
        String extension = "";
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFileName.substring(lastDotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }
}
