package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.TypingEventResponse;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.repository.ConversationMemberRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisTypingSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:typing";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationMemberRepository conversationMemberRepository;

    public RedisTypingSubscriber(SimpMessagingTemplate messagingTemplate,
                                ObjectMapper objectMapper,
                                ConversationMemberRepository conversationMemberRepository) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
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

            TypingEventResponse typingEvent = objectMapper.readValue(payload, TypingEventResponse.class);

            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(typingEvent.getUserId())) {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/typing", typingEvent);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process Redis typing event", e);
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractConversationId(String channel) {
        if (!channel.startsWith("conversation:") || !channel.endsWith(":typing")) {
            return null;
        }
        int start = "conversation:".length();
        int end = channel.length() - ":typing".length();
        return channel.substring(start, end);
    }
}
