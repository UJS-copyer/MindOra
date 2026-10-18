package com.mindora.user.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.user.application.AuthRateLimitGuard;
import com.mindora.user.application.AuthService;
import com.mindora.user.application.AuthSessionService;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.IssuedRefreshToken;
import com.mindora.user.domain.TokenPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    /** 刷新令牌 Cookie：只发给认证端点，HttpOnly + SameSite=Lax（design-12 §2.2）。 */
    public static final String REFRESH_TOKEN_COOKIE = "mindora_rt";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AuthSessionService sessionService;
    private final AuthRateLimitGuard rateLimitGuard;
    private final TokenService tokenService;
    private final Duration refreshTtl;
    private final boolean cookieSecure;
    private final int registerPerHour;
    private final int loginPerMinute;

    public AuthController(
            AuthService authService,
            AuthSessionService sessionService,
            AuthRateLimitGuard rateLimitGuard,
            TokenService tokenService,
            @Value("${mindora.auth.refresh-ttl:P14D}") Duration refreshTtl,
            @Value("${mindora.auth.cookie.secure:false}") boolean cookieSecure,
            @Value("${mindora.auth.rate-limit.register-per-hour:5}") int registerPerHour,
            @Value("${mindora.auth.rate-limit.login-per-minute:10}") int loginPerMinute) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.rateLimitGuard = rateLimitGuard;
        this.tokenService = tokenService;
        this.refreshTtl = refreshTtl;
        this.cookieSecure = cookieSecure;
        this.registerPerHour = registerPerHour;
        this.loginPerMinute = loginPerMinute;
    }

    @PostMapping("/register")
    public ApiResponse<AuthView> register(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            HttpServletResponse response) {
        rateLimitGuard.require(
                "auth:register:ip:" + httpRequest.getRemoteAddr(), registerPerHour, Duration.ofHours(1));
        AuthResult result = authService.register(request.email(), request.password());
        writeRefreshCookie(response, result.refreshToken());
        return ApiResponse.success(authView(result), traceId(traceId));
    }

    @PostMapping("/login")
    public ApiResponse<AuthView> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            HttpServletResponse response) {
        rateLimitGuard.require(
                "auth:login:ip:" + httpRequest.getRemoteAddr(), loginPerMinute, Duration.ofMinutes(1));
        AuthResult result = authService.login(request.email(), request.password());
        writeRefreshCookie(response, result.refreshToken());
        return ApiResponse.success(authView(result), traceId(traceId));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthView> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            HttpServletResponse response) {
        AuthResult result = sessionService.refresh(refreshToken);
        writeRefreshCookie(response, result.refreshToken());
        return ApiResponse.success(authView(result), traceId(traceId));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            HttpServletResponse response) {
        sessionService.logout(refreshToken);
        clearRefreshCookie(response);
        return ApiResponse.success(null, traceId(traceId));
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeView> me(
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenPrincipal principal)) {
            throw new BusinessException("token_invalid", "Access token is invalid");
        }
        AuthSessionService.AccountProfile profile = sessionService.me(principal);
        return ApiResponse.success(
                new AuthMeView(
                        profile.userId(),
                        profile.email(),
                        profile.displayName(),
                        sortedRoles(profile.roles())),
                traceId(traceId));
    }

    private AuthView authView(AuthResult result) {
        return new AuthView(
                PublicIds.toPublicId(result.user().id()),
                result.user().email(),
                sortedRoles(result.user().roles()),
                result.accessToken(),
                tokenService.accessTtlSeconds());
    }

    private List<String> sortedRoles(Set<String> roles) {
        return roles.stream().sorted().toList();
    }

    private void writeRefreshCookie(HttpServletResponse response, IssuedRefreshToken token) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(token.rawToken(), refreshTtl).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private String traceId(String traceId) {
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    public record AuthRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record AuthView(
            String userId,
            String email,
            List<String> roles,
            String accessToken,
            long expiresIn) {
    }

    public record AuthMeView(
            String userId,
            String email,
            String displayName,
            List<String> roles) {
    }
}
