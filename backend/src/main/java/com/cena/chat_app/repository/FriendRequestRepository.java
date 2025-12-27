package com.cena.chat_app.repository;

import com.cena.chat_app.entity.FriendRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends MongoRepository<FriendRequest, String> {
    List<FriendRequest> findByToUserIdAndStatus(String toUserId, String status);
    Optional<FriendRequest> findByFromUserIdAndToUserId(String fromUserId, String toUserId);
}
