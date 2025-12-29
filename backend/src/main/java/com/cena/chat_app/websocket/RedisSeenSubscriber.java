package com.cena.chat_app.websocket;

import com.cena.chat_app.dto.response.SeenEventResponse;
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
public class RedisSeenSubscriber implements MessageListener {
    private static final String CHANNEL_PATTERN = "conversation:*:seen";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationMemberRepository conversationMemberRepository;
    private final Counter seenEventsReceived;
    private final Counter subscribeFailures;
    private final Counter subscribeTimeouts;

    public RedisSeenSubscriber(SimpMessagingTemplate messagingTemplate,
                              ObjectMapper objectMapper,
                              ConversationMemberRepository conversationMemberRepository,
                              MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.conversationMemberRepository = conversationMemberRepository;
        this.seenEventsReceived = meterRegistry.counter("chat.realtime.redis.subscribe.success", "type", "seen");
        this.subscribeFailures = meterRegistry.counter("chat.realtime.redis.subscribe.failures", "type", "seen");
        this.subscribeTimeouts = meterRegistry.counter("chat.realtime.redis.subscribe.timeouts", "type", "seen");
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

            SeenEventResponse seenEvent = objectMapper.readValue(payload, SeenEventResponse.class);

            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(seenEvent.getUserId())) {
                    messagingTemplate.convertAndSendToUser(member.getUserId(), "/queue/seen", seenEvent);
                }
            }
            seenEventsReceived.increment();
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                subscribeTimeouts.increment();
                log.error("Redis timeout processing seen event - channel={}", channel);
            } else {
                subscribeFailures.increment();
                log.error("Failed to process Redis seen event - channel={}, error={}", channel, e.getMessage());
            }
        }
    }

    public PatternTopic getChannelPattern() {
        return new PatternTopic(CHANNEL_PATTERN);
    }

    private String extractConversationId(String channel) {
        if (!channel.startsWith("conversation:") || !channel.endsWith(":seen")) {
            return null;
        }
        int start = "conversation:".length();
        int end = channel.length() - ":seen".length();
        return channel.substring(start, end);
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        return (message != null && (message.contains("timeout") || message.contains("timed out") || message.contains("TimeoutException"))) ||
               (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
