package com.cena.chat_app.repository;

import com.cena.chat_app.entity.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FriendRepository extends MongoRepository<Friend, String> {
    List<Friend> findByUserId(String userId);
}
