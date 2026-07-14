package com.pkm.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successIncludesStandardEnvelopeAndTraceId() {
        ApiResponse<String> response = ApiResponse.success("pong", "trace-1");

        assertEquals("success", response.code());
        assertEquals("OK", response.message());
        assertEquals("pong", response.data());
        assertEquals("trace-1", response.traceId());
        assertNotNull(response.createdAt());
    }
}
