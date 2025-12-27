package com.cena.chat_app.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WebSocketSessionManager {
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();

    public void addSession(String userId, String sessionId) {
        sessionToUser.put(sessionId, userId);
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void removeSession(String sessionId) {
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userToSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userToSessions.remove(userId);
                }
            }
        }
    }

    public String getUserId(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    public Set<String> getUserSessions(String userId) {
        return userToSessions.getOrDefault(userId, Set.of());
    }

    public boolean isUserConnected(String userId) {
        return userToSessions.containsKey(userId) && !userToSessions.get(userId).isEmpty();
    }

    public Set<String> getConnectedUsers() {
        return userToSessions.keySet().stream()
            .filter(this::isUserConnected)
            .collect(Collectors.toSet());
    }
}
