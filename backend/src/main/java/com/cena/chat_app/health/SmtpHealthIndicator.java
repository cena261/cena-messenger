package com.cena.chat_app.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpHealthIndicator implements HealthIndicator {
    private final JavaMailSender mailSender;

    @Override
    public Health health() {
        try {
            if (mailSender instanceof JavaMailSenderImpl javaMailSender) {
                javaMailSender.testConnection();

                return Health.up()
                    .withDetail("host", javaMailSender.getHost())
                    .withDetail("port", javaMailSender.getPort())
                    .withDetail("status", "connected")
                    .build();
            }

            return Health.unknown()
                .withDetail("status", "unable to verify")
                .build();
        } catch (Exception e) {
            log.warn("SMTP health check failed (optional dependency): {}", e.getMessage());
            return Health.down()
                .withDetail("status", "unavailable")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
