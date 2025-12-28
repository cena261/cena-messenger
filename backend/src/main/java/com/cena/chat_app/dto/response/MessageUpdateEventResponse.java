package com.cena.chat_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageUpdateEventResponse {
    private String action;
    private String messageId;
    private String conversationId;
    private String senderId;
    private String content;
    private String updatedAt;
    private Boolean isDeleted;
}
