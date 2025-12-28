package com.cena.chat_app.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMediaMessageRequest {
    private String conversationId;
    private String fileKey;
    private String type;
    private Map<String, Object> mediaMetadata;
    private String replyTo;
}
