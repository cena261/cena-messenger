package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.*;
import com.cena.chat_app.dto.response.GroupEventResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupManagementService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final RedisGroupEventPublisher redisGroupEventPublisher;

    public GroupManagementService(ConversationRepository conversationRepository,
                                  ConversationMemberRepository conversationMemberRepository,
                                  RedisGroupEventPublisher redisGroupEventPublisher) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.redisGroupEventPublisher = redisGroupEventPublisher;
    }

    @Transactional
    public ApiResponse<GroupEventResponse> leaveGroup(LeaveGroupRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!"GROUP".equals(conversation.getType())) {
            throw new AppException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if ("OWNER".equals(member.getRole())) {
            long memberCount = conversationMemberRepository.countByConversationId(request.getConversationId());
            if (memberCount > 1) {
                throw new AppException(ErrorCode.OWNER_CANNOT_LEAVE);
            }
        }

        conversationMemberRepository.delete(member);

        GroupEventResponse event = GroupEventResponse.builder()
                .eventType("MEMBER_LEFT")
                .conversationId(request.getConversationId())
                .actorId(currentUserId)
                .targetUserId(currentUserId)
                .build();

        redisGroupEventPublisher.publishGroupEvent(request.getConversationId(), event);

        return ApiResponse.<GroupEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Left group successfully")
                .data(event)
                .build();
    }

    @Transactional
    public ApiResponse<GroupEventResponse> kickMember(KickMemberRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!"GROUP".equals(conversation.getType())) {
            throw new AppException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember actorMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (request.getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.CANNOT_KICK_SELF);
        }

        ConversationMember targetMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.TARGET_USER_NOT_MEMBER));

        if ("OWNER".equals(targetMember.getRole())) {
            throw new AppException(ErrorCode.CANNOT_KICK_OWNER);
        }

        if ("OWNER".equals(actorMember.getRole())) {
        } else if ("ADMIN".equals(actorMember.getRole())) {
            if (!"MEMBER".equals(targetMember.getRole())) {
                throw new AppException(ErrorCode.INSUFFICIENT_PERMISSION);
            }
        } else {
            throw new AppException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        conversationMemberRepository.delete(targetMember);

        GroupEventResponse event = GroupEventResponse.builder()
                .eventType("MEMBER_KICKED")
                .conversationId(request.getConversationId())
                .actorId(currentUserId)
                .targetUserId(request.getUserId())
                .build();

        redisGroupEventPublisher.publishGroupEvent(request.getConversationId(), event);

        return ApiResponse.<GroupEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Member kicked successfully")
                .data(event)
                .build();
    }

    @Transactional
    public ApiResponse<GroupEventResponse> changeRole(ChangeRoleRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!"GROUP".equals(conversation.getType())) {
            throw new AppException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember actorMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (!("OWNER".equals(actorMember.getRole()))) {
            throw new AppException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        if (request.getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        ConversationMember targetMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.TARGET_USER_NOT_MEMBER));

        if ("OWNER".equals(targetMember.getRole())) {
            throw new AppException(ErrorCode.INVALID_ROLE_CHANGE);
        }

        if (!("ADMIN".equals(request.getNewRole()) || "MEMBER".equals(request.getNewRole()))) {
            throw new AppException(ErrorCode.INVALID_ROLE_CHANGE);
        }

        String previousRole = targetMember.getRole();
        targetMember.setRole(request.getNewRole());
        conversationMemberRepository.save(targetMember);

        GroupEventResponse event = GroupEventResponse.builder()
                .eventType("ROLE_CHANGED")
                .conversationId(request.getConversationId())
                .actorId(currentUserId)
                .targetUserId(request.getUserId())
                .previousRole(previousRole)
                .newRole(request.getNewRole())
                .build();

        redisGroupEventPublisher.publishGroupEvent(request.getConversationId(), event);

        return ApiResponse.<GroupEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Role changed successfully")
                .data(event)
                .build();
    }

    @Transactional
    public ApiResponse<GroupEventResponse> transferOwnership(TransferOwnershipRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!"GROUP".equals(conversation.getType())) {
            throw new AppException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember actorMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (!("OWNER".equals(actorMember.getRole()))) {
            throw new AppException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        ConversationMember newOwnerMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), request.getNewOwnerId())
                .orElseThrow(() -> new AppException(ErrorCode.NEW_OWNER_NOT_MEMBER));

        actorMember.setRole("ADMIN");
        conversationMemberRepository.save(actorMember);

        newOwnerMember.setRole("OWNER");
        conversationMemberRepository.save(newOwnerMember);

        conversation.setOwnerId(request.getNewOwnerId());
        conversationRepository.save(conversation);

        GroupEventResponse event = GroupEventResponse.builder()
                .eventType("OWNERSHIP_TRANSFERRED")
                .conversationId(request.getConversationId())
                .actorId(currentUserId)
                .targetUserId(request.getNewOwnerId())
                .previousRole("ADMIN")
                .newRole("OWNER")
                .build();

        redisGroupEventPublisher.publishGroupEvent(request.getConversationId(), event);

        return ApiResponse.<GroupEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Ownership transferred successfully")
                .data(event)
                .build();
    }

    @Transactional
    public ApiResponse<GroupEventResponse> updateGroupInfo(UpdateGroupInfoRequest request) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!"GROUP".equals(conversation.getType())) {
            throw new AppException(ErrorCode.NOT_GROUP_CONVERSATION);
        }

        ConversationMember actorMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        if (!("OWNER".equals(actorMember.getRole()) || "ADMIN".equals(actorMember.getRole()))) {
            throw new AppException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        if (request.getName() != null) {
            conversation.setName(request.getName());
        }
        if (request.getAvatarUrl() != null) {
            conversation.setAvatarUrl(request.getAvatarUrl());
        }

        conversationRepository.save(conversation);

        GroupEventResponse event = GroupEventResponse.builder()
                .eventType("GROUP_INFO_UPDATED")
                .conversationId(request.getConversationId())
                .actorId(currentUserId)
                .groupName(conversation.getName())
                .groupAvatarUrl(conversation.getAvatarUrl())
                .build();

        redisGroupEventPublisher.publishGroupEvent(request.getConversationId(), event);

        return ApiResponse.<GroupEventResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("Group info updated successfully")
                .data(event)
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
