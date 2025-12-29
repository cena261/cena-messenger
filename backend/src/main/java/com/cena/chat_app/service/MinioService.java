package com.cena.chat_app.service;

import com.cena.chat_app.config.MinioProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter minioSuccessCounter;
    private final Counter minioFailureCounter;
    private final Counter minioTimeoutCounter;

    public MinioService(MinioClient minioClient, MinioProperties minioProperties, MeterRegistry meterRegistry) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.minioSuccessCounter = meterRegistry.counter("minio.operation.success");
        this.minioFailureCounter = meterRegistry.counter("minio.operation.failure");
        this.minioTimeoutCounter = meterRegistry.counter("minio.operation.timeout");
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
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(minioProperties.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                            .build()
            );
            minioSuccessCounter.increment();
            return url;
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                minioTimeoutCounter.increment();
                log.error("MinIO timeout generating presigned URL - fileName={}", fileName);
            } else {
                minioFailureCounter.increment();
                log.error("MinIO failure generating presigned URL - fileName={}, error={}", fileName, e.getMessage());
            }
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out"))) ||
               (cause != null && cause.getMessage() != null &&
                (cause.getMessage().contains("timeout") || cause.getMessage().contains("timed out")));
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
