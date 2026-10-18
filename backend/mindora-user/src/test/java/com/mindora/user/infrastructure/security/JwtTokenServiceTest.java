package com.mindora.user.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.TokenVerification;
import com.mindora.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    private static final String SECRET = "stage-3.6a-jwt-test-secret-0123456789abcdef";
    private static final String OTHER_SECRET = "stage-3.6a-jwt-other-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtTokenService service = new JwtTokenService(SECRET, clock, TTL);

    @Test
    void issuesJwtWithContractClaims() {
        String token = service.issue(user(7));

        Claims claims = claims(token, SECRET);
        assertEquals(USER_ID.toString().replace("-", ""), claims.getSubject());
        assertEquals("reader@example.com", claims.get("email", String.class));
        assertEquals(List.of("super_admin", "user"), claims.get("roles", List.class));
        assertEquals(7, claims.get("tv", Integer.class));
        assertEquals(NOW.getEpochSecond(), claims.getIssuedAt().toInstant().getEpochSecond());
        assertEquals(NOW.plus(TTL).getEpochSecond(), claims.getExpiration().toInstant().getEpochSecond());
        assertTrue(!token.startsWith("Bearer "));
    }

    @Test
    void verifiesBareAndBearerPrefixedTokens() {
        String token = service.issue(user(0));

        TokenVerification bare = service.verify(token);
        TokenVerification prefixed = service.verify("Bearer " + token);

        TokenPrincipal fromBare = assertInstanceOf(TokenVerification.Valid.class, bare).principal();
        TokenPrincipal fromPrefixed = assertInstanceOf(TokenVerification.Valid.class, prefixed).principal();
        assertEquals(USER_ID, fromBare.userId());
        assertEquals(Set.of("super_admin", "user"), fromBare.roles());
        assertEquals(fromBare, fromPrefixed);
    }

    @Test
    void rejectsExpiredTokenButKeepsItDistinguishable() {
        String token = service.issue(user(0));
        JwtTokenService later = new JwtTokenService(
                SECRET, Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC), TTL);

        assertInstanceOf(TokenVerification.Expired.class, later.verify(token));
    }

    @Test
    void rejectsTamperedPayloadAndForeignSignature() {
        String token = service.issue(user(0));
        String[] segments = token.split("\\.");
        String tampered = segments[0] + "." + segments[1] + "x." + segments[2];

        assertInstanceOf(TokenVerification.Invalid.class, service.verify(tampered));
        assertInstanceOf(TokenVerification.Invalid.class, service.verify("garbage"));

        String foreign = new JwtTokenService(OTHER_SECRET, clock, TTL).issue(user(0));
        assertInstanceOf(TokenVerification.Invalid.class, service.verify(foreign));
    }

    @Test
    void treatsMissingHeaderAsAnonymousButMalformedAsInvalid() {
        assertInstanceOf(TokenVerification.Missing.class, service.verify(null));
        assertInstanceOf(TokenVerification.Missing.class, service.verify("   "));
        assertInstanceOf(TokenVerification.Invalid.class, service.verify("Bearer "));
        assertInstanceOf(TokenVerification.Invalid.class, service.verify("Bearer not-a-jwt"));
    }

    private UserAccount user(int tokenVersion) {
        return new UserAccount(
                USER_ID, "reader@example.com", "irrelevant", Set.of("Super-Admin", "user"), tokenVersion);
    }

    private Claims claims(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> java.util.Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertNotNull(claims);
        return claims;
    }
}
