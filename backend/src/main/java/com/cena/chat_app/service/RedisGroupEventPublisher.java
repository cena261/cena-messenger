package com.cena.chat_app.service;

import com.cena.chat_app.dto.response.GroupEventResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisGroupEventPublisher {
    private static final String CHANNEL_PREFIX = "conversation:";
    private static final String CHANNEL_SUFFIX = ":group-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter groupEventsPublished;
    private final Counter publishFailures;
    private final Counter publishTimeouts;

    public RedisGroupEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.groupEventsPublished = meterRegistry.counter("chat.realtime.group.events.published");
        this.publishFailures = meterRegistry.counter("chat.realtime.redis.publish.failures", "type", "group-event");
        this.publishTimeouts = meterRegistry.counter("chat.realtime.redis.publish.timeouts", "type", "group-event");
    }

    public void publishGroupEvent(String conversationId, GroupEventResponse event) {
        String channel = CHANNEL_PREFIX + conversationId + CHANNEL_SUFFIX;
        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, payload);
            groupEventsPublished.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                publishTimeouts.increment();
                log.error("Redis timeout publishing group event - conversationId={}, channel={}", conversationId, channel);
            } else {
                publishFailures.increment();
                log.error("Failed to publish group event to Redis - conversationId={}, channel={}, error={}",
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
