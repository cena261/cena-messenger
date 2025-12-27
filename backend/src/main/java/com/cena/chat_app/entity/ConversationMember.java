package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "conversation_members")
@CompoundIndexes({
    @CompoundIndex(name = "conversationId_userId_unique_idx", def = "{'conversationId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "userId_unreadCount_idx", def = "{'userId': 1, 'unreadCount': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {
    @Id
    private String id;

    private String conversationId;

    @Indexed
    private String userId;

    private String role;
    private boolean canSendMessage;
    private Instant joinedAt;
    private String lastReadMessageId;
    private long unreadCount;
}
