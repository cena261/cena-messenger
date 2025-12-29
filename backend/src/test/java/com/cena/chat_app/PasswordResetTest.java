package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.entity.PasswordResetToken;
import com.cena.chat_app.entity.RefreshToken;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.PasswordResetTokenRepository;
import com.cena.chat_app.repository.RefreshTokenRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import com.cena.chat_app.service.EmailService;
import com.cena.chat_app.service.RedisRateLimitService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestRedisConfiguration.class, TestMongoDBConfiguration.class})
class PasswordResetTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RedisRateLimitService rateLimitService;

    @MockitoBean
    private EmailService emailService;

    private User testUser;
    private String originalPasswordHash;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendPasswordResetCode(anyString(), anyString());

        testUser = User.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode("oldPassword123"))
                .displayName("Test User")
                .email("test@example.com")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testUser = userRepository.save(testUser);
        originalPasswordHash = testUser.getPasswordHash();

        RefreshToken refreshToken1 = RefreshToken.builder()
                .userId(testUser.getId())
                .token("token1")
                .deviceId("device1")
                .expiredAt(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken1);

        RefreshToken refreshToken2 = RefreshToken.builder()
                .userId(testUser.getId())
                .token("token2")
                .deviceId("device2")
                .expiredAt(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken2);
    }

    @AfterEach
    void tearDown() {
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        rateLimitService.resetLimit("127.0.0.1", "auth_forgot_password");
        rateLimitService.resetLimit("127.0.0.1", "auth_reset_password");
    }

    @Test
    void testRequestPasswordResetForExistingEmail() throws Exception {
        String requestBody = "{\"email\":\"test@example.com\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("If the email exists, a reset code has been sent", jsonResponse.get("message").asText());

        Optional<PasswordResetToken> token = resetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("test@example.com"))
                .findFirst();
        assertTrue(token.isPresent());
        assertEquals(6, token.get().getCode().length());
        assertFalse(token.get().isUsed());
        assertTrue(token.get().getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void testRequestPasswordResetForNonExistingEmail() throws Exception {
        String requestBody = "{\"email\":\"nonexistent@example.com\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("If the email exists, a reset code has been sent", jsonResponse.get("message").asText());

        Optional<PasswordResetToken> token = resetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("nonexistent@example.com"))
                .findFirst();
        assertFalse(token.isPresent());
    }

    @Test
    void testResetPasswordWithCorrectCode() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"newPassword123\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("Password has been reset successfully", jsonResponse.get("message").asText());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNotEquals(originalPasswordHash, updatedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("newPassword123", updatedUser.getPasswordHash()));

        PasswordResetToken usedToken = resetTokenRepository.findById(resetToken.getId()).orElseThrow();
        assertTrue(usedToken.isUsed());

        assertEquals(0, refreshTokenRepository.findByUserId(testUser.getId()).size());
    }

    @Test
    void testResetPasswordWithIncorrectCode() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"999999\",\"newPassword\":\"newPassword123\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode());
        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("INVALID_RESET_CODE", jsonResponse.get("code").asText());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(originalPasswordHash, updatedUser.getPasswordHash());

        assertEquals(2, refreshTokenRepository.findByUserId(testUser.getId()).size());
    }

    @Test
    void testResetPasswordWithExpiredCode() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().minusSeconds(60))
                .used(false)
                .createdAt(Instant.now().minusSeconds(660))
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"newPassword123\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode());
        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("INVALID_RESET_CODE", jsonResponse.get("code").asText());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(originalPasswordHash, updatedUser.getPasswordHash());
    }

    @Test
    void testResetPasswordWithAlreadyUsedCode() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(true)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"newPassword123\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode());
        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("INVALID_RESET_CODE", jsonResponse.get("code").asText());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(originalPasswordHash, updatedUser.getPasswordHash());
    }

    @Test
    void testNewCodeInvalidatesPreviousCode() throws Exception {
        String requestBody = "{\"email\":\"test@example.com\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        client.send(request1, HttpResponse.BodyHandlers.ofString());

        Optional<PasswordResetToken> firstToken = resetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("test@example.com"))
                .findFirst();
        assertTrue(firstToken.isPresent());
        String firstCode = firstToken.get().getCode();

        Thread.sleep(100);

        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        client.send(request2, HttpResponse.BodyHandlers.ofString());

        long count = resetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("test@example.com"))
                .count();
        assertEquals(1, count);

        Optional<PasswordResetToken> secondToken = resetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("test@example.com"))
                .findFirst();
        assertTrue(secondToken.isPresent());
        String secondCode = secondToken.get().getCode();

        assertNotEquals(firstCode, secondCode);
    }

    @Test
    void testRateLimitingOnForgotPassword() throws Exception {
        String requestBody = "{\"email\":\"test@example.com\"}";

        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < 7; i++) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                rateLimitedCount++;
            } else {
                successCount++;
            }
        }

        assertTrue(successCount <= 5);
        assertTrue(rateLimitedCount >= 2);
    }

    @Test
    void testRateLimitingOnResetPassword() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"newPassword123\"}";

        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < 7; i++) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                rateLimitedCount++;
            } else {
                successCount++;
            }
        }

        assertTrue(successCount <= 5);
        assertTrue(rateLimitedCount >= 2);
    }

    @Test
    void testPasswordActuallyUpdated() throws Exception {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"brandNewPassword456\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();

        assertFalse(passwordEncoder.matches("oldPassword123", updatedUser.getPasswordHash()));
        assertTrue(passwordEncoder.matches("brandNewPassword456", updatedUser.getPasswordHash()));
    }

    @Test
    void testRefreshTokensRevokedAfterReset() throws Exception {
        assertEquals(2, refreshTokenRepository.findByUserId(testUser.getId()).size());

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email("test@example.com")
                .code("123456")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .createdAt(Instant.now())
                .build();
        resetTokenRepository.save(resetToken);

        String requestBody = "{\"email\":\"test@example.com\",\"code\":\"123456\",\"newPassword\":\"newPassword123\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/reset-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(0, refreshTokenRepository.findByUserId(testUser.getId()).size());
    }
}
