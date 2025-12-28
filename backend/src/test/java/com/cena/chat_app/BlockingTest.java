package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.BlockUserRequest;
import com.cena.chat_app.dto.request.CreateDirectConversationRequest;
import com.cena.chat_app.dto.request.CreateGroupConversationRequest;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.BlockedUserResponse;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.entity.*;
import com.cena.chat_app.repository.*;
import com.cena.chat_app.security.JwtTokenProvider;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class BlockingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private User user3;
    private String token1;
    private String token2;
    private String token3;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .username("user1")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("User One")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .username("user2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("User Two")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user2 = userRepository.save(user2);

        user3 = User.builder()
                .username("user3")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("User Three")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user3 = userRepository.save(user3);

        token1 = jwtTokenProvider.generateAccessToken(user1.getId());
        token2 = jwtTokenProvider.generateAccessToken(user2.getId());
        token3 = jwtTokenProvider.generateAccessToken(user3.getId());
    }

    @AfterEach
    void tearDown() {
        blockedUserRepository.deleteAll();
        messageRepository.deleteAll();
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testBlockUser() throws Exception {
        BlockUserRequest blockRequest = new BlockUserRequest();
        blockRequest.setUserId(user2.getId());

        String response = sendPostRequest("/api/blocking/block", objectMapper.writeValueAsString(blockRequest), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("SUCCESS", jsonResponse.get("code").asText());

        assertTrue(blockedUserRepository.existsByBlockerIdAndBlockedId(user1.getId(), user2.getId()));
    }

    @Test
    void testBlockUserIdempotency() throws Exception {
        BlockUserRequest blockRequest = new BlockUserRequest();
        blockRequest.setUserId(user2.getId());

        sendPostRequest("/api/blocking/block", objectMapper.writeValueAsString(blockRequest), token1);
        String response = sendPostRequest("/api/blocking/block", objectMapper.writeValueAsString(blockRequest), token1);

        JsonNode jsonResponse = objectMapper.readTree(response);
        assertEquals("success", jsonResponse.get("status").asText());

        long blockCount = blockedUserRepository.findByBlockerId(user1.getId()).size();
        assertEquals(1, blockCount);
    }

    @Test
    void testCannotBlockSelf() throws Exception {
        BlockUserRequest blockRequest = new BlockUserRequest();
        blockRequest.setUserId(user1.getId());

        String response = sendPostRequest("/api/blocking/block", objectMapper.writeValueAsString(blockRequest), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("CANNOT_BLOCK_SELF", jsonResponse.get("code").asText());
    }

    @Test
    void testUnblockUser() throws Exception {
        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        BlockUserRequest unblockRequest = new BlockUserRequest();
        unblockRequest.setUserId(user2.getId());

        String response = sendPostRequest("/api/blocking/unblock", objectMapper.writeValueAsString(unblockRequest), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("SUCCESS", jsonResponse.get("code").asText());

        assertFalse(blockedUserRepository.existsByBlockerIdAndBlockedId(user1.getId(), user2.getId()));
    }

    @Test
    void testGetBlockedUsers() throws Exception {
        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        String response = sendGetRequest("/api/blocking/blocked-users", token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode data = jsonResponse.get("data");
        assertTrue(data.isArray());
        assertEquals(1, data.size());
        assertEquals(user2.getId(), data.get(0).get("userId").asText());
        assertEquals("user2", data.get(0).get("username").asText());
    }

    @Test
    void testCannotCreateDirectConversationWithBlockedUser() throws Exception {
        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        CreateDirectConversationRequest request = new CreateDirectConversationRequest();
        request.setTargetUserId(user2.getId());

        String response = sendPostRequest("/api/conversations/direct", objectMapper.writeValueAsString(request), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("USER_BLOCKED", jsonResponse.get("code").asText());
    }

    @Test
    void testCannotSendMessageInDirectConversationWhenBlocked() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .ownerId(null)
                .createdAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member2);

        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        SendMessageRequest messageRequest = SendMessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Test message")
                .build();

        String response = sendPostRequest("/api/messages", objectMapper.writeValueAsString(messageRequest), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("USER_BLOCKED", jsonResponse.get("code").asText());
    }

    @Test
    void testCannotSendMessageInGroupWhenBlockedByMember() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Test Group")
                .ownerId(user1.getId())
                .createdAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0L)
                .build();
        conversationMemberRepository.save(member2);

        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user2.getId())
                .blockedId(user1.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        SendMessageRequest messageRequest = SendMessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Test message")
                .build();

        String response = sendPostRequest("/api/messages", objectMapper.writeValueAsString(messageRequest), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("BLOCKED_BY_USER", jsonResponse.get("code").asText());
    }

    @Test
    void testGroupCreationSkipsBlockedUsers() throws Exception {
        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        CreateGroupConversationRequest request = new CreateGroupConversationRequest();
        request.setName("Test Group");
        request.setMemberIds(List.of(user2.getId(), user3.getId()));

        String response = sendPostRequest("/api/conversations/group", objectMapper.writeValueAsString(request), token1);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());

        String conversationId = jsonResponse.get("data").get("id").asText();
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);

        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> m.getUserId().equals(user1.getId())));
        assertTrue(members.stream().anyMatch(m -> m.getUserId().equals(user3.getId())));
        assertFalse(members.stream().anyMatch(m -> m.getUserId().equals(user2.getId())));
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

    private String sendGetRequest(String endpoint, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + endpoint))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
