package com.mindora.common.web;

import com.mindora.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ErrorResponse handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return handleBusinessException(exception, traceId);
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
}
