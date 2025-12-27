package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.request.TypingRequest;
import com.cena.chat_app.dto.response.TypingEventResponse;
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
class TypingIndicatorTest {

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

    private User user1;
    private User user2;
    private Conversation conversation;
    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .username("user1")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("User 1")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .username("user2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("User 2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user2 = userRepository.save(user2);

        conversation = Conversation.builder()
                .type("DIRECT")
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

        user1Token = jwtTokenProvider.generateAccessToken(user1.getId());
        user2Token = jwtTokenProvider.generateAccessToken(user2.getId());
    }

    @AfterEach
    void tearDown() {
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testTypingStartEventDelivered() throws Exception {
        BlockingQueue<TypingEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TypingEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof TypingEventResponse) {
                    receivedEvents.add((TypingEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        String user1WsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession user1Session = stompClient.connectAsync(user1WsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        TypingRequest typingRequest = TypingRequest.builder()
                .conversationId(conversation.getId())
                .build();

        user1Session.send("/app/typing/start", typingRequest);

        TypingEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive typing start event");
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(user1.getId(), event.getUserId());
        assertTrue(event.isTyping());

        session.disconnect();
        user1Session.disconnect();
    }

    @Test
    void testTypingStopEventDelivered() throws Exception {
        BlockingQueue<TypingEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TypingEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof TypingEventResponse) {
                    receivedEvents.add((TypingEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        String user1WsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession user1Session = stompClient.connectAsync(user1WsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        TypingRequest typingRequest = TypingRequest.builder()
                .conversationId(conversation.getId())
                .build();

        user1Session.send("/app/typing/start", typingRequest);
        receivedEvents.poll(10, TimeUnit.SECONDS);

        user1Session.send("/app/typing/stop", typingRequest);

        TypingEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive typing stop event");
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(user1.getId(), event.getUserId());
        assertFalse(event.isTyping());

        session.disconnect();
        user1Session.disconnect();
    }

    @Test
    void testUserDoesNotReceiveOwnTypingEvent() throws Exception {
        BlockingQueue<TypingEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/typing", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TypingEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof TypingEventResponse) {
                    receivedEvents.add((TypingEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        TypingRequest typingRequest = TypingRequest.builder()
                .conversationId(conversation.getId())
                .build();

        session.send("/app/typing/start", typingRequest);

        TypingEventResponse event = receivedEvents.poll(3, TimeUnit.SECONDS);

        assertNull(event, "User should not receive their own typing event");

        session.disconnect();
    }
}
