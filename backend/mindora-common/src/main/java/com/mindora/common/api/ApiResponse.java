package com.mindora.common.api;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant createdAt) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("success", "OK", data, traceId, Instant.now());
    }
}
