package com.cena.chat_app.service;

import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisUnreadPublisher {
    private static final String CHANNEL_PREFIX = "unread:user:";
    private static final String CHANNEL_SUFFIX = ":updates";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter unreadUpdatesPublished;
    private final Counter publishFailures;

    public RedisUnreadPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.unreadUpdatesPublished = meterRegistry.counter("chat.realtime.unread.updates.published");
        this.publishFailures = meterRegistry.counter("chat.realtime.redis.publish.failures", "type", "unread");
    }

    public void publishUnreadUpdate(String userId, UnreadUpdateResponse update) {
        String channel = CHANNEL_PREFIX + userId + CHANNEL_SUFFIX;
        log.info("Publishing unread update - userId={}, channel={}, conversationId={}, unreadCount={}",
                userId, channel, update.getConversationId(), update.getUnreadCount());
        try {
            String payload = objectMapper.writeValueAsString(update);
            redisTemplate.convertAndSend(channel, payload);
            unreadUpdatesPublished.increment();
            log.info("Unread update published successfully - userId={}", userId);
        } catch (JacksonException e) {
            publishFailures.increment();
            log.error("Failed to publish unread update to Redis - userId={}, channel={}, error={}",
                    userId, channel, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize unread update", e);
        }
    }
}
