package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.RoleName;
import com.mindora.user.infrastructure.persistence.memory.InMemoryUserRepository;
import com.mindora.user.infrastructure.security.JwtTokenService;
import com.mindora.user.infrastructure.security.Sha256PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String SECRET = "stage-3.6a-test-secret-0123456789abcdef";

    @Test
    void registersEmailUserWithDefaultUserRoleAndCanLogin() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new JwtTokenService(SECRET, Clock.fixed(
                        Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(30)));

        AuthResult registered = authService.register("reader@example.com", "Password123!");
        AuthResult loggedIn = authService.login("reader@example.com", "Password123!");

        assertEquals("reader@example.com", registered.user().email());
        assertTrue(registered.user().roles().contains(RoleName.USER.value()));
        // 新契约：返回值是裸 JWS（前缀由客户端拼），不再是 "Bearer ..."。
        assertFalse(loggedIn.accessToken().startsWith("Bearer "));
        assertTrue(loggedIn.accessToken().split("\\.").length == 3);
    }

    @Test
    void rejectsDuplicateEmailRegistration() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new JwtTokenService(SECRET, Clock.systemUTC(), Duration.ofMinutes(30)));

        authService.register("reader@example.com", "Password123!");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("reader@example.com", "Password123!"));
        assertEquals("user_email_exists", exception.code());
    }
}
