package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.request.*;
import com.cena.chat_app.dto.response.GroupEventResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.exception.AppException;
import com.cena.chat_app.exception.ErrorCode;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.UserRepository;
import com.cena.chat_app.security.JwtTokenProvider;
import com.cena.chat_app.service.GroupManagementService;
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
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestRedisConfiguration.class, TestMongoDBConfiguration.class })
class GroupManagementTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;
    private User admin;
    private User member;
    private User outsider;
    private Conversation groupConversation;
    private Conversation directConversation;
    private String ownerToken;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Group Owner")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        owner = userRepository.save(owner);

        admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Group Admin")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        admin = userRepository.save(admin);

        member = User.builder()
                .username("member")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Group Member")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        member = userRepository.save(member);

        outsider = User.builder()
                .username("outsider")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Outsider")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        outsider = userRepository.save(outsider);

        groupConversation = Conversation.builder()
                .type("GROUP")
                .name("Test Group")
                .ownerId(owner.getId())
                .createdAt(Instant.now())
                .build();
        groupConversation = conversationRepository.save(groupConversation);

        directConversation = Conversation.builder()
                .type("DIRECT")
                .createdAt(Instant.now())
                .build();
        directConversation = conversationRepository.save(directConversation);

        ConversationMember ownerMember = ConversationMember.builder()
                .conversationId(groupConversation.getId())
                .userId(owner.getId())
                .role("OWNER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(ownerMember);

        ConversationMember adminMember = ConversationMember.builder()
                .conversationId(groupConversation.getId())
                .userId(admin.getId())
                .role("ADMIN")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(adminMember);

        ConversationMember regularMember = ConversationMember.builder()
                .conversationId(groupConversation.getId())
                .userId(member.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(regularMember);

        ownerToken = jwtTokenProvider.generateAccessToken(owner.getId());
        adminToken = jwtTokenProvider.generateAccessToken(admin.getId());
        memberToken = jwtTokenProvider.generateAccessToken(member.getId());
    }

    @AfterEach
    void tearDown() {
        conversationMemberRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testMemberSuccessfullyLeavesGroup() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getId(), null, Collections.emptyList())
        );

        LeaveGroupRequest request = LeaveGroupRequest.builder()
                .conversationId(groupConversation.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.leaveGroup(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("MEMBER_LEFT", event.getEventType());
        assertEquals(groupConversation.getId(), event.getConversationId());
        assertEquals(member.getId(), event.getActorId());
        assertEquals(member.getId(), event.getTargetUserId());

        Optional<ConversationMember> memberRecord = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), member.getId());
        assertFalse(memberRecord.isPresent());
    }

    @Test
    void testAdminSuccessfullyLeavesGroup() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        LeaveGroupRequest request = LeaveGroupRequest.builder()
                .conversationId(groupConversation.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.leaveGroup(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("MEMBER_LEFT", event.getEventType());
        assertEquals(admin.getId(), event.getActorId());

        Optional<ConversationMember> adminRecord = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), admin.getId());
        assertFalse(adminRecord.isPresent());
    }

    @Test
    void testOwnerCannotLeaveGroup() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        LeaveGroupRequest request = LeaveGroupRequest.builder()
                .conversationId(groupConversation.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.leaveGroup(request);
        });

        assertEquals(ErrorCode.OWNER_CANNOT_LEAVE, exception.getErrorCode());
    }

    @Test
    void testLeaveGroupFailsForDirectConversation() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getId(), null, Collections.emptyList())
        );

        ConversationMember directMember = ConversationMember.builder()
                .conversationId(directConversation.getId())
                .userId(member.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(directMember);

        LeaveGroupRequest request = LeaveGroupRequest.builder()
                .conversationId(directConversation.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.leaveGroup(request);
        });

        assertEquals(ErrorCode.NOT_GROUP_CONVERSATION, exception.getErrorCode());
    }

    @Test
    void testOwnerKicksMemberSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(member.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.kickMember(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("MEMBER_KICKED", event.getEventType());
        assertEquals(owner.getId(), event.getActorId());
        assertEquals(member.getId(), event.getTargetUserId());

        Optional<ConversationMember> memberRecord = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), member.getId());
        assertFalse(memberRecord.isPresent());
    }

    @Test
    void testOwnerKicksAdminSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(admin.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.kickMember(request);

        assertEquals("success", response.getStatus());
        assertEquals("MEMBER_KICKED", response.getData().getEventType());

        Optional<ConversationMember> adminRecord = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), admin.getId());
        assertFalse(adminRecord.isPresent());
    }

    @Test
    void testAdminKicksMemberSuccessfully() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(member.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.kickMember(request);

        assertEquals("success", response.getStatus());

        Optional<ConversationMember> memberRecord = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), member.getId());
        assertFalse(memberRecord.isPresent());
    }

    @Test
    void testAdminCannotKickAnotherAdmin() {
        User anotherAdmin = User.builder()
                .username("admin2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Another Admin")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        anotherAdmin = userRepository.save(anotherAdmin);

        ConversationMember anotherAdminMember = ConversationMember.builder()
                .conversationId(groupConversation.getId())
                .userId(anotherAdmin.getId())
                .role("ADMIN")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(anotherAdminMember);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(anotherAdmin.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.kickMember(request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, exception.getErrorCode());
    }

    @Test
    void testMemberCannotKickAnyone() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getId(), null, Collections.emptyList())
        );

        User anotherMember = User.builder()
                .username("member2")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Another Member")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        anotherMember = userRepository.save(anotherMember);

        ConversationMember anotherMemberRecord = ConversationMember.builder()
                .conversationId(groupConversation.getId())
                .userId(anotherMember.getId())
                .role("MEMBER")
                .canSendMessage(true)
                .joinedAt(Instant.now())
                .unreadCount(0)
                .build();
        conversationMemberRepository.save(anotherMemberRecord);

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(anotherMember.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.kickMember(request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, exception.getErrorCode());
    }

    @Test
    void testCannotKickOwner() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(owner.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.kickMember(request);
        });

        assertEquals(ErrorCode.CANNOT_KICK_OWNER, exception.getErrorCode());
    }

    @Test
    void testCannotKickSelf() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        KickMemberRequest request = KickMemberRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(owner.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.kickMember(request);
        });

        assertEquals(ErrorCode.CANNOT_KICK_SELF, exception.getErrorCode());
    }

    @Test
    void testOwnerPromotesMemberToAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(member.getId())
                .newRole("ADMIN")
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.changeRole(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("ROLE_CHANGED", event.getEventType());
        assertEquals(owner.getId(), event.getActorId());
        assertEquals(member.getId(), event.getTargetUserId());
        assertEquals("MEMBER", event.getPreviousRole());
        assertEquals("ADMIN", event.getNewRole());

        ConversationMember updatedMember = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), member.getId())
                .orElseThrow();
        assertEquals("ADMIN", updatedMember.getRole());
    }

    @Test
    void testOwnerDemotesAdminToMember() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(admin.getId())
                .newRole("MEMBER")
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.changeRole(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("ADMIN", event.getPreviousRole());
        assertEquals("MEMBER", event.getNewRole());

        ConversationMember updatedAdmin = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), admin.getId())
                .orElseThrow();
        assertEquals("MEMBER", updatedAdmin.getRole());
    }

    @Test
    void testOwnerCannotChangeOwnRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(owner.getId())
                .newRole("ADMIN")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.changeRole(request);
        });

        assertEquals(ErrorCode.CANNOT_CHANGE_OWN_ROLE, exception.getErrorCode());
    }

    @Test
    void testAdminCannotChangeRoles() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(member.getId())
                .newRole("ADMIN")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.changeRole(request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, exception.getErrorCode());
    }

    @Test
    void testCannotChangeOwnerRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .conversationId(groupConversation.getId())
                .userId(admin.getId())
                .newRole("OWNER")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.changeRole(request);
        });

        assertEquals(ErrorCode.INVALID_ROLE_CHANGE, exception.getErrorCode());
    }

    @Test
    void testOwnerTransfersOwnershipToMember() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        TransferOwnershipRequest request = TransferOwnershipRequest.builder()
                .conversationId(groupConversation.getId())
                .newOwnerId(member.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.transferOwnership(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("OWNERSHIP_TRANSFERRED", event.getEventType());
        assertEquals(owner.getId(), event.getActorId());
        assertEquals(member.getId(), event.getTargetUserId());

        ConversationMember previousOwner = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), owner.getId())
                .orElseThrow();
        assertEquals("ADMIN", previousOwner.getRole());

        ConversationMember newOwner = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), member.getId())
                .orElseThrow();
        assertEquals("OWNER", newOwner.getRole());

        Conversation updatedConversation = conversationRepository.findById(groupConversation.getId()).orElseThrow();
        assertEquals(member.getId(), updatedConversation.getOwnerId());
    }

    @Test
    void testOwnerTransfersOwnershipToAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        TransferOwnershipRequest request = TransferOwnershipRequest.builder()
                .conversationId(groupConversation.getId())
                .newOwnerId(admin.getId())
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.transferOwnership(request);

        assertEquals("success", response.getStatus());

        ConversationMember newOwner = conversationMemberRepository
                .findByConversationIdAndUserId(groupConversation.getId(), admin.getId())
                .orElseThrow();
        assertEquals("OWNER", newOwner.getRole());
    }

    @Test
    void testNonOwnerCannotTransferOwnership() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        TransferOwnershipRequest request = TransferOwnershipRequest.builder()
                .conversationId(groupConversation.getId())
                .newOwnerId(member.getId())
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.transferOwnership(request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, exception.getErrorCode());
    }

    @Test
    void testOwnerUpdatesGroupName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        UpdateGroupInfoRequest request = UpdateGroupInfoRequest.builder()
                .conversationId(groupConversation.getId())
                .name("Updated Group Name")
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.updateGroupInfo(request);

        assertEquals("success", response.getStatus());
        GroupEventResponse event = response.getData();
        assertEquals("GROUP_INFO_UPDATED", event.getEventType());
        assertEquals("Updated Group Name", event.getGroupName());

        Conversation updated = conversationRepository.findById(groupConversation.getId()).orElseThrow();
        assertEquals("Updated Group Name", updated.getName());
    }

    @Test
    void testAdminUpdatesGroupAvatar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList())
        );

        UpdateGroupInfoRequest request = UpdateGroupInfoRequest.builder()
                .conversationId(groupConversation.getId())
                .avatarUrl("https://example.com/new-avatar.jpg")
                .build();

        ApiResponse<GroupEventResponse> response = groupManagementService.updateGroupInfo(request);

        assertEquals("success", response.getStatus());
        assertEquals("https://example.com/new-avatar.jpg", response.getData().getGroupAvatarUrl());

        Conversation updated = conversationRepository.findById(groupConversation.getId()).orElseThrow();
        assertEquals("https://example.com/new-avatar.jpg", updated.getAvatarUrl());
    }

    @Test
    void testMemberCannotUpdateGroupInfo() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getId(), null, Collections.emptyList())
        );

        UpdateGroupInfoRequest request = UpdateGroupInfoRequest.builder()
                .conversationId(groupConversation.getId())
                .name("Hacked Name")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.updateGroupInfo(request);
        });

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, exception.getErrorCode());
    }

    @Test
    void testUpdateGroupInfoFailsForDirectConversation() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        UpdateGroupInfoRequest request = UpdateGroupInfoRequest.builder()
                .conversationId(directConversation.getId())
                .name("Should Fail")
                .build();

        AppException exception = assertThrows(AppException.class, () -> {
            groupManagementService.updateGroupInfo(request);
        });

        assertEquals(ErrorCode.NOT_GROUP_CONVERSATION, exception.getErrorCode());
    }

    @Test
    void testGroupEventDeliveredViaWebSocket() throws Exception {
        BlockingQueue<GroupEventResponse> receivedEvents = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        stompClient.setMessageConverter(new JacksonJsonMessageConverter(mapper));

        String wsUrl = "ws://localhost:" + port + "/ws?token=" + memberToken;
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/group-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GroupEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof GroupEventResponse) {
                    receivedEvents.add((GroupEventResponse) payload);
                }
            }
        });

        Thread.sleep(500);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getId(), null, Collections.emptyList())
        );

        UpdateGroupInfoRequest request = UpdateGroupInfoRequest.builder()
                .conversationId(groupConversation.getId())
                .name("New Group Name")
                .build();

        groupManagementService.updateGroupInfo(request);

        GroupEventResponse event = receivedEvents.poll(10, TimeUnit.SECONDS);

        assertNotNull(event, "Member should receive group event");
        assertEquals("GROUP_INFO_UPDATED", event.getEventType());
        assertEquals(groupConversation.getId(), event.getConversationId());
        assertEquals(owner.getId(), event.getActorId());
        assertEquals("New Group Name", event.getGroupName());

        session.disconnect();
    }
}
