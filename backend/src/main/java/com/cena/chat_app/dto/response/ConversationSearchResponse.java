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
public class ConversationSearchResponse {
    private String id;
    private String type;
    private String name;
    private String avatarUrl;
    private Instant lastMessageAt;
}
