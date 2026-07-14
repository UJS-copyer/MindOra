package com.mindora.app.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.user.application.AuthService;
import com.mindora.user.domain.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthView> register(
            @Valid @RequestBody AuthRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return response(authService.register(request.email(), request.password()), traceId);
    }

    @PostMapping("/login")
    public ApiResponse<AuthView> login(
            @Valid @RequestBody AuthRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return response(authService.login(request.email(), request.password()), traceId);
    }

    private ApiResponse<AuthView> response(AuthResult result, String traceId) {
        return ApiResponse.success(
                new AuthView(result.user().id(), result.user().email(), result.accessToken()),
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    public record AuthRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record AuthView(
            UUID userId,
            String email,
            String accessToken) {
    }
}
