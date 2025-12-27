package com.cena.chat_app;

import com.cena.chat_app.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
class WebSocketSecurityTest {

    @LocalServerPort
    private int port;

    @Test
    void testWebSocketConnectionRejectedWithInvalidToken() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        String wsUrl = "ws://localhost:" + port + "/ws?token=invalid_token_12345";

        assertThrows(ExecutionException.class, () -> {
            stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    void testWebSocketConnectionRejectedWithoutToken() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        String wsUrl = "ws://localhost:" + port + "/ws";

        assertThrows(Exception.class, () -> {
            stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    void testWebSocketConnectionRejectedWithMalformedToken() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        String wsUrl = "ws://localhost:" + port + "/ws?token=not.a.valid.jwt.format";

        assertThrows(ExecutionException.class, () -> {
            stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        });
    }
}
