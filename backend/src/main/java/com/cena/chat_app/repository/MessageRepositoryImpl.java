package com.cena.chat_app.repository;

import com.cena.chat_app.entity.Message;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepositoryImpl implements MessageRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    public MessageRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureTextIndex() {
        try {
            mongoTemplate.indexOps(Message.class)
                .ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
                    .onField("content")
                    .build());
        } catch (Exception e) {
        }
    }

    @Override
    public Page<Message> searchMessagesInConversation(String conversationId, String query, Pageable pageable) {
        TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(query);

        Query searchQuery = TextQuery.queryText(textCriteria)
            .addCriteria(Criteria.where("conversationId").is(conversationId))
            .addCriteria(Criteria.where("type").is("TEXT"))
            .addCriteria(Criteria.where("isDeleted").is(false));

        searchQuery.with(Sort.by(Sort.Direction.DESC, "score").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        searchQuery.with(pageable);

        List<Message> messages = mongoTemplate.find(searchQuery, Message.class);

        Query countQuery = TextQuery.queryText(textCriteria)
            .addCriteria(Criteria.where("conversationId").is(conversationId))
            .addCriteria(Criteria.where("type").is("TEXT"))
            .addCriteria(Criteria.where("isDeleted").is(false));

        long total = mongoTemplate.count(countQuery, Message.class);

        return new PageImpl<>(messages, pageable, total);
    }
}
