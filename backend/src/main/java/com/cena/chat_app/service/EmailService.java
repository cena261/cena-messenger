package com.cena.chat_app.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {
    private static final int MAX_RETRIES = 1;

    private final JavaMailSender mailSender;
    private final Counter emailSuccessCounter;
    private final Counter emailFailureCounter;
    private final Counter emailTimeoutCounter;

    @Value("${spring.mail.from}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, MeterRegistry meterRegistry) {
        this.mailSender = mailSender;
        this.emailSuccessCounter = meterRegistry.counter("email.send.success");
        this.emailFailureCounter = meterRegistry.counter("email.send.failure");
        this.emailTimeoutCounter = meterRegistry.counter("email.send.timeout");
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);

                helper.setFrom(fromEmail);
                helper.setTo(toEmail);
                helper.setSubject("Password Reset Code");

                String htmlContent = buildPasswordResetEmail(code);
                helper.setText(htmlContent, true);

                mailSender.send(message);
                emailSuccessCounter.increment();
                return;
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (isTimeoutException(e)) {
                    emailTimeoutCounter.increment();
                    log.warn("Email send timeout on attempt {}/{}", attempt, MAX_RETRIES + 1);
                } else {
                    log.warn("Email send failed on attempt {}/{}: {}", attempt, MAX_RETRIES + 1, e.getMessage());
                }

                if (attempt <= MAX_RETRIES) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        emailFailureCounter.increment();
        log.error("Failed to send email after {} attempts", MAX_RETRIES + 1, lastException);
    }

    private boolean isTimeoutException(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("timeout") || message.contains("timed out"));
    }

    private String buildPasswordResetEmail(String code) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
                "<h2>Password Reset Request</h2>" +
                "<p>You have requested to reset your password. Use the code below to reset your password:</p>" +
                "<div style='background-color: #f4f4f4; padding: 15px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 5px; margin: 20px 0;'>" +
                code +
                "</div>" +
                "<p>This code will expire in 10 minutes.</p>" +
                "<p>If you did not request a password reset, please ignore this email.</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
