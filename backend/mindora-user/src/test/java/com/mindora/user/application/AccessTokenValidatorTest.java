package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.user.domain.UserAccount;
import com.mindora.user.infrastructure.persistence.memory.InMemoryUserRepository;
import com.mindora.user.infrastructure.security.JwtTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessTokenValidatorTest {
    private static final String SECRET = "stage-3.6a-access-validator-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtTokenService tokens = new JwtTokenService(SECRET, clock, Duration.ofMinutes(30));
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final AccessTokenValidator validator = new AccessTokenValidator(tokens, users);

    private UserAccount active;

    @BeforeEach
    void setUp() {
        active = new UserAccount(USER_ID, "reader@example.com", "hash", Set.of("user"));
        users.save(active);
    }

    @Test
    void missingHeaderIsAnonymous() {
        assertEquals(AccessTokenValidator.Status.MISSING, validator.validate(null).status());
        assertEquals(AccessTokenValidator.Status.MISSING, validator.validate("  ").status());
    }

    @Test
    void validTokenForActiveAccountAuthenticates() {
        AccessTokenValidator.Outcome outcome = validator.validate(tokens.issue(active));

        assertTrue(outcome.authenticated());
        assertEquals(USER_ID, outcome.principal().userId());
        assertEquals(0, outcome.principal().tokenVersion());
    }

    @Test
    void expiredTokenIsDistinguishable() {
        String token = tokens.issue(active);
        AccessTokenValidator later = new AccessTokenValidator(
                new JwtTokenService(SECRET, Clock.fixed(NOW.plus(Duration.ofMinutes(31)), ZoneOffset.UTC),
                        Duration.ofMinutes(30)),
                users);

        assertEquals(AccessTokenValidator.Status.EXPIRED, later.validate(token).status());
        assertEquals("token_expired", later.validate(token).errorCode());
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = tokens.issue(active);
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertEquals(AccessTokenValidator.Status.INVALID, validator.validate(tampered).status());
        assertEquals("token_invalid", validator.validate("garbage").errorCode());
    }

    @Test
    void unknownAccountIsInvalid() {
        UserAccount stranger = new UserAccount(
                UUID.randomUUID(), "stranger@example.com", "hash", Set.of("user"));

        assertEquals(AccessTokenValidator.Status.INVALID, validator.validate(tokens.issue(stranger)).status());
    }

    @Test
    void disabledAndDeletedAccountsAreRejected() {
        UserAccount disabled = new UserAccount(
                USER_ID, "reader@example.com", "hash", Set.of("user"), 0, "disabled", false, null);
        users.save(disabled);
        assertEquals(AccessTokenValidator.Status.ACCOUNT_DISABLED, validator.validate(tokens.issue(disabled)).status());

        UserAccount deleted = new UserAccount(
                USER_ID, "reader@example.com", "hash", Set.of("user"), 0, "active", true, null);
        users.save(deleted);
        assertEquals(AccessTokenValidator.Status.ACCOUNT_DISABLED, validator.validate(tokens.issue(deleted)).status());
    }

    @Test
    void bumpedTokenVersionRevokesOldTokens() {
        String token = tokens.issue(active);
        users.bumpTokenVersion(USER_ID);

        AccessTokenValidator.Outcome outcome = validator.validate(token);
        assertEquals(AccessTokenValidator.Status.TOKEN_REVOKED, outcome.status());
        assertEquals("token_revoked", outcome.errorCode());
    }
}
