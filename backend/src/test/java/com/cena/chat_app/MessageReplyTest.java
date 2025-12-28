package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.SendMessageRequest;
import com.cena.chat_app.dto.response.MessageResponse;
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
class MessageReplyTest {

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
    private Conversation conversation1;
    private Conversation conversation2;
    private Message originalMessage;
    private Message deletedMessage;
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

        conversation1 = Conversation.builder()
                .type("DIRECT")
                .createdAt(Instant.now())
                .build();
        conversation1 = conversationRepository.save(conversation1);

        conversation2 = Conversation.builder()
                .type("DIRECT")
                .createdAt(Instant.now())
                .build();
        conversation2 = conversationRepository.save(conversation2);

        ConversationMember member1Conv1 = ConversationMember.builder()
                .conversationId(conversation1.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1Conv1);

        ConversationMember member2Conv1 = ConversationMember.builder()
                .conversationId(conversation1.getId())
                .userId(user2.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member2Conv1);

        ConversationMember member1Conv2 = ConversationMember.builder()
                .conversationId(conversation2.getId())
                .userId(user1.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(member1Conv2);

        originalMessage = Message.builder()
                .conversationId(conversation1.getId())
                .senderId(user1.getId())
                .type("TEXT")
                .content("Original message")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        originalMessage = messageRepository.save(originalMessage);

        deletedMessage = Message.builder()
                .conversationId(conversation1.getId())
                .senderId(user2.getId())
                .type("TEXT")
                .content(null)
                .isDeleted(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        deletedMessage = messageRepository.save(deletedMessage);

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
    void testReplyMessagePersistsCorrectly() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user2.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("This is a reply")
                .replyTo(originalMessage.getId())
                .build();

        ApiResponse<MessageResponse> response = messageService.sendMessage(request);

        assertEquals("success", response.getStatus());

        MessageResponse data = response.getData();
        assertNotNull(data);
        assertEquals("This is a reply", data.getContent());
        assertEquals(originalMessage.getId(), data.getReplyTo());
        assertEquals(conversation1.getId(), data.getConversationId());
        assertEquals(user2.getId(), data.getSenderId());

        Message savedMessage = messageRepository.findById(data.getId()).orElseThrow();
        assertEquals(originalMessage.getId(), savedMessage.getReplyTo());
        assertEquals("This is a reply", savedMessage.getContent());
    }

    @Test
    void testReplyToNonExistentMessageFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("Reply to non-existent message")
                .replyTo("nonexistent-message-id")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.sendMessage(request);
        });

        assertEquals(ErrorCode.REPLY_MESSAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testReplyToMessageFromDifferentConversationFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation2.getId())
                .content("Reply to message from different conversation")
                .replyTo(originalMessage.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.sendMessage(request);
        });

        assertEquals(ErrorCode.REPLY_MESSAGE_DIFFERENT_CONVERSATION, exception.getErrorCode());
    }

    @Test
    void testReplyToDeletedMessageSucceeds() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("Reply to deleted message")
                .replyTo(deletedMessage.getId())
                .build();

        ApiResponse<MessageResponse> response = messageService.sendMessage(request);

        assertEquals("success", response.getStatus());

        MessageResponse data = response.getData();
        assertNotNull(data);
        assertEquals("Reply to deleted message", data.getContent());
        assertEquals(deletedMessage.getId(), data.getReplyTo());
    }

    @Test
    void testMessageWithoutReplyWorks() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("Message without reply")
                .build();

        ApiResponse<MessageResponse> response = messageService.sendMessage(request);

        assertEquals("success", response.getStatus());

        MessageResponse data = response.getData();
        assertNotNull(data);
        assertEquals("Message without reply", data.getContent());
        assertNull(data.getReplyTo());
    }

    @Test
    void testReplyDeliveredViaWebSocket() throws Exception {
        BlockingQueue<MessageResponse> receivedMessages = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + user2Token;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/conversation." + conversation1.getId(), new StompFrameHandler() {
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

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("Reply via WebSocket")
                .replyTo(originalMessage.getId())
                .build();

        messageService.sendMessage(request);

        MessageResponse receivedMessage = receivedMessages.poll(10, TimeUnit.SECONDS);

        assertNotNull(receivedMessage, "Should receive reply message via WebSocket");
        assertEquals("Reply via WebSocket", receivedMessage.getContent());
        assertEquals(originalMessage.getId(), receivedMessage.getReplyTo());
        assertEquals(conversation1.getId(), receivedMessage.getConversationId());
        assertEquals(user1.getId(), receivedMessage.getSenderId());

        session.disconnect();
    }

    @Test
    void testNestedRepliesWork() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user2.getId(), null, Collections.emptyList())
        );

        SendMessageRequest firstReply = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("First reply")
                .replyTo(originalMessage.getId())
                .build();

        ApiResponse<MessageResponse> firstReplyResponse = messageService.sendMessage(firstReply);
        String firstReplyId = firstReplyResponse.getData().getId();

        SendMessageRequest secondReply = SendMessageRequest.builder()
                .conversationId(conversation1.getId())
                .content("Reply to reply")
                .replyTo(firstReplyId)
                .build();

        ApiResponse<MessageResponse> secondReplyResponse = messageService.sendMessage(secondReply);

        assertEquals("success", secondReplyResponse.getStatus());

        MessageResponse data = secondReplyResponse.getData();
        assertNotNull(data);
        assertEquals("Reply to reply", data.getContent());
        assertEquals(firstReplyId, data.getReplyTo());
    }
}
