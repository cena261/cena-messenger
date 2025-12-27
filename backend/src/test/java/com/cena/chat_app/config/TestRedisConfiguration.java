package com.cena.chat_app.config;

import com.github.fppt.jedismock.RedisServer;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;

import java.io.IOException;

@TestConfiguration
public class TestRedisConfiguration {
    private static RedisServer redisServer;
    private static final int TEST_REDIS_PORT = 16379;

    static {
        try {
            redisServer = RedisServer.newRedisServer(TEST_REDIS_PORT);
            redisServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start embedded Redis server on port " + TEST_REDIS_PORT, e);
        }
    }

    @PreDestroy
    public void preDestroy() {
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (IOException e) {
                throw new RuntimeException("Failed to stop embedded Redis server", e);
            }
        }
    }
}
