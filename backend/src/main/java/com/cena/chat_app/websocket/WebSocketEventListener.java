package com.cena.chat_app.websocket;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WebSocketEventListener {
    private final RedisPresenceService presenceService;
    private final AtomicInteger activeConnections;
    private final Counter connectionsTotal;
    private final Counter disconnectionsTotal;

    public WebSocketEventListener(RedisPresenceService presenceService, MeterRegistry meterRegistry) {
        this.presenceService = presenceService;
        this.activeConnections = meterRegistry.gauge("chat.realtime.websocket.connections.active",
                new AtomicInteger(0));
        this.connectionsTotal = meterRegistry.counter("chat.realtime.websocket.connections.total");
        this.disconnectionsTotal = meterRegistry.counter("chat.realtime.websocket.disconnections.total");
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        if (headerAccessor.getSessionAttributes() != null) {
            String userId = (String) headerAccessor.getSessionAttributes().get("userId");
            if (userId != null && sessionId != null) {
                presenceService.addSession(userId, sessionId);
                activeConnections.incrementAndGet();
                connectionsTotal.increment();
                log.info("WebSocket connection established - userId={}, sessionId={}", userId, sessionId);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        if (sessionId != null) {
            String userId = presenceService.getUserId(sessionId);
            presenceService.removeSession(sessionId);
            activeConnections.decrementAndGet();
            disconnectionsTotal.increment();
            if (userId != null) {
                log.info("WebSocket connection disconnected - userId={}, sessionId={}", userId, sessionId);
            }
        }
    }
}
