package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.MessageUpdateEventResponse;
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
public class RedisMessageUpdateSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:message-updates";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;
    private final BlockingService blockingService;

    public RedisMessageUpdateSubscriber(SimpMessagingTemplate messagingTemplate,
                                        ObjectMapper objectMapper,
                                        ConversationMemberRepository conversationMemberRepository,
                                        ConversationRepository conversationRepository,
                                        BlockingService blockingService) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationRepository = conversationRepository;
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

            MessageUpdateEventResponse updateEvent = objectMapper.readValue(payload, MessageUpdateEventResponse.class);

            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember member : members) {
                String senderId = extractSenderIdFromEvent(updateEvent);
                if (senderId != null && !member.getUserId().equals(senderId)) {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/message-updates", updateEvent);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Redis message update event - channel={}, error={}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to process Redis message update event", e);
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractConversationId(String channel) {
        if (!channel.startsWith("conversation:") || !channel.endsWith(":message-updates")) {
            return null;
        }
        int start = "conversation:".length();
        int end = channel.length() - ":message-updates".length();
        return channel.substring(start, end);
    }

    private String extractSenderIdFromEvent(MessageUpdateEventResponse event) {
        return event.getSenderId();
    }
}
