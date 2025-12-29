package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.entity.PasswordResetToken;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.PasswordResetTokenRepository;
import com.cena.chat_app.repository.RefreshTokenRepository;
import com.cena.chat_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ApiResponse<Void> requestPasswordReset(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        long startTime = System.currentTimeMillis();

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isPresent()) {
            String code = generateSixDigitCode();
            Instant expiresAt = Instant.now().plusSeconds(CODE_EXPIRY_MINUTES * 60L);

            resetTokenRepository.deleteByEmail(normalizedEmail);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(normalizedEmail)
                .code(code)
                .expiresAt(expiresAt)
                .used(false)
                .createdAt(Instant.now())
                .build();

            resetTokenRepository.save(resetToken);

            emailService.sendPasswordResetCode(normalizedEmail, code);
        }

        ensureMinimumResponseTime(startTime, 200);

        return ApiResponse.<Void>builder()
            .status("success")
            .message("If the email exists, a reset code has been sent")
            .build();
    }

    @Transactional
    public ApiResponse<Void> resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = email.toLowerCase().trim();

        long startTime = System.currentTimeMillis();

        Optional<PasswordResetToken> tokenOptional = resetTokenRepository
            .findByEmailAndCodeAndUsedFalse(normalizedEmail, code);

        if (tokenOptional.isEmpty()) {
            ensureMinimumResponseTime(startTime, 200);
            throw new AppException(ErrorCode.INVALID_RESET_CODE);
        }

        PasswordResetToken resetToken = tokenOptional.get();

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            ensureMinimumResponseTime(startTime, 200);
            throw new AppException(ErrorCode.INVALID_RESET_CODE);
        }

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isEmpty()) {
            ensureMinimumResponseTime(startTime, 200);
            throw new AppException(ErrorCode.INVALID_RESET_CODE);
        }

        User user = userOptional.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        refreshTokenRepository.deleteByUserId(user.getId());

        ensureMinimumResponseTime(startTime, 200);

        return ApiResponse.<Void>builder()
            .status("success")
            .message("Password has been reset successfully")
            .build();
    }

    private String generateSixDigitCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private void ensureMinimumResponseTime(long startTime, long minimumMillis) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < minimumMillis) {
            try {
                Thread.sleep(minimumMillis - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
