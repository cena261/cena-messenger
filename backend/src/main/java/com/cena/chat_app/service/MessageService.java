package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.ReactionRequest;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.dto.response.ReactionEventResponse;
import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.Message;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.MessageRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.websocket.RedisMessagePublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final RedisUnreadService redisUnreadService;
    private final RedisUnreadPublisher redisUnreadPublisher;
    private final RedisReactionPublisher redisReactionPublisher;
    private final Counter messagesSent;
    private final Counter reactionsAdded;

    public MessageService(MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            UserRepository userRepository,
            RedisMessagePublisher redisMessagePublisher,
            RedisUnreadService redisUnreadService,
            RedisUnreadPublisher redisUnreadPublisher,
            RedisReactionPublisher redisReactionPublisher,
            MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.redisUnreadService = redisUnreadService;
        this.redisUnreadPublisher = redisUnreadPublisher;
        this.redisReactionPublisher = redisReactionPublisher;
        this.messagesSent = meterRegistry.counter("chat.realtime.messages.sent");
        this.reactionsAdded = meterRegistry.counter("chat.realtime.reactions.added");
    }

    public ApiResponse<MessageResponse> sendMessage(SendMessageRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        ConversationMember membership = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (!membership.isCanSendMessage()) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED,
                    "You do not have permission to send messages in this conversation");
        }

        Message message = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(currentUserId)
                .type("TEXT")
                .content(request.getContent())
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        message = messageRepository.save(message);
        messagesSent.increment();

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        MessageResponse response = buildMessageResponse(message);

        redisMessagePublisher.publishMessage(request.getConversationId(), response);

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(request.getConversationId());
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(currentUserId)) {
                redisUnreadService.incrementUnreadCount(member.getUserId(), request.getConversationId());
                long unreadCount = redisUnreadService.getUnreadCount(member.getUserId(), request.getConversationId());
                UnreadUpdateResponse unreadUpdate = UnreadUpdateResponse.builder()
                        .conversationId(request.getConversationId())
                        .unreadCount(unreadCount)
                        .build();
                redisUnreadPublisher.publishUnreadUpdate(member.getUserId(), unreadUpdate);
            }
        }

        return ApiResponse.<MessageResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Message sent successfully")
                .data(response)
                .build();
    }

    public ApiResponse<List<MessageResponse>> getMessages(String conversationId, Pageable pageable) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversationMemberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<String> senderIds = messages.stream()
                .map(Message::getSenderId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, User> usersMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            List<User> users = userRepository.findAllById(senderIds);
            usersMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        Map<String, User> finalUsersMap = usersMap;
        List<MessageResponse> responses = messages.stream()
                .map(message -> buildMessageResponse(message, finalUsersMap.get(message.getSenderId())))
                .collect(Collectors.toList());

        return ApiResponse.<List<MessageResponse>>builder()
                .status("success")
                .code("SUCCESS")
                .message("Messages retrieved successfully")
                .data(responses)
                .build();
    }

    private MessageResponse buildMessageResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        return buildMessageResponse(message, sender);
    }

    private MessageResponse buildMessageResponse(Message message, User sender) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderUsername(sender != null ? sender.getUsername() : null)
                .senderDisplayName(sender != null ? sender.getDisplayName() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
                .type(message.getType())
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .replyTo(message.getReplyTo())
                .reactions(message.getReactions())
                .isDeleted(message.isDeleted())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : null)
                .updatedAt(message.getUpdatedAt() != null ? message.getUpdatedAt().toString() : null)
                .build();
    }

    public ApiResponse<ReactionEventResponse> toggleReaction(ReactionRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        conversationMemberRepository.findByConversationIdAndUserId(message.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        Map<String, String> reactions = message.getReactions();
        if (reactions == null) {
            reactions = new HashMap<>();
        }

        boolean added;
        String existingReaction = reactions.get(currentUserId);

        if (existingReaction != null && existingReaction.equals(request.getReactionType())) {
            reactions.remove(currentUserId);
            added = false;
        } else {
            reactions.put(currentUserId, request.getReactionType());
            added = true;
            reactionsAdded.increment();
        }

        message.setReactions(reactions.isEmpty() ? null : reactions);
        message.setUpdatedAt(Instant.now());
        message = messageRepository.save(message);

        ReactionEventResponse reactionEvent = ReactionEventResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .userId(currentUserId)
                .reactionType(request.getReactionType())
                .added(added)
                .allReactions(message.getReactions())
                .build();

        redisReactionPublisher.publishReactionEvent(message.getConversationId(), reactionEvent);

        return ApiResponse.<ReactionEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message(added ? "Reaction added successfully" : "Reaction removed successfully")
                .data(reactionEvent)
                .build();
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return (String) authentication.getPrincipal();
    }
}
