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
public class ConversationMemberResponse {
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String role;
    private Instant joinedAt;
}
