package com.cena.chat_app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "friends")
@CompoundIndex(name = "userId_friendId_unique_idx", def = "{'userId': 1, 'friendId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friend {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String friendId;
    private Instant createdAt;
}
