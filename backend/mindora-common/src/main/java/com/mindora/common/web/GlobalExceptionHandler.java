package com.mindora.common.web;

import com.mindora.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return ResponseEntity
                .status(statusFor(exception.code()))
                .body(handleBusinessException(exception, traceId));
    }

    public ErrorResponse handleBusinessException(
            BusinessException exception,
            String traceId) {
        return new ErrorResponse(
                exception.code(),
                exception.getMessage(),
                null,
                traceId == null || traceId.isBlank() ? "untracked" : traceId);
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "user_email_exists" -> HttpStatus.CONFLICT;
            case "invalid_credentials" -> HttpStatus.UNAUTHORIZED;
            case "validation_error" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
