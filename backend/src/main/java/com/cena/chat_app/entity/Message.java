package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "messages")
@CompoundIndex(name = "conversationId_createdAt_desc_idx", def = "{'conversationId': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private String id;

    private String conversationId;
    private String senderId;
    private String type;
    private String content;
    private String mediaUrl;
    private String replyTo;
    private Map<String, String> reactions;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;
}
