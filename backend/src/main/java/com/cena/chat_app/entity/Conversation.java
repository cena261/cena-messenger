package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import static org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING;

@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    private String id;

    private String type;
    private String name;
    private String avatarUrl;
    private String ownerId;
    private String lastMessageId;

    @Indexed(direction = DESCENDING)
    private Instant lastMessageAt;

    private Instant createdAt;
}
