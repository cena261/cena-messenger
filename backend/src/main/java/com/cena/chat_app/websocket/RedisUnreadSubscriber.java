package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisUnreadSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "unread:user:*:updates";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final Counter unreadUpdatesReceived;
    private final Counter subscribeFailures;
    private final Counter subscribeTimeouts;

    public RedisUnreadSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.unreadUpdatesReceived = meterRegistry.counter("chat.realtime.redis.subscribe.success", "type", "unread");
        this.subscribeFailures = meterRegistry.counter("chat.realtime.redis.subscribe.failures", "type", "unread");
        this.subscribeTimeouts = meterRegistry.counter("chat.realtime.redis.subscribe.timeouts", "type", "unread");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = null;
        try {
            channel = new String(message.getChannel());
            String payload = new String(message.getBody());

            String userId = extractUserId(channel);
            if (userId == null) {
                return;
            }

            UnreadUpdateResponse unreadUpdate = objectMapper.readValue(payload, UnreadUpdateResponse.class);
            messagingTemplate.convertAndSendToUser(userId, "/queue/unread", unreadUpdate);
            unreadUpdatesReceived.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                subscribeTimeouts.increment();
                log.error("Redis timeout processing unread update - channel={}", channel);
            } else {
                subscribeFailures.increment();
                log.error("Failed to process Redis unread update - channel={}, error={}", channel, e.getMessage());
            }
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractUserId(String channel) {
        if (!channel.startsWith("unread:user:") || !channel.endsWith(":updates")) {
            return null;
        }
        int start = "unread:user:".length();
        int end = channel.length() - ":updates".length();
        return channel.substring(start, end);
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
