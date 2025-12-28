package com.cena.chat_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupEventResponse {
    private String eventType;
    private String conversationId;
    private String actorId;
    private String targetUserId;
    private String previousRole;
    private String newRole;
    private String groupName;
    private String groupAvatarUrl;
}
