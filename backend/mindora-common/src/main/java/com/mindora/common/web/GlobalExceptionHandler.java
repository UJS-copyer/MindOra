package com.mindora.common.web;

import com.mindora.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
                // 显式指定 JSON：SSE 客户端常只发 Accept: text/event-stream，不显式指定就会协商失败（406），
                // 请求被转发到 /error，真正的错误码与消息全部丢失（外部表现是 403 空体）。
                .contentType(MediaType.APPLICATION_JSON)
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
            // 公开问答关闭时端点等同于不存在（不暴露「曾经开放过」的信息）
            case "rag_public_disabled" -> HttpStatus.NOT_FOUND;
            // 公开问答限流：429 而非 400，便于调用方区分「参数错」与「太频繁」
            case "rag_rate_limited" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
