package com.cena.chat_app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allowRequest(String identifier, String action, int maxRequests, Duration window) {
        String key = buildKey(identifier, action);

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                log.warn("Failed to increment rate limit counter for key: {}", key);
                return true;
            }

            if (currentCount == 1) {
                redisTemplate.expire(key, window.getSeconds(), TimeUnit.SECONDS);
            }

            boolean allowed = currentCount <= maxRequests;

            if (!allowed) {
                log.warn("Rate limit exceeded - key={}, count={}, max={}", key, currentCount, maxRequests);
            }

            return allowed;
        } catch (Exception e) {
            log.error("Error checking rate limit for key={}: {}", key, e.getMessage(), e);
            return true;
        }
    }

    public void resetLimit(String identifier, String action) {
        String key = buildKey(identifier, action);
        redisTemplate.delete(key);
    }

    public Long getCurrentCount(String identifier, String action) {
        String key = buildKey(identifier, action);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    private String buildKey(String identifier, String action) {
        return RATE_LIMIT_PREFIX + action + ":" + identifier;
    }
}
