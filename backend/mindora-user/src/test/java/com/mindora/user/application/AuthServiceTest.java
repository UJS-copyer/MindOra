package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.IssuedRefreshToken;
import com.mindora.user.domain.RoleName;
import com.mindora.user.infrastructure.persistence.memory.InMemoryUserRepository;
import com.mindora.user.infrastructure.security.JwtTokenService;
import com.mindora.user.infrastructure.security.Sha256PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String SECRET = "stage-3.6a-test-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = mock(RefreshTokenService.class);
        when(refreshTokenService.issue(anyString(), anyInt()))
                .thenReturn(new IssuedRefreshToken("raw-refresh", "family-1", NOW.plus(Duration.ofDays(14))));
    }

    private AuthService authService(Clock clock) {
        return new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new JwtTokenService(SECRET, clock, Duration.ofMinutes(30)),
                refreshTokenService);
    }

    @Test
    void registersEmailUserWithDefaultUserRoleAndCanLogin() {
        AuthService authService = authService(Clock.fixed(NOW, ZoneOffset.UTC));

        AuthResult registered = authService.register("reader@example.com", "Password123!");
        AuthResult loggedIn = authService.login("reader@example.com", "Password123!");

        assertEquals("reader@example.com", registered.user().email());
        assertTrue(registered.user().roles().contains(RoleName.USER.value()));
        // 新契约：返回值是裸 JWS（前缀由客户端拼），不再是 "Bearer ..."。
        assertFalse(loggedIn.accessToken().startsWith("Bearer "));
        assertTrue(loggedIn.accessToken().split("\\.").length == 3);
        assertNotNull(loggedIn.refreshToken());
        assertEquals("raw-refresh", loggedIn.refreshToken().rawToken());
    }

    @Test
    void rejectsDuplicateEmailRegistration() {
        AuthService authService = authService(Clock.systemUTC());

        authService.register("reader@example.com", "Password123!");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("reader@example.com", "Password123!"));
        assertEquals("user_email_exists", exception.code());
    }
}
