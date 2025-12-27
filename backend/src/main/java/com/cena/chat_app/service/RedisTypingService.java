package com.cena.chat_app.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisTypingService {
    private static final String TYPING_PREFIX = "typing:";
    private static final Duration TYPING_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;

    public RedisTypingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addTypingUser(String conversationId, String userId) {
        String key = buildKey(conversationId);
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, TYPING_TTL);
    }

    public void removeTypingUser(String conversationId, String userId) {
        String key = buildKey(conversationId);
        redisTemplate.opsForSet().remove(key, userId);
    }

    private String buildKey(String conversationId) {
        return TYPING_PREFIX + conversationId;
    }
}
