package com.cena.chat_app.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter redisRateLimitSuccessCounter;
    private final Counter redisRateLimitFailureCounter;
    private final Counter redisRateLimitTimeoutCounter;

    public RedisRateLimitService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.redisRateLimitSuccessCounter = meterRegistry.counter("redis.ratelimit.success");
        this.redisRateLimitFailureCounter = meterRegistry.counter("redis.ratelimit.failure");
        this.redisRateLimitTimeoutCounter = meterRegistry.counter("redis.ratelimit.timeout");
    }

    public boolean allowRequest(String identifier, String action, int maxRequests, Duration window) {
        String key = buildKey(identifier, action);

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                log.warn("Failed to increment rate limit counter for key: {}", key);
                redisRateLimitFailureCounter.increment();
                return true;
            }

            if (currentCount == 1) {
                redisTemplate.expire(key, window.getSeconds(), TimeUnit.SECONDS);
            }

            boolean allowed = currentCount <= maxRequests;

            if (!allowed) {
                log.warn("Rate limit exceeded - key={}, count={}, max={}", key, currentCount, maxRequests);
            }

            redisRateLimitSuccessCounter.increment();
            return allowed;
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                redisRateLimitTimeoutCounter.increment();
                log.error("Redis timeout checking rate limit for key={}", key);
            } else {
                redisRateLimitFailureCounter.increment();
                log.error("Redis error checking rate limit for key={}: {}", key, e.getMessage());
            }
            return true;
        }
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
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
