package com.cena.chat_app.service;

import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisUnreadPublisher {
    private static final String CHANNEL_PREFIX = "unread:user:";
    private static final String CHANNEL_SUFFIX = ":updates";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUnreadPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishUnreadUpdate(String userId, UnreadUpdateResponse update) {
        String channel = CHANNEL_PREFIX + userId + CHANNEL_SUFFIX;
        try {
            String payload = objectMapper.writeValueAsString(update);
            redisTemplate.convertAndSend(channel, payload);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize unread update", e);
        }
    }
}
