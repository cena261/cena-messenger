package com.cena.chat_app.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("UNAUTHORIZED", "User not authenticated", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("ACCESS_DENIED", "Access denied", HttpStatus.FORBIDDEN),
    USERNAME_EXISTS("USERNAME_EXISTS", "Username already exists", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_TARGET_USER("INVALID_TARGET_USER", "Invalid target user", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND),
    CONVERSATION_ACCESS_DENIED("CONVERSATION_ACCESS_DENIED", "You do not have access to this conversation", HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_MISSING("REFRESH_TOKEN_MISSING", "Refresh token is missing", HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REVOKED("REFRESH_TOKEN_REVOKED", "Refresh token has been revoked", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", "Refresh token has expired", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
