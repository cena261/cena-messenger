package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.DeleteMessageRequest;
import com.cena.chat_app.dto.request.EditMessageRequest;
import com.cena.chat_app.dto.response.MessageUpdateEventResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.Message;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
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
class MessageEditDeleteTest {

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
    private Message textMessage;
    private Message mediaMessage;
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

        textMessage = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Original text message")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        textMessage = messageRepository.save(textMessage);

        mediaMessage = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("IMAGE")
                .content("Image description")
                .mediaUrl("https://example.com/image.jpg")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        mediaMessage = messageRepository.save(mediaMessage);

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
    void testEditMessageByOriginalSenderSucceeds() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(textMessage.getId())
                .content("Edited message content")
                .build();

        ApiResponse<MessageUpdateEventResponse> response = messageService.editMessage(request);

        assertEquals("success", response.getStatus());

        MessageUpdateEventResponse data = response.getData();
        assertNotNull(data);
        assertEquals("EDIT", data.getAction());
        assertEquals(textMessage.getId(), data.getMessageId());
        assertEquals(conversation.getId(), data.getConversationId());
        assertEquals(user1.getId(), data.getSenderId());
        assertEquals("Edited message content", data.getContent());
        assertFalse(data.getIsDeleted());

        Message updatedMessage = messageRepository.findById(textMessage.getId()).orElseThrow();
        assertEquals("Edited message content", updatedMessage.getContent());
        assertNotEquals(textMessage.getUpdatedAt(), updatedMessage.getUpdatedAt());
    }

    @Test
    void testEditMessageByNonSenderFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user2.getId(), null, Collections.emptyList())
        );

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(textMessage.getId())
                .content("Edited by wrong user")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.editMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_EDIT_DENIED, exception.getErrorCode());
    }

    @Test
    void testEditDeletedMessageFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        textMessage.setDeleted(true);
        textMessage.setContent(null);
        messageRepository.save(textMessage);

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(textMessage.getId())
                .content("Try to edit deleted message")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.editMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_ALREADY_DELETED, exception.getErrorCode());
    }

    @Test
    void testEditNonTextMessageFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(mediaMessage.getId())
                .content("Try to edit media message")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.editMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_NOT_EDITABLE, exception.getErrorCode());
    }

    @Test
    void testDeleteMessageByOriginalSenderSucceeds() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(textMessage.getId())
                .build();

        ApiResponse<MessageUpdateEventResponse> response = messageService.deleteMessage(request);

        assertEquals("success", response.getStatus());

        MessageUpdateEventResponse data = response.getData();
        assertNotNull(data);
        assertEquals("DELETE", data.getAction());
        assertEquals(textMessage.getId(), data.getMessageId());
        assertEquals(conversation.getId(), data.getConversationId());
        assertEquals(user1.getId(), data.getSenderId());
        assertNull(data.getContent());
        assertTrue(data.getIsDeleted());

        Message deletedMessage = messageRepository.findById(textMessage.getId()).orElseThrow();
        assertTrue(deletedMessage.isDeleted());
        assertNull(deletedMessage.getContent());
        assertNotEquals(textMessage.getUpdatedAt(), deletedMessage.getUpdatedAt());
    }

    @Test
    void testDeleteMessageByNonSenderFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user2.getId(), null, Collections.emptyList())
        );

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(textMessage.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.deleteMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_DELETE_DENIED, exception.getErrorCode());
    }

    @Test
    void testDeleteAlreadyDeletedMessageFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        textMessage.setDeleted(true);
        textMessage.setContent(null);
        messageRepository.save(textMessage);

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(textMessage.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.deleteMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_ALREADY_DELETED, exception.getErrorCode());
    }

    @Test
    void testEditEventDeliveredViaWebSocket() throws Exception {
        BlockingQueue<MessageUpdateEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/message-updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageUpdateEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageUpdateEventResponse) {
                    receivedEvents.add((MessageUpdateEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(textMessage.getId())
                .content("Edited via WebSocket test")
                .build();

        messageService.editMessage(request);

        MessageUpdateEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive edit event");
        assertEquals("EDIT", event.getAction());
        assertEquals(textMessage.getId(), event.getMessageId());
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(user1.getId(), event.getSenderId());
        assertEquals("Edited via WebSocket test", event.getContent());
        assertFalse(event.getIsDeleted());

        session.disconnect();
    }

    @Test
    void testDeleteEventDeliveredViaWebSocket() throws Exception {
        BlockingQueue<MessageUpdateEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/message-updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageUpdateEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageUpdateEventResponse) {
                    receivedEvents.add((MessageUpdateEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(textMessage.getId())
                .build();

        messageService.deleteMessage(request);

        MessageUpdateEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Should receive delete event");
        assertEquals("DELETE", event.getAction());
        assertEquals(textMessage.getId(), event.getMessageId());
        assertEquals(conversation.getId(), event.getConversationId());
        assertEquals(user1.getId(), event.getSenderId());
        assertNull(event.getContent());
        assertTrue(event.getIsDeleted());

        session.disconnect();
    }

    @Test
    void testUserDoesNotReceiveOwnEditEvent() throws Exception {
        BlockingQueue<MessageUpdateEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/message-updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageUpdateEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageUpdateEventResponse) {
                    receivedEvents.add((MessageUpdateEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(textMessage.getId())
                .content("Self edit test")
                .build();

        messageService.editMessage(request);

        MessageUpdateEventResponse event = receivedEvents.poll(3, TimeUnit.SECONDS);

        assertNull(event, "User should not receive their own edit event");

        session.disconnect();
    }

    @Test
    void testUserDoesNotReceiveOwnDeleteEvent() throws Exception {
        BlockingQueue<MessageUpdateEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user1Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/message-updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageUpdateEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageUpdateEventResponse) {
                    receivedEvents.add((MessageUpdateEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(textMessage.getId())
                .build();

        messageService.deleteMessage(request);

        MessageUpdateEventResponse event = receivedEvents.poll(3, TimeUnit.SECONDS);

        assertNull(event, "User should not receive their own delete event");

        session.disconnect();
    }
}
