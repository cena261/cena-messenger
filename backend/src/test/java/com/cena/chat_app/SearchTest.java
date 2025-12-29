package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.entity.*;
import com.cena.chat_app.repository.*;
import com.cena.chat_app.security.JwtTokenProvider;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class SearchTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private User user3;
    private String user1Token;
    private String user2Token;
    private String user3Token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        conversationRepository.deleteAll();
        conversationMemberRepository.deleteAll();
        messageRepository.deleteAll();
        blockedUserRepository.deleteAll();

        user1 = User.builder()
                .username("alice")
                .displayName("Alice Smith")
                .email("alice@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user1 = userRepository.save(user1);
        user1Token = jwtTokenProvider.generateAccessToken(user1.getId());

        user2 = User.builder()
                .username("bob")
                .displayName("Bob Jones")
                .email("bob@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user2 = userRepository.save(user2);
        user2Token = jwtTokenProvider.generateAccessToken(user2.getId());

        user3 = User.builder()
                .username("charlie")
                .displayName("Charlie Brown")
                .email("charlie@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user3 = userRepository.save(user3);
        user3Token = jwtTokenProvider.generateAccessToken(user3.getId());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        conversationRepository.deleteAll();
        conversationMemberRepository.deleteAll();
        messageRepository.deleteAll();
        blockedUserRepository.deleteAll();
    }

    @Test
    void testSearchGroupConversationByName() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        String response = sendGetRequest("/api/search/conversations?query=project", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertTrue(jsonResponse.get("data").isArray());
        assertEquals(1, jsonResponse.get("data").size());
        assertEquals("Project Team", jsonResponse.get("data").get(0).get("name").asText());
        assertEquals("GROUP", jsonResponse.get("data").get(0).get("type").asText());
    }

    @Test
    void testSearchGroupConversationCaseInsensitive() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Engineering Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        String response = sendGetRequest("/api/search/conversations?query=ENGINEERING", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(1, jsonResponse.get("data").size());
        assertEquals("Engineering Team", jsonResponse.get("data").get(0).get("name").asText());
    }

    @Test
    void testSearchDirectConversationByUsername() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        String response = sendGetRequest("/api/search/conversations?query=bob", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(1, jsonResponse.get("data").size());
        assertEquals("DIRECT", jsonResponse.get("data").get(0).get("type").asText());
        assertEquals("Bob Jones", jsonResponse.get("data").get(0).get("name").asText());
    }

    @Test
    void testSearchDirectConversationByDisplayName() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        String response = sendGetRequest("/api/search/conversations?query=jones", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(1, jsonResponse.get("data").size());
        assertEquals("Bob Jones", jsonResponse.get("data").get(0).get("name").asText());
    }

    @Test
    void testSearchConversationExcludesBlockedUsers() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user1.getId())
                .blockedId(user2.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        String response = sendGetRequest("/api/search/conversations?query=bob", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(0, jsonResponse.get("data").size());
    }

    @Test
    void testSearchConversationExcludesWhenBlockedBy() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        BlockedUser blockedUser = BlockedUser.builder()
                .blockerId(user2.getId())
                .blockedId(user1.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockedUser);

        String response = sendGetRequest("/api/search/conversations?query=bob", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(0, jsonResponse.get("data").size());
    }

    @Test
    void testSearchConversationReturnsEmptyForNoMatches() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        String response = sendGetRequest("/api/search/conversations?query=nonexistent", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(0, jsonResponse.get("data").size());
    }

    @Test
    void testSearchConversationOnlyReturnsUserMemberships() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user2.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        String response = sendGetRequest("/api/search/conversations?query=project", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals(0, jsonResponse.get("data").size());
    }

    @Test
    void testSearchMessagesInConversation() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message1 = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Hello everyone")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message1);

        Message message2 = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Meeting at 3pm")
                .isDeleted(false)
                .createdAt(Instant.now().plusSeconds(1))
                .updatedAt(Instant.now().plusSeconds(1))
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message2);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=hello", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertTrue(messages.isArray());
        assertEquals(1, messages.size());
        assertEquals("Hello everyone", messages.get(0).get("content").asText());
    }

    @Test
    void testSearchMessagesCaseInsensitive() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Important Update")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=IMPORTANT", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertEquals(1, messages.size());
        assertEquals("Important Update", messages.get(0).get("content").asText());
    }

    @Test
    void testSearchMessagesExcludesDeletedMessages() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Deleted message")
                .isDeleted(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=deleted", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertEquals(0, messages.size());
    }

    @Test
    void testSearchMessagesExcludesNonTextMessages() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("IMAGE")
                .content("image.jpg")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=image", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertEquals(0, messages.size());
    }

    @Test
    void testSearchMessagesPagination() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        for (int i = 1; i <= 25; i++) {
            Message message = Message.builder()
                    .conversationId(conversation.getId())
                    .senderId(user1.getId())
                    .type("TEXT")
                    .content("Test message " + i)
                    .isDeleted(false)
                    .createdAt(Instant.now().plusSeconds(i))
                    .updatedAt(Instant.now().plusSeconds(i))
                    .reactions(new HashMap<>())
                    .build();
            messageRepository.save(message);
        }

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=test&page=0&size=10", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode data = jsonResponse.get("data");
        JsonNode messages = data.get("messages");
        assertEquals(10, messages.size());
        assertEquals(0, data.get("page").asInt());
        assertEquals(10, data.get("size").asInt());
        assertTrue(data.get("hasNext").asBoolean());
        assertEquals(25, data.get("totalElements").asLong());
    }

    @Test
    void testSearchMessagesSortedByCreatedAtDesc() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message1 = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("First test message")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message1);

        Message message2 = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Second test message")
                .isDeleted(false)
                .createdAt(Instant.now().plusSeconds(10))
                .updatedAt(Instant.now().plusSeconds(10))
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message2);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=test", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertEquals(2, messages.size());
        assertEquals("Second test message", messages.get(0).get("content").asText());
        assertEquals("First test message", messages.get(1).get("content").asText());
    }

    @Test
    void testSearchMessagesForbiddenForNonMember() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user2.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member2 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user2.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=test", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("FORBIDDEN", jsonResponse.get("code").asText());
    }

    @Test
    void testSearchMessagesReturnsEmptyForNoMatches() throws Exception {
        Conversation conversation = Conversation.builder()
                .type("GROUP")
                .name("Project Team")
                .ownerId(user1.getId())
                .lastMessageAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember member1 = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(user1.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1);

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Hello world")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .reactions(new HashMap<>())
                .build();
        messageRepository.save(message);

        String response = sendGetRequest("/api/search/messages?conversationId=" + conversation.getId() + "&query=nonexistent", user1Token);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        JsonNode messages = jsonResponse.get("data").get("messages");
        assertEquals(0, messages.size());
    }

    private String sendGetRequest(String endpoint, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + endpoint))
                .GET();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
