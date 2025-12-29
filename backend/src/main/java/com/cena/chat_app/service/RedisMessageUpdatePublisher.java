package com.cena.chat_app.service;

import com.cena.chat_app.dto.response.MessageUpdateEventResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisMessageUpdatePublisher {
    private static final String CHANNEL_PREFIX = "conversation:";
    private static final String CHANNEL_SUFFIX = ":message-updates";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter messageUpdatesPublished;
    private final Counter publishFailures;
    private final Counter publishTimeouts;

    public RedisMessageUpdatePublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messageUpdatesPublished = meterRegistry.counter("chat.realtime.message.updates.published");
        this.publishFailures = meterRegistry.counter("chat.realtime.redis.publish.failures", "type", "message-update");
        this.publishTimeouts = meterRegistry.counter("chat.realtime.redis.publish.timeouts", "type", "message-update");
    }

    public void publishMessageUpdate(String conversationId, MessageUpdateEventResponse event) {
        String channel = CHANNEL_PREFIX + conversationId + CHANNEL_SUFFIX;
        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, payload);
            messageUpdatesPublished.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                publishTimeouts.increment();
                log.error("Redis timeout publishing message update - conversationId={}, channel={}", conversationId, channel);
            } else {
                publishFailures.increment();
                log.error("Failed to publish message update to Redis - conversationId={}, channel={}, error={}",
                        conversationId, channel, e.getMessage());
            }
        }
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
