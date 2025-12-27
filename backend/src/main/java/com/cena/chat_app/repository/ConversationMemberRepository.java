package com.cena.chat_app.repository;

import com.cena.chat_app.entity.ConversationMember;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends MongoRepository<ConversationMember, String> {
    List<ConversationMember> findByUserId(String userId);
    List<ConversationMember> findByConversationId(String conversationId);
    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);
}
