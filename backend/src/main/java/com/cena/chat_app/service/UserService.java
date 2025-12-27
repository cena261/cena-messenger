package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
