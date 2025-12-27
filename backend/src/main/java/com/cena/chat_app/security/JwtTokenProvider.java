package com.cena.chat_app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private static final String SECRET_KEY = "your-256-bit-secret-key-for-jwt-signing-must-be-at-least-256-bits-long";
    private static final long ACCESS_TOKEN_VALIDITY = 3600000;
    private static final long REFRESH_TOKEN_VALIDITY = 2592000000L;
    private final SecretKey key;
    private final SecureRandom secureRandom;

    public JwtTokenProvider() {
        this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        this.secureRandom = new SecureRandom();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    public String generateAccessToken(String userId) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(ACCESS_TOKEN_VALIDITY);

        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public long getAccessTokenValidity() {
        return ACCESS_TOKEN_VALIDITY;
    }

    public Instant getRefreshTokenExpiration() {
        return Instant.now().plusMillis(REFRESH_TOKEN_VALIDITY);
    }
}
