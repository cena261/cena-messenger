package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class RealtimeMessageFlowTest {

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

    private User testUser;
    private Conversation testConversation;
    private String accessToken;

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
                .build();
        testConversation = conversationRepository.save(testConversation);

        ConversationMember member = ConversationMember.builder()
                .conversationId(testConversation.getId())
                .userId(testUser.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member);

        accessToken = jwtTokenProvider.generateAccessToken(testUser.getId());
    }

    @AfterEach
    void tearDown() {
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testMessageFlowFromRestToWebSocket() throws Exception {
        BlockingQueue<MessageResponse> receivedMessages = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + accessToken;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/conversation." + testConversation.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageResponse) {
                    receivedMessages.add((MessageResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(testConversation.getId())
                .content("Test message")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);

        String response = sendRestRequest(requestBody);

        assertNotNull(response);

        MessageResponse receivedMessage = receivedMessages.poll(10, TimeUnit.SECONDS);

        assertNotNull(receivedMessage, "Should receive message via WebSocket");
        assertEquals("Test message", receivedMessage.getContent());
        assertEquals(testConversation.getId(), receivedMessage.getConversationId());
        assertEquals(testUser.getId(), receivedMessage.getSenderId());

        session.disconnect();
    }

    private String sendRestRequest(String requestBody) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/messages"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
