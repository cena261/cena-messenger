package com.cena.chat_app.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoHealthIndicator implements HealthIndicator {
    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            if (result.getDouble("ok") == 1.0) {
                return Health.up()
                    .withDetail("database", mongoTemplate.getDb().getName())
                    .withDetail("status", "reachable")
                    .build();
            }
            return Health.down()
                .withDetail("database", mongoTemplate.getDb().getName())
                .withDetail("status", "unreachable")
                .build();
        } catch (Exception e) {
            log.error("MongoDB health check failed", e);
            return Health.down()
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
