package com.cena.chat_app.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisUnreadService {
    private static final String UNREAD_PREFIX = "unread:user:";
    private static final String CONVERSATION_INFIX = ":conversation:";

    private final StringRedisTemplate redisTemplate;

    public RedisUnreadService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementUnreadCount(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        redisTemplate.opsForValue().increment(key);
    }

    public long getUnreadCount(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public void resetUnreadCount(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        redisTemplate.opsForValue().set(key, "0");
    }

    private String buildKey(String userId, String conversationId) {
        return UNREAD_PREFIX + userId + CONVERSATION_INFIX + conversationId;
    }
}
