package com.cena.chat_app;

import com.cena.chat_app.ChatAppApplication;
import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.CreateMediaMessageRequest;
import com.cena.chat_app.dto.request.EditMessageRequest;
import com.cena.chat_app.dto.request.RequestPresignedUrlRequest;
import com.cena.chat_app.dto.response.MessageResponse;
import com.cena.chat_app.dto.response.PresignedUrlResponse;
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
import com.cena.chat_app.service.MediaService;
import com.cena.chat_app.service.MessageService;
import com.cena.chat_app.service.MinioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ChatAppApplication.class)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class, MediaMessageTest.TestConfig.class })
class MediaMessageTest {

    @Configuration
    static class TestConfig {
        @Bean
        @Primary
        public MinioService minioService() {
            return Mockito.mock(MinioService.class);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MinioService minioService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private Conversation conversation;

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

        when(minioService.generatePresignedUploadUrl(anyString(), anyString()))
                .thenReturn("http://localhost:9000/chat-media-test/test-file.jpg?X-Amz-Signature=test");
        when(minioService.extractObjectKeyFromUrl(anyString()))
                .thenReturn("test-file.jpg");
        when(minioService.getObjectUrl(anyString()))
                .thenReturn("http://localhost:9000/chat-media-test/test-file.jpg");
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testRequestPresignedUrlSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        RequestPresignedUrlRequest request = RequestPresignedUrlRequest.builder()
                .conversationId(conversation.getId())
                .fileName("test-image.jpg")
                .fileSize(1024L * 1024L)
                .mimeType("image/jpeg")
                .build();

        ApiResponse<PresignedUrlResponse> response = mediaService.requestPresignedUrl(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertNotNull(response.getData().getPresignedUrl());
        assertNotNull(response.getData().getFileKey());
        assertNotNull(response.getData().getExpiresAt());
    }

    @Test
    void testRequestPresignedUrlFailsForNonMember() {
        User outsider = User.builder()
                .username("outsider")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Outsider")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        outsider = userRepository.save(outsider);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(outsider.getId(), null, Collections.emptyList())
        );

        RequestPresignedUrlRequest request = RequestPresignedUrlRequest.builder()
                .conversationId(conversation.getId())
                .fileName("test-image.jpg")
                .fileSize(1024L * 1024L)
                .mimeType("image/jpeg")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            mediaService.requestPresignedUrl(request);
        });

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void testRequestPresignedUrlFailsForLargeFile() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        RequestPresignedUrlRequest request = RequestPresignedUrlRequest.builder()
                .conversationId(conversation.getId())
                .fileName("large-file.jpg")
                .fileSize(60L * 1024L * 1024L)
                .mimeType("image/jpeg")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            mediaService.requestPresignedUrl(request);
        });

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("File size exceeds"));
    }

    @Test
    void testRequestPresignedUrlFailsForInvalidMimeType() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        RequestPresignedUrlRequest request = RequestPresignedUrlRequest.builder()
                .conversationId(conversation.getId())
                .fileName("test.exe")
                .fileSize(1024L * 1024L)
                .mimeType("application/x-msdownload")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            mediaService.requestPresignedUrl(request);
        });

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("File type not allowed"));
    }

    @Test
    void testCreateImageMessageSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test-image.jpg");
        metadata.put("fileSize", 1024000L);
        metadata.put("mimeType", "image/jpeg");
        metadata.put("width", 1920);
        metadata.put("height", 1080);

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("test-file.jpg")
                .type("IMAGE")
                .mediaMetadata(metadata)
                .build();

        ApiResponse<MessageResponse> response = mediaService.createMediaMessage(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertEquals("IMAGE", response.getData().getType());
        assertNull(response.getData().getContent());
        assertNotNull(response.getData().getMediaUrl());
        assertNotNull(response.getData().getMediaMetadata());
        assertEquals("test-image.jpg", response.getData().getMediaMetadata().get("fileName"));
    }

    @Test
    void testCreateVideoMessageSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test-video.mp4");
        metadata.put("fileSize", 5120000L);
        metadata.put("mimeType", "video/mp4");
        metadata.put("duration", 120);

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("test-video.mp4")
                .type("VIDEO")
                .mediaMetadata(metadata)
                .build();

        ApiResponse<MessageResponse> response = mediaService.createMediaMessage(request);

        assertEquals("success", response.getStatus());
        assertEquals("VIDEO", response.getData().getType());
        assertNull(response.getData().getContent());
    }

    @Test
    void testCreateAudioMessageSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test-audio.mp3");
        metadata.put("fileSize", 2048000L);
        metadata.put("mimeType", "audio/mpeg");
        metadata.put("duration", 180);

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("test-audio.mp3")
                .type("AUDIO")
                .mediaMetadata(metadata)
                .build();

        ApiResponse<MessageResponse> response = mediaService.createMediaMessage(request);

        assertEquals("success", response.getStatus());
        assertEquals("AUDIO", response.getData().getType());
    }

    @Test
    void testCreateFileMessageSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "document.pdf");
        metadata.put("fileSize", 512000L);
        metadata.put("mimeType", "application/pdf");

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("document.pdf")
                .type("FILE")
                .mediaMetadata(metadata)
                .build();

        ApiResponse<MessageResponse> response = mediaService.createMediaMessage(request);

        assertEquals("success", response.getStatus());
        assertEquals("FILE", response.getData().getType());
    }

    @Test
    void testMediaMessageWithReplySuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Message originalMessage = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user2.getId())
                .type("TEXT")
                .content("Original message")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        originalMessage = messageRepository.save(originalMessage);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "reply-image.jpg");
        metadata.put("fileSize", 1024000L);
        metadata.put("mimeType", "image/jpeg");

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("reply-image.jpg")
                .type("IMAGE")
                .mediaMetadata(metadata)
                .replyTo(originalMessage.getId())
                .build();

        ApiResponse<MessageResponse> response = mediaService.createMediaMessage(request);

        assertEquals("success", response.getStatus());
        assertEquals(originalMessage.getId(), response.getData().getReplyTo());
    }

    @Test
    void testEditingMediaMessageFails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user1.getId(), null, Collections.emptyList())
        );

        Message mediaMessage = Message.builder()
                .conversationId(conversation.getId())
                .senderId(user1.getId())
                .type("IMAGE")
                .content(null)
                .mediaUrl("http://localhost:9000/chat-media-test/image.jpg")
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        mediaMessage = messageRepository.save(mediaMessage);

        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(mediaMessage.getId())
                .content("Cannot edit media message")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            messageService.editMessage(request);
        });

        assertEquals(ErrorCode.MESSAGE_NOT_EDITABLE, exception.getErrorCode());
    }

    @Test
    void testCreateMediaMessageFailsForNonMember() {
        User outsider = User.builder()
                .username("outsider2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Outsider 2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        outsider = userRepository.save(outsider);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(outsider.getId(), null, Collections.emptyList())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test.jpg");

        CreateMediaMessageRequest request = CreateMediaMessageRequest.builder()
                .conversationId(conversation.getId())
                .fileKey("test.jpg")
                .type("IMAGE")
                .mediaMetadata(metadata)
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            mediaService.createMediaMessage(request);
        });

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.getErrorCode());
    }
}
