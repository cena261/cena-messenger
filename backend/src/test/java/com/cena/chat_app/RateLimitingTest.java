package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import com.cena.chat_app.service.RedisRateLimitService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class RateLimitingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisRateLimitService rateLimitService;

    private User testUser;
    private String accessToken;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Test User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testUser = userRepository.save(testUser);

        testConversation = Conversation.builder()
                .type("DIRECT")
                .ownerId(testUser.getId())
                .createdAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        testConversation = conversationRepository.save(testConversation);

        ConversationMember member = ConversationMember.builder()
                .conversationId(testConversation.getId())
                .userId(testUser.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member);

        accessToken = jwtTokenProvider.generateAccessToken(testUser.getId());
    }

    @AfterEach
    void tearDown() {
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        rateLimitService.resetLimit(testUser.getId(), "message_send");
        rateLimitService.resetLimit(testUser.getId(), "conversation_create");
        rateLimitService.resetLimit(testUser.getId(), "blocking_action");
    }

    @Test
    void testRestApiRateLimitForMessages() throws Exception {
        SendMessageRequest messageRequest = SendMessageRequest.builder()
                .conversationId(testConversation.getId())
                .content("Test message")
                .build();

        String requestBody = objectMapper.writeValueAsString(messageRequest);

        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < 65; i++) {
            String response = sendPostRequest("/api/messages", requestBody, accessToken);
            JsonNode jsonResponse = objectMapper.readTree(response);

            if ("success".equals(jsonResponse.get("status").asText())) {
                successCount++;
            } else if ("RATE_LIMIT_EXCEEDED".equals(jsonResponse.get("code").asText())) {
                rateLimitedCount++;
            }
        }

        assertTrue(successCount <= 60, "Should allow up to 60 messages per minute");
        assertTrue(rateLimitedCount >= 5, "Should block at least 5 requests after limit");
        assertEquals(65, successCount + rateLimitedCount);
    }

    @Test
    void testRestApiRateLimitForAuthenticationByIp() throws Exception {
        String loginRequest = "{\"username\":\"testuser\",\"password\":\"wrongpassword\"}";

        int totalRequests = 7;
        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < totalRequests; i++) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginRequest))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                rateLimitedCount++;
            } else {
                successCount++;
            }
        }

        assertTrue(successCount <= 5, "Should allow up to 5 login attempts per minute");
        assertTrue(rateLimitedCount >= 2, "Should block at least 2 requests after limit");
    }

    @Test
    void testRestApiNormalTrafficWithinLimits() throws Exception {
        SendMessageRequest messageRequest = SendMessageRequest.builder()
                .conversationId(testConversation.getId())
                .content("Test message")
                .build();

        String requestBody = objectMapper.writeValueAsString(messageRequest);

        for (int i = 0; i < 5; i++) {
            String response = sendPostRequest("/api/messages", requestBody, accessToken);
            JsonNode jsonResponse = objectMapper.readTree(response);

            assertEquals("success", jsonResponse.get("status").asText(),
                    "Normal traffic within limits should succeed");
        }
    }

    @Test
    void testRateLimitServiceDirectly() throws Exception {
        String identifier = "test-user-123";
        String action = "test-action";
        int maxRequests = 5;

        for (int i = 0; i < 5; i++) {
            boolean allowed = rateLimitService.allowRequest(identifier, action, maxRequests, java.time.Duration.ofMinutes(1));
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
        }

        boolean sixthRequest = rateLimitService.allowRequest(identifier, action, maxRequests, java.time.Duration.ofMinutes(1));
        assertFalse(sixthRequest, "Sixth request should be blocked");

        Long currentCount = rateLimitService.getCurrentCount(identifier, action);
        assertEquals(6, currentCount, "Should have 6 total attempts");

        rateLimitService.resetLimit(identifier, action);
        Long afterReset = rateLimitService.getCurrentCount(identifier, action);
        assertEquals(0, afterReset, "Count should be reset to 0");

        boolean afterResetRequest = rateLimitService.allowRequest(identifier, action, maxRequests, java.time.Duration.ofMinutes(1));
        assertTrue(afterResetRequest, "Request after reset should be allowed");
    }

    @Test
    void testMultipleUsersIndependentRateLimits() throws Exception {
        User user2 = User.builder()
                .username("testuser2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Test User 2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user2 = userRepository.save(user2);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(testConversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member2);

        String token2 = jwtTokenProvider.generateAccessToken(user2.getId());

        SendMessageRequest messageRequest = SendMessageRequest.builder()
                .conversationId(testConversation.getId())
                .content("Test message")
                .build();
        String requestBody = objectMapper.writeValueAsString(messageRequest);

        for (int i = 0; i < 61; i++) {
            sendPostRequest("/api/messages", requestBody, accessToken);
        }

        String user2Response = sendPostRequest("/api/messages", requestBody, token2);
        JsonNode jsonResponse = objectMapper.readTree(user2Response);
        assertEquals("success", jsonResponse.get("status").asText(),
                "User 2 should not be affected by User 1's rate limit");

        rateLimitService.resetLimit(user2.getId(), "message_send");
    }

    @Test
    void testConversationCreationRateLimit() throws Exception {
        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String conversationRequest = "{\"targetUserId\":\"" + targetUser.getId() + "\"}";

        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < 12; i++) {
            String response = sendPostRequest("/api/conversations/direct", conversationRequest, accessToken);
            JsonNode jsonResponse = objectMapper.readTree(response);

            if ("success".equals(jsonResponse.get("status").asText())) {
                successCount++;
            } else if ("RATE_LIMIT_EXCEEDED".equals(jsonResponse.get("code").asText())) {
                rateLimitedCount++;
            }
        }

        assertTrue(successCount <= 10, "Should allow up to 10 conversation creations per minute");
        assertTrue(rateLimitedCount >= 2, "Should block at least 2 requests after limit");
    }

    private String sendPostRequest(String endpoint, String body, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
