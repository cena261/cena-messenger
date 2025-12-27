package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.MessageResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository,
                         ConversationRepository conversationRepository,
                         ConversationMemberRepository conversationMemberRepository,
                         UserRepository userRepository,
                         SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
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
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED, "You do not have permission to send messages in this conversation");
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

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        MessageResponse response = buildMessageResponse(message);

        messagingTemplate.convertAndSend("/topic/conversation." + request.getConversationId(), response);

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
            .isDeleted(message.isDeleted())
            .createdAt(message.getCreatedAt())
            .updatedAt(message.getUpdatedAt())
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
