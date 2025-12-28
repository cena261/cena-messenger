package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.service.BlockingService;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RedisMessageSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:messages";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final BlockingService blockingService;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate,
                                   ObjectMapper objectMapper,
                                   ConversationRepository conversationRepository,
                                   ConversationMemberRepository conversationMemberRepository,
                                   BlockingService blockingService) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.blockingService = blockingService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = null;
        try {
            channel = new String(message.getChannel());
            String payload = new String(message.getBody());

            String conversationId = extractConversationId(channel);
            if (conversationId == null) {
                return;
            }

            MessageResponse messageResponse = objectMapper.readValue(payload, MessageResponse.class);
            messagingTemplate.convertAndSend("/topic/conversation." + conversationId, messageResponse);
        } catch (Exception e) {
            log.error("Failed to process Redis message - channel={}, error={}", channel, e.getMessage(), e);
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
