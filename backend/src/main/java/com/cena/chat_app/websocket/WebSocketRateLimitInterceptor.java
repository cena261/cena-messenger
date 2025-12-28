package com.cena.chat_app.websocket;

import com.cena.chat_app.service.RedisRateLimitService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WebSocketRateLimitInterceptor implements ChannelInterceptor {
    private static final int CONNECT_MAX_PER_MINUTE = 10;
    private static final int SEND_MESSAGE_MAX_PER_MINUTE = 60;
    private static final int TYPING_MAX_PER_MINUTE = 30;
    private static final int REACTION_MAX_PER_MINUTE = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisRateLimitService rateLimitService;
    private final Map<String, Counter> rateLimitCounters;

    public WebSocketRateLimitInterceptor(RedisRateLimitService rateLimitService, MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.rateLimitCounters = new HashMap<>();
        this.rateLimitCounters.put("ws_connect", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "websocket", "action", "connect"));
        this.rateLimitCounters.put("ws_send", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "websocket", "action", "send"));
        this.rateLimitCounters.put("ws_typing", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "websocket", "action", "typing"));
        this.rateLimitCounters.put("ws_reaction", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "websocket", "action", "reaction"));
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {
            String userId = getUserId(accessor);

            if (userId != null) {
                RateLimitResult result = checkRateLimit(accessor, userId);

                if (result != null && !result.allowed) {
                    Counter counter = rateLimitCounters.get("ws_" + result.metricAction);
                    if (counter != null) {
                        counter.increment();
                    }

                    log.warn("WebSocket rate limit exceeded - userId={}, action={}, command={}",
                            userId, result.action, accessor.getCommand());

                    return createErrorMessage(accessor, "Rate limit exceeded for " + result.action);
                }
            }
        }

        return message;
    }

    private RateLimitResult checkRateLimit(StompHeaderAccessor accessor, String userId) {
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            boolean allowed = rateLimitService.allowRequest(userId, "ws_connect", CONNECT_MAX_PER_MINUTE, WINDOW);
            return new RateLimitResult("ws_connect", allowed, "connect");
        } else if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null) {
                if (destination.startsWith("/app/chat.sendMessage")) {
                    boolean allowed = rateLimitService.allowRequest(userId, "ws_send_message", SEND_MESSAGE_MAX_PER_MINUTE, WINDOW);
                    return new RateLimitResult("ws_send_message", allowed, "send");
                } else if (destination.startsWith("/app/chat.typing")) {
                    boolean allowed = rateLimitService.allowRequest(userId, "ws_typing", TYPING_MAX_PER_MINUTE, WINDOW);
                    return new RateLimitResult("ws_typing", allowed, "typing");
                } else if (destination.startsWith("/app/chat.reaction")) {
                    boolean allowed = rateLimitService.allowRequest(userId, "ws_reaction", REACTION_MAX_PER_MINUTE, WINDOW);
                    return new RateLimitResult("ws_reaction", allowed, "reaction");
                }
            }
        }

        return null;
    }

    private String getUserId(StompHeaderAccessor accessor) {
        Authentication auth = (Authentication) accessor.getUser();
        if (auth != null && auth.getPrincipal() != null) {
            return (String) auth.getPrincipal();
        }

        if (accessor.getSessionAttributes() != null) {
            Object userId = accessor.getSessionAttributes().get("userId");
            if (userId != null) {
                return (String) userId;
            }
        }

        return null;
    }

    private Message<?> createErrorMessage(StompHeaderAccessor originalAccessor, String errorMessage) {
        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setSessionId(originalAccessor.getSessionId());
        errorAccessor.setMessage(errorMessage);

        byte[] payload = errorMessage.getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private static class RateLimitResult {
        String action;
        boolean allowed;
        String metricAction;

        RateLimitResult(String action, boolean allowed, String metricAction) {
            this.action = action;
            this.allowed = allowed;
            this.metricAction = metricAction;
        }
    }
}
