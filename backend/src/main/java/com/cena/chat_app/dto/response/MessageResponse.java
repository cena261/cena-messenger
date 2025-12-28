package com.cena.chat_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String id;
    private String conversationId;
    private String senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String senderAvatarUrl;
    private String type;
    private String content;
    private String mediaUrl;
    private String replyTo;
    private Map<String, String> reactions;
    private boolean isDeleted;
    private String createdAt;
    private String updatedAt;
}
