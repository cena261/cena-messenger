package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.ReactionRequest;
import com.cena.chat_app.dto.response.ReactionEventResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.Message;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.MessageRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import com.cena.chat_app.service.MessageService;
import tools.jackson.databind.DeserializationFeature;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class ReactionTest {

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
    private MessageService messageService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private Conversation conversation;
    private Message message;
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

        message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Test message")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        message = messageRepository.save(message);

        user1Token = jwtTokenProvider.generateAccessToken(user1.getId());
        user2Token = jwtTokenProvider.generateAccessToken(user2.getId());
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testAddReactionPersistsCorrectly() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        ReactionRequest request = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("❤️")
                .build();

        ApiResponse<ReactionEventResponse> response = messageService.toggleReaction(request);

        assertEquals("success", response.getStatus());

        ReactionEventResponse data = response.getData();
        assertNotNull(data);
        assertTrue(data.isAdded());
        assertEquals("❤️", data.getReactionType());
        assertEquals(user1.getId(), data.getUserId());

        Message updatedMessage = messageRepository.findById(message.getId()).orElseThrow();
        assertNotNull(updatedMessage.getReactions());
        assertEquals("❤️", updatedMessage.getReactions().get(user1.getId()));
    }

    @Test
    void testToggleSameReactionRemovesIt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        ReactionRequest request = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("❤️")
                .build();

        messageService.toggleReaction(request);

        ApiResponse<ReactionEventResponse> response = messageService.toggleReaction(request);

        ReactionEventResponse data = response.getData();
        assertFalse(data.isAdded());

        Message updatedMessage = messageRepository.findById(message.getId()).orElseThrow();
        assertTrue(updatedMessage.getReactions() == null || !updatedMessage.getReactions().containsKey(user1.getId()));
    }

    @Test
    void testDifferentReactionReplacesExisting() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        ReactionRequest request1 = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("❤️")
                .build();

        messageService.toggleReaction(request1);

        ReactionRequest request2 = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("👍")
                .build();

        ApiResponse<ReactionEventResponse> response = messageService.toggleReaction(request2);

        ReactionEventResponse data = response.getData();
        assertTrue(data.isAdded());
        assertEquals("👍", data.getReactionType());

        Message updatedMessage = messageRepository.findById(message.getId()).orElseThrow();
        assertEquals("👍", updatedMessage.getReactions().get(user1.getId()));
        assertEquals(1, updatedMessage.getReactions().size());
    }

    @Test
    void testReactionEventDeliveredViaWebSocket() throws Exception {
        BlockingQueue<ReactionEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/reactions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ReactionEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ReactionEventResponse) {
                    receivedEvents.add((ReactionEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        ReactionRequest request = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("❤️")
                .build();

        messageService.toggleReaction(request);

        ReactionEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive reaction event");
        assertEquals(message.getId(), event.getMessageId());
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(user1.getId(), event.getUserId());
        assertEquals("❤️", event.getReactionType());
        assertTrue(event.isAdded());

        session.disconnect();
    }

    @Test
    void testUserDoesNotReceiveOwnReactionEvent() throws Exception {
        BlockingQueue<ReactionEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/reactions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ReactionEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ReactionEventResponse) {
                    receivedEvents.add((ReactionEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        ReactionRequest request = ReactionRequest.builder()
                .messageId(message.getId())
                .reactionType("❤️")
                .build();

        messageService.toggleReaction(request);

        ReactionEventResponse event = receivedEvents.poll(3, TimeUnit.SECONDS);

        assertNull(event, "User should not receive their own reaction event");

        session.disconnect();
    }
}
