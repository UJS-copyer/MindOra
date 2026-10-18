package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.user.application.port.AuthRefreshTokenRepository;
import com.mindora.user.domain.AuthRefreshToken;
import com.mindora.user.domain.IssuedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 刷新令牌生命周期（design-12 §3，落库表见 V9）。
 *
 * <p>四条规则：
 * 1. 滑动有限度：每次刷新把过期时间推到 {@code now + refreshTtl}，但家族出生时间不变，
 *    家族总年龄超过 {@code absoluteTtl} 即要求重新登录；
 * 2. 宽限期：旧令牌轮换后 {@code grace} 内再次使用视为多标签页并发，直接再发一枚同族令牌；
 * 3. 重用检测：超过宽限使用已轮换令牌，撤销整个家族并返回 {@code refresh_reused}；
 * 4. 登出/禁用：按族或按用户撤销全部记录（{@code access token} 侧由 token_version 负责）。
 *
 * <p>本轮不接 HTTP：Cookie 写入、401 语义与端点编排归 a3。
 */
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final AuthRefreshTokenRepository repository;
    private final Duration refreshTtl;
    private final Duration absoluteTtl;
    private final Duration grace;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            AuthRefreshTokenRepository repository,
            Duration refreshTtl,
            Duration absoluteTtl,
            Duration grace,
            Clock clock) {
        this.repository = repository;
        this.refreshTtl = refreshTtl;
        this.absoluteTtl = absoluteTtl;
        this.grace = grace;
        this.clock = clock;
    }

    /** 登录成功后的第一枚刷新令牌（新建家族）。 */
    public IssuedRefreshToken issue(String userId, int tokenVersion) {
        Instant now = Instant.now(clock);
        String familyId = PublicIds.newId();
        return insert(userId, tokenVersion, familyId, now, now, PublicIds.newId());
    }

    /**
     * 用旧刷新令牌换新令牌。
     *
     * @throws BusinessException {@code refresh_missing / refresh_invalid / refresh_expired /
     *     refresh_absolute_expired / refresh_reused}
     */
    public IssuedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("refresh_missing", "Refresh token is missing");
        }
        String tokenHash = hash(rawToken);
        AuthRefreshToken current = repository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("refresh_invalid", "Refresh token is invalid"));
        Instant now = Instant.now(clock);

        if (current.revokedAt() != null) {
            throw new BusinessException("refresh_reused", "Refresh token has been revoked");
        }
        if (now.isAfter(current.familyIssuedAt().plus(absoluteTtl))) {
            repository.revokeFamily(current.familyId(), "refresh_absolute_expired", now);
            throw new BusinessException(
                    "refresh_absolute_expired", "Refresh session has exceeded its absolute lifetime");
        }
        if (now.isAfter(current.expiresAt())) {
            throw new BusinessException("refresh_expired", "Refresh token has expired");
        }

        if (current.rotatedAt() == null) {
            String newId = PublicIds.newId();
            if (repository.markRotated(current.id(), newId, now) > 0) {
                return insert(
                        current.userId(),
                        current.tokenVersion(),
                        current.familyId(),
                        current.familyIssuedAt(),
                        now,
                        newId);
            }
            // 并发轮换：另一请求先改了 rotated_at，按「已轮换」分支继续判定。
            current = repository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new BusinessException("refresh_invalid", "Refresh token is invalid"));
        }

        Instant rotatedAt = current.rotatedAt();
        if (rotatedAt != null && !now.isAfter(rotatedAt.plus(grace))) {
            return insert(
                    current.userId(),
                    current.tokenVersion(),
                    current.familyId(),
                    current.familyIssuedAt(),
                    now,
                    PublicIds.newId());
        }
        repository.revokeFamily(current.familyId(), "refresh_reused", now);
        throw new BusinessException("refresh_reused", "Refresh token reuse detected");
    }

    public void revokeFamily(String familyId, String reason) {
        repository.revokeFamily(familyId, reason, Instant.now(clock));
    }

    public void revokeAllForUser(String userId, String reason) {
        repository.revokeAllForUser(userId, reason, Instant.now(clock));
    }

    private IssuedRefreshToken insert(
            String userId,
            int tokenVersion,
            String familyId,
            Instant familyIssuedAt,
            Instant now,
            String id) {
        String rawToken = newRawToken();
        Instant expiresAt = now.plus(refreshTtl);
        repository.save(new AuthRefreshToken(
                id,
                userId,
                hash(rawToken),
                familyId,
                familyIssuedAt,
                tokenVersion,
                now,
                expiresAt,
                null,
                null,
                null,
                null));
        return new IssuedRefreshToken(rawToken, familyId, expiresAt);
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
