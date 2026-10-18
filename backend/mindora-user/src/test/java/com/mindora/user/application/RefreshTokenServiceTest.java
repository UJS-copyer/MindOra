package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.application.port.AuthRefreshTokenRepository;
import com.mindora.user.domain.AuthRefreshToken;
import com.mindora.user.domain.IssuedRefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-18T00:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final Duration ABSOLUTE_TTL = Duration.ofDays(30);
    private static final Duration GRACE = Duration.ofSeconds(60);
    private static final String USER = "user-001";

    private MutableClock clock;
    private FakeRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        repository = new FakeRepository();
        service = new RefreshTokenService(repository, REFRESH_TTL, ABSOLUTE_TTL, GRACE, clock);
    }

    @Test
    void issueStoresOnlyHashAndReturnsRawTokenOnce() {
        IssuedRefreshToken issued = service.issue(USER, 3);

        assertEquals(43, issued.rawToken().length());
        assertEquals(T0.plus(REFRESH_TTL), issued.expiresAt());
        assertTrue(repository.findByTokenHash(issued.rawToken()).isEmpty(), "raw token must not be indexed");

        AuthRefreshToken stored = repository.findByTokenHash(RefreshTokenService.hash(issued.rawToken()))
                .orElseThrow();
        assertEquals(issued.familyId(), stored.familyId());
        assertEquals(USER, stored.userId());
        assertEquals(3, stored.tokenVersion());
        assertEquals(T0, stored.familyIssuedAt());
        assertNull(stored.rotatedAt());
        assertNull(stored.revokedAt());
    }

    @Test
    void rotateMarksOldTokenAndIssuesNewOneInSameFamily() {
        IssuedRefreshToken first = service.issue(USER, 0);
        clock.advance(Duration.ofMinutes(5));

        IssuedRefreshToken second = service.rotate(service.requireActive(first.rawToken()));

        AuthRefreshToken old = stored(first);
        AuthRefreshToken renewed = stored(second);
        assertNotNull(old.rotatedAt());
        assertEquals(renewed.id(), old.replacedBy());
        assertEquals(first.familyId(), renewed.familyId());
        assertEquals(T0, renewed.familyIssuedAt());
        assertEquals(clock.instant().plus(REFRESH_TTL), renewed.expiresAt());
        assertEquals(2, repository.all().size());
    }

    @Test
    void rotateWithinGraceIssuesAnotherTokenWithoutRevokingFamily() {
        IssuedRefreshToken first = service.issue(USER, 0);
        clock.advance(Duration.ofMinutes(1));
        service.rotate(service.requireActive(first.rawToken()));
        clock.advance(Duration.ofSeconds(30));

        IssuedRefreshToken third = service.rotate(service.requireActive(first.rawToken()));

        assertEquals(3, repository.all().size());
        assertEquals(first.familyId(), third.familyId());
        assertTrue(repository.all().stream().allMatch(token -> token.revokedAt() == null));
    }

    @Test
    void rotateAfterGraceReportsReuseAndRevokesWholeFamily() {
        IssuedRefreshToken first = service.issue(USER, 0);
        clock.advance(Duration.ofMinutes(1));
        service.rotate(service.requireActive(first.rawToken()));
        clock.advance(Duration.ofSeconds(61));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.rotate(service.requireActive(first.rawToken())));

        assertEquals("refresh_reused", exception.code());
        assertTrue(repository.all().stream().allMatch(token -> token.revokedAt() != null));
    }

    @Test
    void rotateRejectsExpiredToken() {
        IssuedRefreshToken first = service.issue(USER, 0);
        clock.advance(Duration.ofDays(15));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.rotate(service.requireActive(first.rawToken())));

        assertEquals("refresh_expired", exception.code());
    }

    @Test
    void rotateRejectsFamilyPastAbsoluteLifetime() {
        IssuedRefreshToken first = service.issue(USER, 0);
        clock.advance(Duration.ofDays(31));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.rotate(service.requireActive(first.rawToken())));

        assertEquals("refresh_absolute_expired", exception.code());
        assertTrue(repository.all().stream().allMatch(token -> token.revokedAt() != null));
    }

    @Test
    void rotateRejectsRevokedToken() {
        IssuedRefreshToken first = service.issue(USER, 0);
        service.revokeAllForUser(USER, "logout");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.rotate(service.requireActive(first.rawToken())));

        assertEquals("refresh_reused", exception.code());
    }

    @Test
    void rotateRejectsMissingAndUnknownTokens() {
        assertEquals("refresh_missing",
                assertThrows(BusinessException.class, () -> service.requireActive(null)).code());
        assertEquals("refresh_missing",
                assertThrows(BusinessException.class, () -> service.requireActive("  ")).code());
        assertEquals("refresh_invalid",
                assertThrows(BusinessException.class, () -> service.requireActive("unknown-token")).code());
    }

    @Test
    void revokeFamilyOnlyTouchesThatFamily() {
        IssuedRefreshToken first = service.issue(USER, 0);
        IssuedRefreshToken other = service.issue(USER, 0);

        service.revokeFamily(first.familyId(), "logout");

        assertNotNull(stored(first).revokedAt());
        assertNull(stored(other).revokedAt());
        assertEquals("logout", stored(first).revokedReason());
    }

    private AuthRefreshToken stored(IssuedRefreshToken issued) {
        return repository.findByTokenHash(RefreshTokenService.hash(issued.rawToken())).orElseThrow();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeRepository implements AuthRefreshTokenRepository {
        private final Map<String, AuthRefreshToken> byHash = new HashMap<>();

        @Override
        public void save(AuthRefreshToken token) {
            byHash.put(token.tokenHash(), token);
        }

        @Override
        public Optional<AuthRefreshToken> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public int markRotated(String id, String replacedBy, Instant rotatedAt) {
            for (Map.Entry<String, AuthRefreshToken> entry : byHash.entrySet()) {
                AuthRefreshToken token = entry.getValue();
                if (token.id().equals(id)) {
                    if (token.rotatedAt() != null) {
                        return 0;
                    }
                    entry.setValue(copy(token, rotatedAt, token.revokedAt(), token.revokedReason(), replacedBy));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int revokeFamily(String familyId, String reason, Instant now) {
            return revoke(token -> token.familyId().equals(familyId), reason, now);
        }

        @Override
        public int revokeAllForUser(String userId, String reason, Instant now) {
            return revoke(token -> token.userId().equals(userId), reason, now);
        }

        private int revoke(Predicate<AuthRefreshToken> filter, String reason, Instant now) {
            int changed = 0;
            for (Map.Entry<String, AuthRefreshToken> entry : byHash.entrySet()) {
                AuthRefreshToken token = entry.getValue();
                if (filter.test(token) && token.revokedAt() == null) {
                    entry.setValue(copy(token, token.rotatedAt(), now, reason, token.replacedBy()));
                    changed++;
                }
            }
            return changed;
        }

        private List<AuthRefreshToken> all() {
            return List.copyOf(byHash.values());
        }

        private AuthRefreshToken copy(
                AuthRefreshToken token,
                Instant rotatedAt,
                Instant revokedAt,
                String revokedReason,
                String replacedBy) {
            return new AuthRefreshToken(
                    token.id(),
                    token.userId(),
                    token.tokenHash(),
                    token.familyId(),
                    token.familyIssuedAt(),
                    token.tokenVersion(),
                    token.issuedAt(),
                    token.expiresAt(),
                    rotatedAt,
                    revokedAt,
                    revokedReason,
                    replacedBy);
        }
    }
}
