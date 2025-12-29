package com.cena.chat_app.health;

import com.cena.chat_app.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioHealthIndicator implements HealthIndicator {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public Health health() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build()
            );

            if (bucketExists) {
                return Health.up()
                    .withDetail("bucket", minioProperties.getBucketName())
                    .withDetail("endpoint", minioProperties.getEndpoint())
                    .withDetail("status", "available")
                    .build();
            }

            return Health.down()
                .withDetail("bucket", minioProperties.getBucketName())
                .withDetail("endpoint", minioProperties.getEndpoint())
                .withDetail("status", "bucket not found")
                .build();
        } catch (Exception e) {
            log.warn("MinIO health check failed (optional dependency): {}", e.getMessage());
            return Health.down()
                .withDetail("endpoint", minioProperties.getEndpoint())
                .withDetail("status", "unavailable")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
