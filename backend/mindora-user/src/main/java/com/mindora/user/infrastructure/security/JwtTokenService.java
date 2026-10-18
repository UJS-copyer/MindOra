package com.mindora.user.infrastructure.security;

import com.mindora.common.id.PublicIds;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.TokenVerification;
import com.mindora.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * 基于 JJWT 的访问令牌服务（契约见 design-12 §2.1 / §7）。
 *
 * <p>与旧实现的三点差异：
 * 1. 令牌带 {@code exp}（默认 30 分钟）与 {@code tv}（账户令牌版本），令牌因此会自然过期；
 * 2. {@code sub} 统一为 32 位 public id（库里 user_account.id 的口径），校验时还原为 UUID；
 * 3. 返回裸 JWS，不再自带 {@code "Bearer "} 前缀；校验时两种写法都接受。
 */
public class JwtTokenService implements TokenService {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final Clock clock;
    private final Duration accessTtl;

    public JwtTokenService(String secret, Clock clock, Duration accessTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
        this.accessTtl = accessTtl;
    }

    @Override
    public String issue(UserAccount user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(PublicIds.toPublicId(user.id()))
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLES, normalizedRoles(user.roles()))
                .claim(CLAIM_TOKEN_VERSION, user.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    @Override
    public TokenVerification verify(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        if (token == null) {
            return new TokenVerification.Missing();
        }
        if (token.isBlank() || token.indexOf(' ') >= 0) {
            return new TokenVerification.Invalid();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .sig()
                    .add(Jwts.SIG.HS256)
                    .and()
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            TokenPrincipal principal = principal(claims);
            return principal == null
                    ? new TokenVerification.Invalid()
                    : new TokenVerification.Valid(principal);
        } catch (ExpiredJwtException exception) {
            return new TokenVerification.Expired();
        } catch (JwtException | IllegalArgumentException exception) {
            return new TokenVerification.Invalid();
        }
    }

    private TokenPrincipal principal(Claims claims) {
        String subject = claims.getSubject();
        String email = claims.get(CLAIM_EMAIL, String.class);
        Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
        if (subject == null || subject.isBlank() || email == null || tokenVersion == null || rawRoles == null) {
            return null;
        }
        try {
            UUID userId = PublicIds.toUuid(subject);
            Set<String> roles = new LinkedHashSet<>();
            rawRoles.stream()
                    .map(String::valueOf)
                    .map(JwtTokenService::normalizeRoleName)
                    .filter(role -> !role.isBlank())
                    .sorted()
                    .forEach(roles::add);
            return new TokenPrincipal(userId, email, Set.copyOf(roles), tokenVersion);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 返回待解析的裸令牌；请求没有携带身份时返回 null。 */
    private static String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    private static List<String> normalizedRoles(Set<String> roles) {
        return roles == null
                ? List.of()
                : roles.stream()
                        .map(JwtTokenService::normalizeRoleName)
                        .filter(role -> !role.isBlank())
                        .sorted()
                        .toList();
    }

    private static String normalizeRoleName(String role) {
        return role == null ? "" : role.trim().toLowerCase().replace('-', '_');
    }
}
