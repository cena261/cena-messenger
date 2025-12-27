package com.cena.chat_app.config;

import com.cena.chat_app.websocket.RedisMessageSubscriber;
import com.cena.chat_app.websocket.RedisUnreadSubscriber;
import tools.jackson.databind.DeserializationFeature;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return (builder) -> {
            builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        };
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber messageSubscriber,
            RedisUnreadSubscriber unreadSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageSubscriber, messageSubscriber.getChannelPattern());
        container.addMessageListener(unreadSubscriber, unreadSubscriber.getChannelPattern());
        return container;
    }
}
