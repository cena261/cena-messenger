package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.ReactionEventResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.service.BlockingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final ConversationRepository conversationRepository;
    private final BlockingService blockingService;
    private final Counter reactionEventsReceived;
    private final Counter subscribeFailures;
    private final Counter subscribeTimeouts;

    public RedisReactionSubscriber(SimpMessagingTemplate messagingTemplate,
                                   ObjectMapper objectMapper,
                                   ConversationMemberRepository conversationMemberRepository,
                                   ConversationRepository conversationRepository,
                                   BlockingService blockingService,
                                   MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationRepository = conversationRepository;
        this.blockingService = blockingService;
        this.reactionEventsReceived = meterRegistry.counter("chat.realtime.redis.subscribe.success", "type", "reaction");
        this.subscribeFailures = meterRegistry.counter("chat.realtime.redis.subscribe.failures", "type", "reaction");
        this.subscribeTimeouts = meterRegistry.counter("chat.realtime.redis.subscribe.timeouts", "type", "reaction");
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
            reactionEventsReceived.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                subscribeTimeouts.increment();
                log.error("Redis timeout processing reaction event - channel={}", channel);
            } else {
                subscribeFailures.increment();
                log.error("Failed to process Redis reaction event - channel={}, error={}", channel, e.getMessage());
            }
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

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
