package com.mindora.app.web;

import com.mindora.common.api.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class HealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(
                Map.of("status", "up", "service", "mindora"),
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }
}
