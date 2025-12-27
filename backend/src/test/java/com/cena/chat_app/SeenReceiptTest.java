package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.SeenEventResponse;
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
class SeenReceiptTest {

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

    private User sender;
    private User recipient;
    private Conversation conversation;
    private String senderToken;
    private String recipientToken;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .username("sender")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Sender User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        sender = userRepository.save(sender);

        recipient = User.builder()
                .username("recipient")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Recipient User")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        recipient = userRepository.save(recipient);

        conversation = Conversation.builder()
                .type("DIRECT")
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        ConversationMember senderMember = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(sender.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(senderMember);

        ConversationMember recipientMember = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(recipient.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(recipientMember);

        senderToken = jwtTokenProvider.generateAccessToken(sender.getId());
        recipientToken = jwtTokenProvider.generateAccessToken(recipient.getId());
    }

    @AfterEach
    void tearDown() {
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testLastReadMessageIdUpdatedWhenMarkingAsRead() throws Exception {
        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Test message")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        sendRestRequest(requestBody, senderToken);

        Thread.sleep(500);

        markConversationAsRead(conversation.getId(), recipientToken);

        Thread.sleep(500);

        ConversationMember recipientMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversation.getId(), recipient.getId())
                .orElseThrow();

        assertNotNull(recipientMember.getLastReadMessageId());

        Conversation updatedConversation = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(updatedConversation.getLastMessageId(), recipientMember.getLastReadMessageId());
    }

    @Test
    void testSeenEventDeliveredViaWebSocket() throws Exception {
        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Test message")
                .build();

        String requestBody = objectMapper.writeValueAsString(request);
        sendRestRequest(requestBody, senderToken);

        Thread.sleep(500);

        BlockingQueue<SeenEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + senderToken;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/seen", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SeenEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof SeenEventResponse) {
                    receivedEvents.add((SeenEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        markConversationAsRead(conversation.getId(), recipientToken);

        SeenEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive seen event via WebSocket");
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(recipient.getId(), event.getUserId());
        assertNotNull(event.getLastReadMessageId());

        session.disconnect();
    }

    private void sendRestRequest(String requestBody, String token) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/messages"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private void markConversationAsRead(String conversationId, String token) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/conversations/" + conversationId + "/read"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();

        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
