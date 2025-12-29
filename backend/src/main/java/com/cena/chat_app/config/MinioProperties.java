package com.cena.chat_app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private int presignedUrlExpiry;
    private long connectTimeout = 5000;
    private long writeTimeout = 10000;
    private long readTimeout = 10000;
}
