package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.CreateMediaMessageRequest;
import com.cena.chat_app.dto.request.RequestPresignedUrlRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.dto.response.PresignedUrlResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MediaService {
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/mpeg", "video/webm", "video/quicktime"
    );
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/webm", "audio/ogg"
    );
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip", "text/plain"
    );

    private final MinioService minioService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final RedisUnreadService redisUnreadService;
    private final BlockingService blockingService;
    private final Counter mediaMessagesCreated;

    public MediaService(MinioService minioService,
                        ConversationRepository conversationRepository,
                        ConversationMemberRepository conversationMemberRepository,
                        MessageRepository messageRepository,
                        UserRepository userRepository,
                        RedisMessagePublisher redisMessagePublisher,
                        RedisUnreadService redisUnreadService,
                        BlockingService blockingService,
                        MeterRegistry meterRegistry) {
        this.minioService = minioService;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.redisUnreadService = redisUnreadService;
        this.blockingService = blockingService;
        this.mediaMessagesCreated = meterRegistry.counter("chat.realtime.media.messages.created");
    }

    public ApiResponse<PresignedUrlResponse> requestPresignedUrl(RequestPresignedUrlRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (request.getFileSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "File size exceeds maximum allowed size of 50MB");
        }

        if (!isAllowedMimeType(request.getMimeType())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "File type not allowed");
        }

        String presignedUrl = minioService.generatePresignedUploadUrl(request.getFileName(), request.getMimeType());
        String fileKey = minioService.extractObjectKeyFromUrl(presignedUrl);

        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .presignedUrl(presignedUrl)
                .fileKey(fileKey)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        return ApiResponse.<PresignedUrlResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Presigned URL generated successfully")
                .data(response)
                .build();
    }

    public ApiResponse<MessageResponse> createMediaMessage(CreateMediaMessageRequest request) {
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

        if ("DIRECT".equals(conversation.getType())) {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(request.getConversationId());
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(currentUserId)) {
                    if (blockingService.areUsersBlockedEitherWay(currentUserId, member.getUserId())) {
                        throw new AppException(ErrorCode.USER_BLOCKED);
                    }
                }
            }
        } else if ("GROUP".equals(conversation.getType())) {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(request.getConversationId());
            List<String> memberIds = members.stream()
                    .map(ConversationMember::getUserId)
                    .collect(java.util.stream.Collectors.toList());
            if (blockingService.isBlockedByAnyMember(currentUserId, memberIds)) {
                throw new AppException(ErrorCode.BLOCKED_BY_USER);
            }
        }

        if (request.getReplyTo() != null && !request.getReplyTo().isEmpty()) {
            Message repliedMessage = messageRepository.findById(request.getReplyTo())
                    .orElseThrow(() -> new AppException(ErrorCode.REPLY_MESSAGE_NOT_FOUND));

            if (!repliedMessage.getConversationId().equals(request.getConversationId())) {
                throw new AppException(ErrorCode.REPLY_MESSAGE_DIFFERENT_CONVERSATION);
            }
        }

        String mediaUrl = minioService.getObjectUrl(request.getFileKey());

        Message message = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(currentUserId)
                .type(request.getType())
                .content(null)
                .mediaUrl(mediaUrl)
                .mediaMetadata(request.getMediaMetadata())
                .replyTo(request.getReplyTo())
                .reactions(new HashMap<>())
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        message = messageRepository.save(message);

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(request.getConversationId());
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(currentUserId)) {
                redisUnreadService.incrementUnreadCount(member.getUserId(), request.getConversationId());
            }
        }

        MessageResponse messageResponse = buildMessageResponse(message);

        redisMessagePublisher.publishMessage(request.getConversationId(), messageResponse);

        mediaMessagesCreated.increment();

        return ApiResponse.<MessageResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Media message created successfully")
                .data(messageResponse)
                .build();
    }

    private boolean isAllowedMimeType(String mimeType) {
        return ALLOWED_IMAGE_TYPES.contains(mimeType) ||
                ALLOWED_VIDEO_TYPES.contains(mimeType) ||
                ALLOWED_AUDIO_TYPES.contains(mimeType) ||
                ALLOWED_FILE_TYPES.contains(mimeType);
    }

    private MessageResponse buildMessageResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);

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
                .mediaMetadata(message.getMediaMetadata())
                .replyTo(message.getReplyTo())
                .reactions(message.getReactions())
                .isDeleted(message.isDeleted())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : null)
                .updatedAt(message.getUpdatedAt() != null ? message.getUpdatedAt().toString() : null)
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
