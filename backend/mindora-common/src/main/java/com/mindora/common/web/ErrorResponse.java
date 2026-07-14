package com.mindora.common.web;

public record ErrorResponse(
        String code,
        String message,
        Object details,
        String traceId) {
}
