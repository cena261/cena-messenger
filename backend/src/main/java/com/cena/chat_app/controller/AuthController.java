package com.cena.chat_app.controller;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.LoginRequest;
import com.cena.chat_app.dto.request.RefreshTokenRequest;
import com.cena.chat_app.dto.request.RegisterRequest;
import com.cena.chat_app.dto.request.RequestPasswordResetRequest;
import com.cena.chat_app.dto.request.ResetPasswordRequest;
import com.cena.chat_app.dto.response.AuthResponse;
import com.cena.chat_app.dto.response.UserProfileResponse;
import com.cena.chat_app.service.AuthService;
import com.cena.chat_app.service.PasswordResetService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return authService.register(request, response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String refreshToken = extractRefreshTokenFromCookie(httpRequest);
        return authService.refresh(refreshToken);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        return authService.logout(refreshToken, response);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        return passwordResetService.requestPasswordReset(request.getEmail());
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordResetService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
