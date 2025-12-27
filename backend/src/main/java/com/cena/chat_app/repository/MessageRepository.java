package com.cena.chat_app.repository;

import com.cena.chat_app.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
}
