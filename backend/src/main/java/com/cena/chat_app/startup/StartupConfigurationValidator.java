package com.cena.chat_app.startup;

import com.cena.chat_app.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupConfigurationValidator implements ApplicationRunner {
    private final Environment environment;
    private final FeatureFlags featureFlags;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting configuration validation...");

        validateCriticalDependencies();
        validateOptionalDependencies();

        log.info("Configuration validation complete");
        log.info("Feature flags: media={}, email={}", featureFlags.isMediaEnabled(), featureFlags.isEmailEnabled());
    }

    private void validateCriticalDependencies() {
        validateMongoDBConfiguration();
        validateRedisConfiguration();
    }

    private void validateMongoDBConfiguration() {
        String mongoUri = environment.getProperty("spring.data.mongodb.uri");
        String mongoDatabase = environment.getProperty("spring.data.mongodb.database");

        if (isBlank(mongoUri) && isBlank(mongoDatabase)) {
            throw new IllegalStateException(
                "MongoDB configuration is missing. " +
                "Application requires either 'spring.data.mongodb.uri' or 'spring.data.mongodb.database' to be configured. " +
                "MongoDB is a critical dependency and the application cannot start without it."
            );
        }

        log.info("MongoDB configuration validated");
    }

    private void validateRedisConfiguration() {
        String redisHost = environment.getProperty("spring.data.redis.host");
        String redisPort = environment.getProperty("spring.data.redis.port");

        if (isBlank(redisHost)) {
            throw new IllegalStateException(
                "Redis configuration is missing. " +
                "Application requires 'spring.data.redis.host' to be configured. " +
                "Redis is a critical dependency and the application cannot start without it."
            );
        }

        if (isBlank(redisPort)) {
            throw new IllegalStateException(
                "Redis configuration is missing. " +
                "Application requires 'spring.data.redis.port' to be configured. " +
                "Redis is a critical dependency and the application cannot start without it."
            );
        }

        log.info("Redis configuration validated");
    }

    private void validateOptionalDependencies() {
        validateMinIOConfiguration();
        validateSMTPConfiguration();
    }

    private void validateMinIOConfiguration() {
        String endpoint = environment.getProperty("minio.endpoint");
        String accessKey = environment.getProperty("minio.access-key");
        String secretKey = environment.getProperty("minio.secret-key");
        String bucketName = environment.getProperty("minio.bucket-name");

        if (isBlank(endpoint) || isBlank(accessKey) || isBlank(secretKey) || isBlank(bucketName)) {
            log.warn("MinIO configuration is incomplete. Media upload features will be disabled.");
            log.warn("To enable media uploads, configure: minio.endpoint, minio.access-key, minio.secret-key, minio.bucket-name");
            featureFlags.setMediaEnabled(false);
            return;
        }

        featureFlags.setMediaEnabled(true);
        log.info("MinIO configuration validated - media features enabled");
    }

    private void validateSMTPConfiguration() {
        String host = environment.getProperty("spring.mail.host");
        String port = environment.getProperty("spring.mail.port");
        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");

        if (isBlank(host) || isBlank(port)) {
            log.warn("SMTP configuration is incomplete. Email features will be disabled.");
            log.warn("To enable email features, configure: spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password");
            featureFlags.setEmailEnabled(false);
            return;
        }

        featureFlags.setEmailEnabled(true);
        log.info("SMTP configuration validated - email features enabled");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
