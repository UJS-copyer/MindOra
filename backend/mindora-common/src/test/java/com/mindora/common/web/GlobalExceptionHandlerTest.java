package com.mindora.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mindora.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {
    @Test
    void businessExceptionMapsToStableErrorCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ErrorResponse response = handler.handleBusinessException(
                new BusinessException("user_email_exists", "Email already exists"),
                "trace-1");

        assertEquals("user_email_exists", response.code());
        assertEquals("Email already exists", response.message());
        assertEquals("trace-1", response.traceId());
    }
}
