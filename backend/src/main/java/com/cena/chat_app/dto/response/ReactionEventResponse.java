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
public class ReactionEventResponse {
    private String messageId;
    private String conversationId;
    private String userId;
    private String reactionType;
    private boolean added;
    private Map<String, String> allReactions;
}
