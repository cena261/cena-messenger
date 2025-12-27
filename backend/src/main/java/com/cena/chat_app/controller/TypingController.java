package com.cena.chat_app.controller;

import com.cena.chat_app.dto.request.TypingRequest;
import com.cena.chat_app.dto.response.TypingEventResponse;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.service.RedisTypingPublisher;
import com.cena.chat_app.service.RedisTypingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class TypingController {
    private final RedisTypingService redisTypingService;
    private final RedisTypingPublisher redisTypingPublisher;
    private final ConversationMemberRepository conversationMemberRepository;

    public TypingController(RedisTypingService redisTypingService,
                           RedisTypingPublisher redisTypingPublisher,
                           ConversationMemberRepository conversationMemberRepository) {
        this.redisTypingService = redisTypingService;
        this.redisTypingPublisher = redisTypingPublisher;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @MessageMapping("/typing/start")
    public void handleTypingStart(@Payload TypingRequest request, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        ConversationMember membership = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        redisTypingService.addTypingUser(request.getConversationId(), userId);

        TypingEventResponse typingEvent = TypingEventResponse.builder()
                .conversationId(request.getConversationId())
                .userId(userId)
                .isTyping(true)
                .build();

        redisTypingPublisher.publishTypingEvent(request.getConversationId(), typingEvent);
    }

    @MessageMapping("/typing/stop")
    public void handleTypingStop(@Payload TypingRequest request, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        ConversationMember membership = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        redisTypingService.removeTypingUser(request.getConversationId(), userId);

        TypingEventResponse typingEvent = TypingEventResponse.builder()
                .conversationId(request.getConversationId())
                .userId(userId)
                .isTyping(false)
                .build();

        redisTypingPublisher.publishTypingEvent(request.getConversationId(), typingEvent);
    }
}
