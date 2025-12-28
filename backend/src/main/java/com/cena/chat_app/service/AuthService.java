package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.LoginRequest;
import com.cena.chat_app.dto.request.RegisterRequest;
import com.cena.chat_app.dto.response.AuthResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.entity.RefreshToken;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.RefreshTokenRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 2592000;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                      RefreshTokenRepository refreshTokenRepository,
                      PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ApiResponse<AuthResponse> register(RegisterRequest request, HttpServletResponse response) {
        String normalizedUsername = request.getUsername().toLowerCase().trim();
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        String normalizedPhone = normalizePhone(request.getPhone());

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("USERNAME_EXISTS")
                .message("Username already exists")
                .build();
        }

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("EMAIL_EXISTS")
                .message("Email already exists")
                .build();
        }

        if (normalizedPhone != null) {
            if (userRepository.findByPhone(normalizedPhone).isPresent()) {
                return ApiResponse.<AuthResponse>builder()
                    .status("error")
                    .code("PHONE_EXISTS")
                    .message("Phone number already exists")
                    .build();
            }
        }

        User user = User.builder()
            .username(normalizedUsername)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .email(normalizedEmail)
            .phone(normalizedPhone)
            .status("ACTIVE")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .token(refreshTokenValue)
            .deviceId(UUID.randomUUID().toString())
            .expiredAt(jwtTokenProvider.getRefreshTokenExpiration())
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);

        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshTokenValue);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(cookie);

        UserProfileResponse userProfile = UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getAccessTokenValidity() / 1000)
            .user(userProfile)
            .build();

        return ApiResponse.<AuthResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("User registered successfully")
            .data(authResponse)
            .build();
    }

    public ApiResponse<AuthResponse> login(LoginRequest request, HttpServletResponse response) {
        String normalizedUsername = request.getUsername().toLowerCase().trim();
        User user = userRepository.findByUsername(normalizedUsername)
            .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("INVALID_CREDENTIALS")
                .message("Invalid username or password")
                .build();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .token(refreshTokenValue)
            .deviceId(UUID.randomUUID().toString())
            .expiredAt(jwtTokenProvider.getRefreshTokenExpiration())
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);

        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshTokenValue);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(cookie);

        UserProfileResponse userProfile = UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getAccessTokenValidity() / 1000)
            .user(userProfile)
            .build();

        return ApiResponse.<AuthResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Login successful")
            .data(authResponse)
            .build();
    }

    public ApiResponse<AuthResponse> refresh(String refreshTokenValue) {
        if (refreshTokenValue == null) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("REFRESH_TOKEN_MISSING")
                .message("Refresh token is missing")
                .build();
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
            .orElse(null);

        if (refreshToken == null) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("INVALID_REFRESH_TOKEN")
                .message("Invalid refresh token")
                .build();
        }

        if (refreshToken.isRevoked()) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("REFRESH_TOKEN_REVOKED")
                .message("Refresh token has been revoked")
                .build();
        }

        if (refreshToken.getExpiredAt().isBefore(Instant.now())) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("REFRESH_TOKEN_EXPIRED")
                .message("Refresh token has expired")
                .build();
        }

        User user = userRepository.findById(refreshToken.getUserId())
            .orElse(null);

        if (user == null) {
            return ApiResponse.<AuthResponse>builder()
                .status("error")
                .code("USER_NOT_FOUND")
                .message("User not found")
                .build();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());

        UserProfileResponse userProfile = UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getAccessTokenValidity() / 1000)
            .user(userProfile)
            .build();

        return ApiResponse.<AuthResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Token refreshed successfully")
            .data(authResponse)
            .build();
    }

    public ApiResponse<Void> logout(String refreshTokenValue, HttpServletResponse response) {
        if (refreshTokenValue != null) {
            refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshTokenRepository.save(refreshToken);
                });
        }

        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ApiResponse.<Void>builder()
            .status("success")
            .code("SUCCESS")
            .message("Logout successful")
            .build();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
