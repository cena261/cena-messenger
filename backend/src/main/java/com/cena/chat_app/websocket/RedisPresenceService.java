package com.cena.chat_app.websocket;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RedisPresenceService {
    private static final String USER_SESSIONS_PREFIX = "presence:user:";
    private static final String SESSION_USER_PREFIX = "session:";
    private static final String SESSION_USER_SUFFIX = ":user";

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addSession(String userId, String sessionId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        String sessionKey = SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX;

        redisTemplate.opsForSet().add(userKey, sessionId);
        redisTemplate.opsForValue().set(sessionKey, userId);
    }

    public void removeSession(String sessionId) {
        String sessionKey = SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX;
        String userId = redisTemplate.opsForValue().get(sessionKey);

        if (userId != null) {
            String userKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userKey, sessionId);
            redisTemplate.delete(sessionKey);

            Long remaining = redisTemplate.opsForSet().size(userKey);
            if (remaining != null && remaining == 0) {
                redisTemplate.delete(userKey);
            }
        }
    }

    public String getUserId(String sessionId) {
        String sessionKey = SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX;
        return redisTemplate.opsForValue().get(sessionKey);
    }

    public Set<String> getUserSessions(String userId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        return redisTemplate.opsForSet().members(userKey);
    }

    public boolean isUserConnected(String userId) {
        String userKey = USER_SESSIONS_PREFIX + userId;
        Long size = redisTemplate.opsForSet().size(userKey);
        return size != null && size > 0;
    }

    public Set<String> getConnectedUsers() {
        Set<String> keys = redisTemplate.keys(USER_SESSIONS_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
            .map(key -> key.substring(USER_SESSIONS_PREFIX.length()))
            .collect(java.util.stream.Collectors.toSet());
    }
}
