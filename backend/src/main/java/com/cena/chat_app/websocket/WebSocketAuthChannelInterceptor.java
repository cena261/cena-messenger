package com.cena.chat_app.websocket;

import com.cena.chat_app.repository.ConversationMemberRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
                if (accessor.getSessionAttributes() != null) {
                    String userId = (String) accessor.getSessionAttributes().get("userId");
                    if (userId != null) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId, null, Collections.emptyList());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        accessor.setUser(auth);
                    }
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                Authentication auth = (Authentication) accessor.getUser();

                if (destination != null && destination.startsWith("/topic/conversation.") && auth != null) {
                    String conversationId = destination.substring("/topic/conversation.".length());
                    String userId = (String) auth.getPrincipal();

                    boolean isMember = conversationMemberRepository
                            .findByConversationIdAndUserId(conversationId, userId)
                            .isPresent();
                    if (!isMember) {
                        throw new AccessDeniedException("User not member of conversation");
                    }
                }
            }
        }

        return message;
    }
}
