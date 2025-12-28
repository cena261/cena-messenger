package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.BlockedUserResponse;
import com.cena.chat_app.entity.BlockedUser;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.BlockedUserRepository;
import com.cena.chat_app.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlockingService {
    private final BlockedUserRepository blockedUserRepository;
    private final UserRepository userRepository;
    private final Counter usersBlocked;
    private final Counter usersUnblocked;

    public BlockingService(BlockedUserRepository blockedUserRepository,
                          UserRepository userRepository,
                          MeterRegistry meterRegistry) {
        this.blockedUserRepository = blockedUserRepository;
        this.userRepository = userRepository;
        this.usersBlocked = meterRegistry.counter("chat.blocking.users.blocked");
        this.usersUnblocked = meterRegistry.counter("chat.blocking.users.unblocked");
    }

    @Transactional
    public ApiResponse<Void> blockUser(String targetUserId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean alreadyBlocked = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId);
        if (!alreadyBlocked) {
            BlockedUser blockedUser = BlockedUser.builder()
                    .blockerId(currentUserId)
                    .blockedId(targetUserId)
                    .createdAt(Instant.now())
                    .build();
            blockedUserRepository.save(blockedUser);
            usersBlocked.increment();
        }

        return ApiResponse.<Void>builder()
                .status("success")
                .code("SUCCESS")
                .message("User blocked successfully")
                .build();
    }

    @Transactional
    public ApiResponse<Void> unblockUser(String targetUserId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        blockedUserRepository.deleteByBlockerIdAndBlockedId(currentUserId, targetUserId);
        usersUnblocked.increment();

        return ApiResponse.<Void>builder()
                .status("success")
                .code("SUCCESS")
                .message("User unblocked successfully")
                .build();
    }

    public ApiResponse<List<BlockedUserResponse>> getBlockedUsers() {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<BlockedUser> blockedUsers = blockedUserRepository.findByBlockerId(currentUserId);

        List<String> blockedUserIds = blockedUsers.stream()
                .map(BlockedUser::getBlockedId)
                .collect(Collectors.toList());

        Map<String, User> usersMap = userRepository.findAllById(blockedUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<BlockedUserResponse> responses = blockedUsers.stream()
                .map(blockedUser -> {
                    User user = usersMap.get(blockedUser.getBlockedId());
                    return BlockedUserResponse.builder()
                            .userId(blockedUser.getBlockedId())
                            .username(user != null ? user.getUsername() : null)
                            .displayName(user != null ? user.getDisplayName() : null)
                            .avatarUrl(user != null ? user.getAvatarUrl() : null)
                            .blockedAt(blockedUser.getCreatedAt() != null ? blockedUser.getCreatedAt().toString() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return ApiResponse.<List<BlockedUserResponse>>builder()
                .status("success")
                .code("SUCCESS")
                .message("Blocked users retrieved successfully")
                .data(responses)
                .build();
    }

    public boolean isBlocked(String userId1, String userId2) {
        return blockedUserRepository.existsByBlockerIdAndBlockedId(userId1, userId2);
    }

    public boolean areUsersBlockedEitherWay(String userId1, String userId2) {
        return isBlocked(userId1, userId2) || isBlocked(userId2, userId1);
    }

    public boolean isBlockedByAnyMember(String senderId, List<String> memberIds) {
        for (String memberId : memberIds) {
            if (!memberId.equals(senderId) && isBlocked(memberId, senderId)) {
                return true;
            }
        }
        return false;
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return (String) authentication.getPrincipal();
    }
}
