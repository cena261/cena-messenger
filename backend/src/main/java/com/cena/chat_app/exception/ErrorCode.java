package com.cena.chat_app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("UNAUTHORIZED", "User not authenticated", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("ACCESS_DENIED", "Access denied", HttpStatus.FORBIDDEN),
    USERNAME_EXISTS("USERNAME_EXISTS", "Username already exists", HttpStatus.CONFLICT),
    EMAIL_EXISTS("EMAIL_EXISTS", "Email already exists", HttpStatus.CONFLICT),
    PHONE_EXISTS("PHONE_EXISTS", "Phone number already exists", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_TARGET_USER("INVALID_TARGET_USER", "Invalid target user", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND),
    CONVERSATION_ACCESS_DENIED("CONVERSATION_ACCESS_DENIED", "You do not have access to this conversation", HttpStatus.FORBIDDEN),
    MESSAGE_NOT_FOUND("MESSAGE_NOT_FOUND", "Message not found", HttpStatus.NOT_FOUND),
    MESSAGE_EDIT_DENIED("MESSAGE_EDIT_DENIED", "You can only edit your own messages", HttpStatus.FORBIDDEN),
    MESSAGE_DELETE_DENIED("MESSAGE_DELETE_DENIED", "You can only delete your own messages", HttpStatus.FORBIDDEN),
    MESSAGE_ALREADY_DELETED("MESSAGE_ALREADY_DELETED", "Message has already been deleted", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_EDITABLE("MESSAGE_NOT_EDITABLE", "Only TEXT messages can be edited", HttpStatus.BAD_REQUEST),
    REPLY_MESSAGE_NOT_FOUND("REPLY_MESSAGE_NOT_FOUND", "Replied message not found", HttpStatus.NOT_FOUND),
    REPLY_MESSAGE_DIFFERENT_CONVERSATION("REPLY_MESSAGE_DIFFERENT_CONVERSATION", "Cannot reply to message from different conversation", HttpStatus.BAD_REQUEST),
    NOT_GROUP_CONVERSATION("NOT_GROUP_CONVERSATION", "This operation is only allowed for group conversations", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_PERMISSION("INSUFFICIENT_PERMISSION", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    OWNER_CANNOT_LEAVE("OWNER_CANNOT_LEAVE", "Owner must transfer ownership before leaving the group", HttpStatus.BAD_REQUEST),
    CANNOT_KICK_OWNER("CANNOT_KICK_OWNER", "Cannot kick the group owner", HttpStatus.BAD_REQUEST),
    CANNOT_KICK_SELF("CANNOT_KICK_SELF", "Cannot kick yourself from the group", HttpStatus.BAD_REQUEST),
    INVALID_ROLE_CHANGE("INVALID_ROLE_CHANGE", "Invalid role change operation", HttpStatus.BAD_REQUEST),
    CANNOT_CHANGE_OWN_ROLE("CANNOT_CHANGE_OWN_ROLE", "Cannot change your own role", HttpStatus.BAD_REQUEST),
    TARGET_USER_NOT_MEMBER("TARGET_USER_NOT_MEMBER", "Target user is not a member of this group", HttpStatus.BAD_REQUEST),
    NEW_OWNER_NOT_MEMBER("NEW_OWNER_NOT_MEMBER", "New owner must be a member of the group", HttpStatus.BAD_REQUEST),
    CANNOT_BLOCK_SELF("CANNOT_BLOCK_SELF", "Cannot block yourself", HttpStatus.BAD_REQUEST),
    USER_BLOCKED("USER_BLOCKED", "This action cannot be performed because of blocking", HttpStatus.FORBIDDEN),
    BLOCKED_BY_USER("BLOCKED_BY_USER", "This action cannot be performed because you are blocked", HttpStatus.FORBIDDEN),
    BLOCKED_USER_IN_GROUP("BLOCKED_USER_IN_GROUP", "Cannot add blocked user to group", HttpStatus.FORBIDDEN),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),
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
}
