package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "blocked_users")
@CompoundIndex(name = "blocker_blocked_idx", def = "{'blockerId': 1, 'blockedId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUser {
    @Id
    private String id;

    private String blockerId;
    private String blockedId;
    private Instant createdAt;
}
