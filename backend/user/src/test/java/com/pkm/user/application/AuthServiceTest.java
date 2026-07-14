package com.pkm.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pkm.common.exception.BusinessException;
import com.pkm.user.domain.AuthResult;
import com.pkm.user.domain.RoleName;
import com.pkm.user.infrastructure.InMemoryUserRepository;
import com.pkm.user.infrastructure.Sha256PasswordHasher;
import com.pkm.user.infrastructure.SimpleJwtTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test
    void registersEmailUserWithDefaultUserRoleAndCanLogin() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new SimpleJwtTokenService("stage0-secret", Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)));

        AuthResult registered = authService.register("reader@example.com", "Password123!");
        AuthResult loggedIn = authService.login("reader@example.com", "Password123!");

        assertEquals("reader@example.com", registered.user().email());
        assertTrue(registered.user().roles().contains(RoleName.USER));
        assertTrue(loggedIn.accessToken().startsWith("Bearer "));
    }

    @Test
    void rejectsDuplicateEmailRegistration() {
        AuthService authService = new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                new SimpleJwtTokenService("stage0-secret", Clock.systemUTC()));

        authService.register("reader@example.com", "Password123!");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("reader@example.com", "Password123!"));
        assertEquals("user_email_exists", exception.code());
    }
}
