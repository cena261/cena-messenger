package com.cena.chat_app.filter;

import com.cena.chat_app.dto.ApiResponse;
import com.cena.chat_app.service.RedisRateLimitService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int AUTH_MAX_REQUESTS_PER_MINUTE = 5;
    private static final int MESSAGE_MAX_REQUESTS_PER_MINUTE = 60;
    private static final int MEDIA_PRESIGN_MAX_REQUESTS_PER_MINUTE = 20;
    private static final int CONVERSATION_MAX_REQUESTS_PER_MINUTE = 10;
    private static final int BLOCKING_MAX_REQUESTS_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final Map<String, Counter> rateLimitCounters;

    public RateLimitFilter(RedisRateLimitService rateLimitService,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.rateLimitCounters = new HashMap<>();
        this.rateLimitCounters.put("rest_auth", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "rest", "action", "auth"));
        this.rateLimitCounters.put("rest_message", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "rest", "action", "message"));
        this.rateLimitCounters.put("rest_media", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "rest", "action", "media"));
        this.rateLimitCounters.put("rest_conversation", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "rest", "action", "conversation"));
        this.rateLimitCounters.put("rest_blocking", meterRegistry.counter("chat.ratelimit.blocked", "protocol", "rest", "action", "blocking"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        RateLimitConfig config = determineRateLimitConfig(uri, method);

        if (config != null) {
            String identifier = getIdentifier(request, config.useIp);

            if (identifier != null) {
                boolean allowed = rateLimitService.allowRequest(identifier, config.action, config.maxRequests, WINDOW);

                if (!allowed) {
                    Counter counter = rateLimitCounters.get("rest_" + config.metricAction);
                    if (counter != null) {
                        counter.increment();
                    }

                    log.warn("Rate limit exceeded - uri={}, method={}, identifier={}, action={}",
                            uri, method, identifier, config.action);

                    sendRateLimitResponse(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitConfig determineRateLimitConfig(String uri, String method) {
        if (uri.startsWith("/api/auth/login") && "POST".equals(method)) {
            return new RateLimitConfig("auth_login", AUTH_MAX_REQUESTS_PER_MINUTE, true, "auth");
        } else if (uri.startsWith("/api/auth/register") && "POST".equals(method)) {
            return new RateLimitConfig("auth_register", AUTH_MAX_REQUESTS_PER_MINUTE, true, "auth");
        } else if (uri.startsWith("/api/messages") && "POST".equals(method)) {
            return new RateLimitConfig("message_send", MESSAGE_MAX_REQUESTS_PER_MINUTE, false, "message");
        } else if (uri.startsWith("/api/media/presigned-url") && "POST".equals(method)) {
            return new RateLimitConfig("media_presign", MEDIA_PRESIGN_MAX_REQUESTS_PER_MINUTE, false, "media");
        } else if (uri.startsWith("/api/media/create-message") && "POST".equals(method)) {
            return new RateLimitConfig("media_create", MEDIA_PRESIGN_MAX_REQUESTS_PER_MINUTE, false, "media");
        } else if (uri.startsWith("/api/conversations") && "POST".equals(method)) {
            return new RateLimitConfig("conversation_create", CONVERSATION_MAX_REQUESTS_PER_MINUTE, false, "conversation");
        } else if (uri.startsWith("/api/blocking") && "POST".equals(method)) {
            return new RateLimitConfig("blocking_action", BLOCKING_MAX_REQUESTS_PER_MINUTE, false, "blocking");
        }
        return null;
    }

    private String getIdentifier(HttpServletRequest request, boolean useIp) {
        if (useIp) {
            return getClientIp(request);
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return (String) auth.getPrincipal();
            }
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .status("error")
                .code("RATE_LIMIT_EXCEEDED")
                .message("Too many requests, please try again later")
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }

    private static class RateLimitConfig {
        String action;
        int maxRequests;
        boolean useIp;
        String metricAction;

        RateLimitConfig(String action, int maxRequests, boolean useIp, String metricAction) {
            this.action = action;
            this.maxRequests = maxRequests;
            this.useIp = useIp;
            this.metricAction = metricAction;
        }
    }
}
