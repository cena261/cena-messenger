package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.CreateDirectConversationRequest;
import com.cena.chat_app.dto.request.CreateGroupConversationRequest;
import com.cena.chat_app.dto.response.ConversationMemberResponse;
import com.cena.chat_app.dto.response.ConversationResponse;
import com.cena.chat_app.dto.response.SeenEventResponse;
import com.cena.chat_app.dto.response.UnreadUpdateResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final RedisUnreadService redisUnreadService;
    private final RedisUnreadPublisher redisUnreadPublisher;
    private final RedisSeenPublisher redisSeenPublisher;

    public ConversationService(ConversationRepository conversationRepository,
                              ConversationMemberRepository conversationMemberRepository,
                              UserRepository userRepository,
                              RedisUnreadService redisUnreadService,
                              RedisUnreadPublisher redisUnreadPublisher,
                              RedisSeenPublisher redisSeenPublisher) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.redisUnreadService = redisUnreadService;
        this.redisUnreadPublisher = redisUnreadPublisher;
        this.redisSeenPublisher = redisSeenPublisher;
    }

    public ApiResponse<ConversationResponse> createDirectConversation(CreateDirectConversationRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ApiResponse.<ConversationResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        if (request.getTargetUserId() == null || request.getTargetUserId().equals(currentUserId)) {
            return ApiResponse.<ConversationResponse>builder()
                .status("error")
                .code("INVALID_TARGET_USER")
                .message("Invalid target user")
                .build();
        }

        User targetUser = userRepository.findById(request.getTargetUserId()).orElse(null);
        if (targetUser == null) {
            return ApiResponse.<ConversationResponse>builder()
                .status("error")
                .code("USER_NOT_FOUND")
                .message("Target user not found")
                .build();
        }

        Conversation existingConversation = findExistingDirectConversation(currentUserId, request.getTargetUserId());
        if (existingConversation != null) {
            ConversationResponse response = buildConversationResponse(existingConversation);
            return ApiResponse.<ConversationResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Conversation already exists")
                .data(response)
                .build();
        }

        Conversation conversation = Conversation.builder()
            .type("DIRECT")
            .ownerId(null)
            .lastMessageAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
            .conversationId(conversation.getId())
            .userId(currentUserId)
            .role("MEMBER")
            .canSendMessage(true)
            .joinedAt(Instant.now())
            .unreadCount(0L)
            .build();

        ConversationMember member2 = ConversationMember.builder()
            .conversationId(conversation.getId())
            .userId(request.getTargetUserId())
            .role("MEMBER")
            .canSendMessage(true)
            .joinedAt(Instant.now())
            .unreadCount(0L)
            .build();

        conversationMemberRepository.save(member1);
        conversationMemberRepository.save(member2);

        ConversationResponse response = buildConversationResponse(conversation);

        return ApiResponse.<ConversationResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Direct conversation created successfully")
            .data(response)
            .build();
    }

    public ApiResponse<ConversationResponse> createGroupConversation(CreateGroupConversationRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ApiResponse.<ConversationResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        Conversation conversation = Conversation.builder()
            .type("GROUP")
            .name(request.getName())
            .avatarUrl(request.getAvatarUrl())
            .ownerId(currentUserId)
            .lastMessageAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        conversation = conversationRepository.save(conversation);

        ConversationMember ownerMember = ConversationMember.builder()
            .conversationId(conversation.getId())
            .userId(currentUserId)
            .role("OWNER")
            .canSendMessage(true)
            .joinedAt(Instant.now())
            .unreadCount(0L)
            .build();

        conversationMemberRepository.save(ownerMember);

        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            for (String memberId : request.getMemberIds()) {
                if (!memberId.equals(currentUserId)) {
                    ConversationMember member = ConversationMember.builder()
                        .conversationId(conversation.getId())
                        .userId(memberId)
                        .role("MEMBER")
                        .canSendMessage(true)
                        .joinedAt(Instant.now())
                        .unreadCount(0L)
                        .build();
                    conversationMemberRepository.save(member);
                }
            }
        }

        ConversationResponse response = buildConversationResponse(conversation);

        return ApiResponse.<ConversationResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Group conversation created successfully")
            .data(response)
            .build();
    }

    public ApiResponse<List<ConversationResponse>> getConversations() {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ApiResponse.<List<ConversationResponse>>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        List<ConversationMember> userConversationMembers = conversationMemberRepository.findByUserId(currentUserId);

        List<String> conversationIds = userConversationMembers.stream()
            .map(ConversationMember::getConversationId)
            .collect(Collectors.toList());

        if (conversationIds.isEmpty()) {
            return ApiResponse.<List<ConversationResponse>>builder()
                .status("success")
                .code("SUCCESS")
                .message("Conversations retrieved successfully")
                .data(new ArrayList<>())
                .build();
        }

        List<Conversation> conversations = conversationRepository.findAllById(conversationIds);

        conversations.sort((c1, c2) -> {
            Instant t1 = c1.getLastMessageAt();
            Instant t2 = c2.getLastMessageAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        List<ConversationResponse> responses = conversations.stream()
            .map(this::buildConversationResponse)
            .collect(Collectors.toList());

        return ApiResponse.<List<ConversationResponse>>builder()
            .status("success")
            .code("SUCCESS")
            .message("Conversations retrieved successfully")
            .data(responses)
            .build();
    }

    public ApiResponse<UnreadUpdateResponse> markConversationAsRead(String conversationId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        ConversationMember membership = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        long currentUnreadCount = redisUnreadService.getUnreadCount(currentUserId, conversationId);

        membership.setUnreadCount(currentUnreadCount);
        membership.setLastReadMessageId(conversation.getLastMessageId());
        conversationMemberRepository.save(membership);

        redisUnreadService.resetUnreadCount(currentUserId, conversationId);

        UnreadUpdateResponse unreadUpdate = UnreadUpdateResponse.builder()
                .conversationId(conversationId)
                .unreadCount(0L)
                .build();

        redisUnreadPublisher.publishUnreadUpdate(currentUserId, unreadUpdate);

        if (conversation.getLastMessageId() != null) {
            SeenEventResponse seenEvent = SeenEventResponse.builder()
                    .conversationId(conversationId)
                    .userId(currentUserId)
                    .lastReadMessageId(conversation.getLastMessageId())
                    .build();

            redisSeenPublisher.publishSeenEvent(conversationId, seenEvent);
        }

        return ApiResponse.<UnreadUpdateResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Conversation marked as read")
                .data(unreadUpdate)
                .build();
    }

    private Conversation findExistingDirectConversation(String userId1, String userId2) {
        List<ConversationMember> user1Conversations = conversationMemberRepository.findByUserId(userId1);

        for (ConversationMember member : user1Conversations) {
            Conversation conversation = conversationRepository.findById(member.getConversationId()).orElse(null);
            if (conversation != null && "DIRECT".equals(conversation.getType())) {
                List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
                if (members.size() == 2) {
                    Set<String> memberUserIds = members.stream()
                        .map(ConversationMember::getUserId)
                        .collect(Collectors.toSet());
                    if (memberUserIds.contains(userId1) && memberUserIds.contains(userId2)) {
                        return conversation;
                    }
                }
            }
        }
        return null;
    }

    private ConversationResponse buildConversationResponse(Conversation conversation) {
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());

        List<String> userIds = members.stream()
            .map(ConversationMember::getUserId)
            .collect(Collectors.toList());

        Map<String, User> usersMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userRepository.findAllById(userIds);
            usersMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        Map<String, User> finalUsersMap = usersMap;
        List<ConversationMemberResponse> memberResponses = members.stream()
            .map(member -> {
                User user = finalUsersMap.get(member.getUserId());
                return ConversationMemberResponse.builder()
                    .userId(member.getUserId())
                    .username(user != null ? user.getUsername() : null)
                    .displayName(user != null ? user.getDisplayName() : null)
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .role(member.getRole())
                    .joinedAt(member.getJoinedAt())
                    .build();
            })
            .collect(Collectors.toList());

        return ConversationResponse.builder()
            .id(conversation.getId())
            .type(conversation.getType())
            .name(conversation.getName())
            .avatarUrl(conversation.getAvatarUrl())
            .ownerId(conversation.getOwnerId())
            .lastMessageAt(conversation.getLastMessageAt())
            .createdAt(conversation.getCreatedAt())
            .members(memberResponses)
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
