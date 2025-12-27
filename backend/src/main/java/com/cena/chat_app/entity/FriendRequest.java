package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "friend_requests")
@CompoundIndexes({
    @CompoundIndex(name = "toUserId_status_idx", def = "{'toUserId': 1, 'status': 1}"),
    @CompoundIndex(name = "fromUserId_toUserId_unique_idx", def = "{'fromUserId': 1, 'toUserId': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {
    @Id
    private String id;

    private String fromUserId;
    private String toUserId;
    private String status;
    private Instant createdAt;
}
