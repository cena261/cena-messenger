package com.cena.chat_app.service;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.dto.response.ConversationSearchResponse;
import com.cena.chat_app.dto.response.MessageSearchResponse;
import com.cena.chat_app.dto.response.PagedMessageSearchResponse;
import com.cena.chat_app.entity.Conversation;
import com.cena.chat_app.entity.ConversationMember;
import com.cena.chat_app.entity.Message;
import com.cena.chat_app.entity.User;
import com.cena.chat_app.repository.BlockedUserRepository;
import com.cena.chat_app.repository.ConversationMemberRepository;
import com.cena.chat_app.repository.ConversationRepository;
import com.cena.chat_app.repository.MessageRepository;
import com.cena.chat_app.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;

    public SearchService(ConversationRepository conversationRepository,
                        ConversationMemberRepository conversationMemberRepository,
                        MessageRepository messageRepository,
                        UserRepository userRepository,
                        BlockedUserRepository blockedUserRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
    }

    public ApiResponse<List<ConversationSearchResponse>> searchConversations(String query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ApiResponse.<List<ConversationSearchResponse>>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        String currentUserId = (String) authentication.getPrincipal();

        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(currentUserId);
        if (memberships.isEmpty()) {
            return ApiResponse.<List<ConversationSearchResponse>>builder()
                .status("success")
                .code("SUCCESS")
                .message("No conversations found")
                .data(new ArrayList<>())
                .build();
        }

        Set<String> conversationIds = memberships.stream()
            .map(ConversationMember::getConversationId)
            .collect(Collectors.toSet());

        List<Conversation> allConversations = conversationRepository.findAllById(conversationIds);

        String lowerQuery = query.toLowerCase().trim();
        List<ConversationSearchResponse> results = new ArrayList<>();

        for (Conversation conversation : allConversations) {
            if ("GROUP".equals(conversation.getType())) {
                if (conversation.getName() != null && conversation.getName().toLowerCase().contains(lowerQuery)) {
                    results.add(ConversationSearchResponse.builder()
                        .id(conversation.getId())
                        .type(conversation.getType())
                        .name(conversation.getName())
                        .avatarUrl(conversation.getAvatarUrl())
                        .lastMessageAt(conversation.getLastMessageAt())
                        .build());
                }
            } else if ("DIRECT".equals(conversation.getType())) {
                List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
                ConversationMember otherMember = members.stream()
                    .filter(m -> !m.getUserId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);

                if (otherMember != null) {
                    boolean isBlocked = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, otherMember.getUserId());
                    boolean isBlockedBy = blockedUserRepository.existsByBlockerIdAndBlockedId(otherMember.getUserId(), currentUserId);

                    if (isBlocked || isBlockedBy) {
                        continue;
                    }

                    User otherUser = userRepository.findById(otherMember.getUserId()).orElse(null);
                    if (otherUser != null) {
                        String username = otherUser.getUsername() != null ? otherUser.getUsername().toLowerCase() : "";
                        String displayName = otherUser.getDisplayName() != null ? otherUser.getDisplayName().toLowerCase() : "";

                        if (username.contains(lowerQuery) || displayName.contains(lowerQuery)) {
                            String displayedName = otherUser.getDisplayName() != null ? otherUser.getDisplayName() : otherUser.getUsername();
                            results.add(ConversationSearchResponse.builder()
                                .id(conversation.getId())
                                .type(conversation.getType())
                                .name(displayedName)
                                .avatarUrl(otherUser.getAvatarUrl())
                                .lastMessageAt(conversation.getLastMessageAt())
                                .build());
                        }
                    }
                }
            }
        }

        return ApiResponse.<List<ConversationSearchResponse>>builder()
            .status("success")
            .code("SUCCESS")
            .message("Conversations found")
            .data(results)
            .build();
    }

    public ApiResponse<PagedMessageSearchResponse> searchMessages(String conversationId, String query, Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ApiResponse.<PagedMessageSearchResponse>builder()
                .status("error")
                .code("UNAUTHORIZED")
                .message("User not authenticated")
                .build();
        }

        String currentUserId = (String) authentication.getPrincipal();

        ConversationMember membership = conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, currentUserId)
            .orElse(null);

        if (membership == null) {
            return ApiResponse.<PagedMessageSearchResponse>builder()
                .status("error")
                .code("FORBIDDEN")
                .message("User is not a member of this conversation")
                .build();
        }

        Page<Message> messagesPage = messageRepository.searchMessagesInConversation(conversationId, query, pageable);

        Set<String> senderIds = messagesPage.getContent().stream()
            .map(Message::getSenderId)
            .collect(Collectors.toSet());

        Map<String, User> senderMap = userRepository.findAllById(senderIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));

        List<MessageSearchResponse> messageResponses = messagesPage.getContent().stream()
            .map(message -> {
                User sender = senderMap.get(message.getSenderId());
                return MessageSearchResponse.builder()
                    .id(message.getId())
                    .conversationId(message.getConversationId())
                    .senderId(message.getSenderId())
                    .senderUsername(sender != null ? sender.getUsername() : null)
                    .senderDisplayName(sender != null ? sender.getDisplayName() : null)
                    .content(message.getContent())
                    .createdAt(message.getCreatedAt())
                    .build();
            })
            .collect(Collectors.toList());

        PagedMessageSearchResponse pagedResponse = PagedMessageSearchResponse.builder()
            .messages(messageResponses)
            .page(messagesPage.getNumber())
            .size(messagesPage.getSize())
            .totalElements(messagesPage.getTotalElements())
            .hasNext(messagesPage.hasNext())
            .build();

        return ApiResponse.<PagedMessageSearchResponse>builder()
            .status("success")
            .code("SUCCESS")
            .message("Messages found")
            .data(pagedResponse)
            .build();
    }
}
