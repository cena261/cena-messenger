package com.cena.chat_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchResponse {
    private String id;
    private String conversationId;
    private String senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private Instant createdAt;
}
