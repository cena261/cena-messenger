package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.MessageResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessageSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:messages";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String payload = new String(message.getBody());

            String conversationId = extractConversationId(channel);
            if (conversationId == null) {
                return;
            }

            MessageResponse messageResponse = objectMapper.readValue(payload, MessageResponse.class);
            messagingTemplate.convertAndSend("/topic/conversation." + conversationId, messageResponse);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process Redis message", e);
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractConversationId(String channel) {
        if (!channel.startsWith("conversation:") || !channel.endsWith(":messages")) {
            return null;
        }
        int start = "conversation:".length();
        int end = channel.length() - ":messages".length();
        return channel.substring(start, end);
    }
}
