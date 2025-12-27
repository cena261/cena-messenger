package com.cena.chat_app.exception;

import com.cena.chat_app.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .status("error")
            .code(errorCode.getCode())
            .message(ex.getMessage())
            .data(null)
            .build();

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .status("error")
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .data(null)
            .build();

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .status("error")
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .data(null)
            .build();

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }
}
