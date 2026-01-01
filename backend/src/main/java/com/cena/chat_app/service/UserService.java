package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.dto.response.UserSearchResponse;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.BlockedUserRepository;
import com.cena.chat_app.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;

    public UserService(UserRepository userRepository, BlockedUserRepository blockedUserRepository) {
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
    }

    public ApiResponse<UserProfileResponse> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ApiResponse.<UserProfileResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        String userId = (String) authentication.getPrincipal();

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ApiResponse.<UserProfileResponse>builder()
                .status("error")
                .code("USER_NOT_FOUND")
                .message("User not found")
                .build();
        }

        UserProfileResponse userProfile = UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();

        return ApiResponse.<UserProfileResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("User profile retrieved successfully")
            .data(userProfile)
            .build();
    }

    public ApiResponse<UserSearchResponse> searchUser(String query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ApiResponse.<UserSearchResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        String currentUserId = (String) authentication.getPrincipal();

        String normalizedQuery = query.toLowerCase().trim();
        String trimmedQuery = query.trim();

        Optional<User> foundUser = userRepository.findByUsername(normalizedQuery);
        if (foundUser.isEmpty()) {
            foundUser = userRepository.findByEmail(normalizedQuery);
        }
        if (foundUser.isEmpty()) {
            foundUser = userRepository.findByPhone(trimmedQuery);
        }

        if (foundUser.isEmpty()) {
            return ApiResponse.<UserSearchResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("No user found")
                .data(null)
                .build();
        }

        User user = foundUser.get();

        if (user.getId().equals(currentUserId)) {
            return ApiResponse.<UserSearchResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("No user found")
                .data(null)
                .build();
        }

        boolean isBlocked = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, user.getId());
        boolean isBlockedBy = blockedUserRepository.existsByBlockerIdAndBlockedId(user.getId(), currentUserId);

        if (isBlocked || isBlockedBy) {
            return ApiResponse.<UserSearchResponse>builder()
                .status("success")
                .code("SUCCESS")
                .message("No user found")
                .data(null)
                .build();
        }

        UserSearchResponse searchResponse = UserSearchResponse.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .avatarUrl(user.getAvatarUrl())
            .build();

        return ApiResponse.<UserSearchResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("User found")
            .data(searchResponse)
            .build();
    }
}
