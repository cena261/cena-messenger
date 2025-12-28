package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.RegisterRequest;
import com.cena.chat_app.entity.BlockedUser;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.BlockedUserRepository;
import com.cena.chat_app.repository.UserRepository;
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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class UserIdentityAndSearchTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.cena.chat_app.service.RedisRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        blockedUserRepository.deleteAll();
        rateLimitService.resetLimit("127.0.0.1", "auth_register");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        blockedUserRepository.deleteAll();
        rateLimitService.resetLimit("127.0.0.1", "auth_register");
    }

    @Test
    void testRegistrationWithUsernameAndEmailOnly() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .password("password123")
                .displayName("Test User")
                .email("test@example.com")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("SUCCESS", jsonResponse.get("code").asText());
        assertNotNull(jsonResponse.get("data").get("accessToken"));

        User user = userRepository.findByUsername("testuser").orElse(null);
        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertNull(user.getPhone());
    }

    @Test
    void testRegistrationWithUsernameEmailAndPhone() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .password("password123")
                .displayName("Test User")
                .email("test@example.com")
                .phone("+1234567890")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("SUCCESS", jsonResponse.get("code").asText());

        User user = userRepository.findByUsername("testuser").orElse(null);
        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertEquals("+1234567890", user.getPhone());
    }

    @Test
    void testRejectionOfDuplicateUsername() throws Exception {
        User existingUser = User.builder()
                .username("existinguser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("existing@example.com")
                .displayName("Existing User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = RegisterRequest.builder()
                .username("existinguser")
                .password("password123")
                .displayName("New User")
                .email("new@example.com")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("USERNAME_EXISTS", jsonResponse.get("code").asText());
    }

    @Test
    void testRejectionOfDuplicateEmail() throws Exception {
        User existingUser = User.builder()
                .username("existinguser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("duplicate@example.com")
                .displayName("Existing User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .displayName("New User")
                .email("duplicate@example.com")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("EMAIL_EXISTS", jsonResponse.get("code").asText());
    }

    @Test
    void testRejectionOfDuplicatePhone() throws Exception {
        User existingUser = User.builder()
                .username("existinguser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("existing@example.com")
                .phone("+9876543210")
                .displayName("Existing User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .displayName("New User")
                .email("new@example.com")
                .phone("+9876543210")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("error", jsonResponse.get("status").asText());
        assertEquals("PHONE_EXISTS", jsonResponse.get("code").asText());
    }

    @Test
    void testSearchByUsername() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("target@example.com")
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=targetuser", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertNotNull(jsonResponse.get("data"));
        assertEquals("targetuser", jsonResponse.get("data").get("username").asText());
        assertEquals("Target User", jsonResponse.get("data").get("displayName").asText());
    }

    @Test
    void testSearchByEmail() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("findme@example.com")
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=findme@example.com", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertNotNull(jsonResponse.get("data"));
        assertEquals("targetuser", jsonResponse.get("data").get("username").asText());
    }

    @Test
    void testSearchByPhone() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("target@example.com")
                .phone("+1112223333")
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=%2B1112223333", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertNotNull(jsonResponse.get("data"));
        assertEquals("targetuser", jsonResponse.get("data").get("username").asText());
    }

    @Test
    void testBlockedUsersExcludedFromSearch() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User blockedUser = User.builder()
                .username("blockeduser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("blocked@example.com")
                .displayName("Blocked User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        blockedUser = userRepository.save(blockedUser);

        BlockedUser blockRelation = BlockedUser.builder()
                .blockerId(searcherUser.getId())
                .blockedId(blockedUser.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockRelation);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=blockeduser", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertTrue(jsonResponse.get("data") == null || jsonResponse.get("data").isNull());
    }

    @Test
    void testUserBlockedByTargetExcludedFromSearch() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User blockerUser = User.builder()
                .username("blockeruser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("blocker@example.com")
                .displayName("Blocker User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        blockerUser = userRepository.save(blockerUser);

        BlockedUser blockRelation = BlockedUser.builder()
                .blockerId(blockerUser.getId())
                .blockedId(searcherUser.getId())
                .createdAt(Instant.now())
                .build();
        blockedUserRepository.save(blockRelation);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=blockeruser", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertTrue(jsonResponse.get("data") == null || jsonResponse.get("data").isNull());
    }

    @Test
    void testEmptySearchResults() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=nonexistent", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertTrue(jsonResponse.get("data") == null || jsonResponse.get("data").isNull());
    }

    @Test
    void testUsernameNormalizationDuringRegistration() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("  TestUser  ")
                .password("password123")
                .displayName("Test User")
                .email("  Test@Example.COM  ")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());

        User user = userRepository.findByUsername("testuser").orElse(null);
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
    }

    @Test
    void testPhoneNormalizationDuringRegistration() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("phoneuser")
                .password("password123")
                .displayName("Phone User")
                .email("phone@example.com")
                .phone("  +1234567890  ")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        String response = sendPostRequest("/api/auth/register", requestBody, null);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());

        User user = userRepository.findByUsername("phoneuser").orElse(null);
        assertNotNull(user);
        assertEquals("+1234567890", user.getPhone());
    }

    @Test
    void testSearchWithDifferentCase() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("target@example.com")
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=TARGETUSER", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertNotNull(jsonResponse.get("data"));
        assertEquals("targetuser", jsonResponse.get("data").get("username").asText());
    }

    @Test
    void testSearchByEmailWithDifferentCase() throws Exception {
        User searcherUser = User.builder()
                .username("searcher")
                .passwordHash(passwordEncoder.encode("password"))
                .email("searcher@example.com")
                .displayName("Searcher User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        searcherUser = userRepository.save(searcherUser);

        User targetUser = User.builder()
                .username("targetuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("findme@example.com")
                .displayName("Target User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        targetUser = userRepository.save(targetUser);

        String accessToken = jwtTokenProvider.generateAccessToken(searcherUser.getId());

        String response = sendGetRequest("/api/users/search?query=FindMe@EXAMPLE.COM", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertNotNull(jsonResponse.get("data"));
        assertEquals("targetuser", jsonResponse.get("data").get("username").asText());
    }

    @Test
    void testSelfSearchReturnsNoUserFound() throws Exception {
        User user = User.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode("password"))
                .email("test@example.com")
                .displayName("Test User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());

        String response = sendGetRequest("/api/users/search?query=testuser", accessToken);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertEquals("success", jsonResponse.get("status").asText());
        assertEquals("No user found", jsonResponse.get("message").asText());
        assertTrue(jsonResponse.get("data") == null || jsonResponse.get("data").isNull());
    }

    private String sendPostRequest(String endpoint, String body, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String sendGetRequest(String endpoint, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + endpoint))
                .header("Content-Type", "application/json")
                .GET();

        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
