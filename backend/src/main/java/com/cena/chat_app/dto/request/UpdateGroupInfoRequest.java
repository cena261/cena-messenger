package com.cena.chat_app.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupInfoRequest {
    private String conversationId;
    private String name;
    private String avatarUrl;
}
