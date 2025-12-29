package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.TypingEventResponse;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.repository.ConversationMemberRepository;
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
public class RedisTypingSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:typing";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationMemberRepository conversationMemberRepository;
    private final Counter typingEventsReceived;
    private final Counter subscribeFailures;
    private final Counter subscribeTimeouts;

    public RedisTypingSubscriber(SimpMessagingTemplate messagingTemplate,
                                ObjectMapper objectMapper,
                                ConversationMemberRepository conversationMemberRepository,
                                MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
        this.typingEventsReceived = meterRegistry.counter("chat.realtime.redis.subscribe.success", "type", "typing");
        this.subscribeFailures = meterRegistry.counter("chat.realtime.redis.subscribe.failures", "type", "typing");
        this.subscribeTimeouts = meterRegistry.counter("chat.realtime.redis.subscribe.timeouts", "type", "typing");
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

            TypingEventResponse typingEvent = objectMapper.readValue(payload, TypingEventResponse.class);

            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(typingEvent.getUserId())) {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/typing", typingEvent);
                }
            }
            typingEventsReceived.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                subscribeTimeouts.increment();
                log.error("Redis timeout processing typing event - channel={}", channel);
            } else {
                subscribeFailures.increment();
                log.error("Failed to process Redis typing event - channel={}, error={}", channel, e.getMessage());
            }
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

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
