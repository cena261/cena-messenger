package com.cena.chat_app.repository;

import com.cena.chat_app.entity.BlockedUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedUserRepository extends MongoRepository<BlockedUser, String> {
    Optional<BlockedUser> findByBlockerIdAndBlockedId(String blockerId, String blockedId);

    List<BlockedUser> findByBlockerId(String blockerId);

    List<BlockedUser> findByBlockedId(String blockedId);

    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);

    void deleteByBlockerIdAndBlockedId(String blockerId, String blockedId);
}
