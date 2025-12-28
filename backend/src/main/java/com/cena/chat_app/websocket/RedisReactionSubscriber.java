package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.ReactionEventResponse;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.repository.ConversationMemberRepository;
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
public class RedisReactionSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:reactions";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationMemberRepository conversationMemberRepository;

    public RedisReactionSubscriber(SimpMessagingTemplate messagingTemplate,
                                   ObjectMapper objectMapper,
                                   ConversationMemberRepository conversationMemberRepository) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
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

            ReactionEventResponse reactionEvent = objectMapper.readValue(payload, ReactionEventResponse.class);

            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(reactionEvent.getUserId())) {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/reactions", reactionEvent);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Redis reaction event - channel={}, error={}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to process Redis reaction event", e);
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractConversationId(String channel) {
        if (!channel.startsWith("conversation:") || !channel.endsWith(":reactions")) {
            return null;
        }
        int start = "conversation:".length();
        int end = channel.length() - ":reactions".length();
        return channel.substring(start, end);
    }
}
