package com.cena.chat_app.repository;

import com.cena.chat_app.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MessageRepositoryCustom {
    Page<Message> searchMessagesInConversation(String conversationId, String query, Pageable pageable);
}
