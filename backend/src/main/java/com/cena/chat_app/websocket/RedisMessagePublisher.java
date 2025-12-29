package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.MessageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisMessagePublisher {
    private static final String CHANNEL_PREFIX = "conversation:";
    private static final String CHANNEL_SUFFIX = ":messages";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter messagesPublished;
    private final Counter publishFailures;
    private final Counter publishTimeouts;

    public RedisMessagePublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagesPublished = meterRegistry.counter("chat.realtime.messages.published");
        this.publishFailures = meterRegistry.counter("chat.realtime.redis.publish.failures", "type", "message");
        this.publishTimeouts = meterRegistry.counter("chat.realtime.redis.publish.timeouts", "type", "message");
    }

    public void publishMessage(String conversationId, MessageResponse message) {
        String channel = CHANNEL_PREFIX + conversationId + CHANNEL_SUFFIX;
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, payload);
            messagesPublished.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                publishTimeouts.increment();
                log.error("Redis timeout publishing message - conversationId={}, channel={}", conversationId, channel);
            } else {
                publishFailures.increment();
                log.error("Failed to publish message to Redis - conversationId={}, channel={}, error={}",
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
