package com.cena.chat_app.websocket;

import com.cena.chat_app.repository.ConversationMemberRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final ConversationMemberRepository conversationMemberRepository;

    public WebSocketAuthChannelInterceptor(ConversationMemberRepository conversationMemberRepository) {
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String userId = (String) accessor.getSessionAttributes().get("userId");
                if (userId != null) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    accessor.setUser(authentication);
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String userId = (String) accessor.getSessionAttributes().get("userId");
                String destination = accessor.getDestination();

                if (userId != null && destination != null && destination.startsWith("/topic/conversation.")) {
                    String conversationId = extractConversationId(destination);
                    if (conversationId != null) {
                        boolean isMember = conversationMemberRepository
                            .findByConversationIdAndUserId(conversationId, userId)
                            .isPresent();
                        if (!isMember) {
                            throw new SecurityException("User not authorized to subscribe to this conversation");
                        }
                    }
                }
            }
        }

        return message;
    }

    private String extractConversationId(String destination) {
        if (destination.startsWith("/topic/conversation.")) {
            return destination.substring("/topic/conversation.".length());
        }
        return null;
    }
}
