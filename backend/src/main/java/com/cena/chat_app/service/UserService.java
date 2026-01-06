package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.RequestProfilePresignedUrlRequest;
import com.cena.chat_app.dto.request.UpdateProfileRequest;
import com.cena.chat_app.dto.response.PresignedUrlResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.dto.response.UserSearchResponse;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.BlockedUserRepository;
import com.cena.chat_app.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final MinioService minioService;

    public UserService(UserRepository userRepository, BlockedUserRepository blockedUserRepository, MinioService minioService) {
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.minioService = minioService;
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

    public ApiResponse<UserProfileResponse> updateProfile(UpdateProfileRequest request) {
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

        if (request.getDisplayName() == null && request.getAvatarUrl() == null) {
            return ApiResponse.<UserProfileResponse>builder()
                .status("error")
                .code("INVALID_REQUEST")
                .message("At least one field must be provided")
                .build();
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        userRepository.save(user);

        UserProfileResponse profileResponse = UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();

        return ApiResponse.<UserProfileResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Profile updated successfully")
            .data(profileResponse)
            .build();
    }

    public ApiResponse<PresignedUrlResponse> requestAvatarPresignedUrl(RequestProfilePresignedUrlRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ApiResponse.<PresignedUrlResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        if (request.getFileSize() > MAX_AVATAR_SIZE) {
            return ApiResponse.<PresignedUrlResponse>builder()
                .status("error")
                .code("ACCESS_DENIED")
                .message("File size exceeds maximum allowed size of 5MB")
                .build();
        }

        if (!ALLOWED_AVATAR_TYPES.contains(request.getMimeType())) {
            return ApiResponse.<PresignedUrlResponse>builder()
                .status("error")
                .code("ACCESS_DENIED")
                .message("File type not allowed. Only images are supported.")
                .build();
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
}
