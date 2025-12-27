package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.UnreadUpdateResponse;
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

    public RedisUnreadSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
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
        } catch (Exception e) {
            log.error("Failed to process Redis unread update - channel={}, error={}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to process Redis unread update", e);
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
}
