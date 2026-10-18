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
import java.util.Optional;

/**
 * 刷新令牌生命周期（design-12 §3，落库表见 V9）。
 *
 * <p>职责拆成两步，便于端点先校验账户状态再轮换：
 * <ol>
 *   <li>{@link #requireActive(String)}：缺失/查不到/已撤销/家族超龄/已过期；</li>
 *   <li>{@link #rotate(AuthRefreshToken)}：真正轮换（含宽限与重用检测）。</li>
 * </ol>
 *
 * <p>本轮不接 HTTP：Cookie 写入与 401 语义在 AuthSessionService / AuthController。
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

    /** 按原始串查记录（登出等场景只需要知道是谁），不做有效性判定。 */
    public Optional<AuthRefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(rawToken));
    }

    /**
     * 校验一枚刷新令牌仍可用于换新。
     *
     * @throws BusinessException {@code refresh_missing / refresh_invalid / refresh_reused /
     *     refresh_absolute_expired / refresh_expired}
     */
    public AuthRefreshToken requireActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("refresh_missing", "Refresh token is missing");
        }
        AuthRefreshToken current = repository.findByTokenHash(hash(rawToken))
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
        return current;
    }

    /**
     * 轮换一枚已通过 {@link #requireActive(String)} 的令牌。
     *
     * @throws BusinessException {@code refresh_reused}（宽限外重用，撤销整族）或
     *     {@code refresh_invalid}（并发期间记录被删）
     */
    public IssuedRefreshToken rotate(AuthRefreshToken current) {
        Instant now = Instant.now(clock);
        if (current.revokedAt() != null) {
            throw new BusinessException("refresh_reused", "Refresh token has been revoked");
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
            // 并发轮换：另一请求先改了 rotated_at（或已撤销），重读后按已轮换分支判定。
            current = repository.findByTokenHash(current.tokenHash())
                    .orElseThrow(() -> new BusinessException("refresh_invalid", "Refresh token is invalid"));
            if (current.revokedAt() != null) {
                throw new BusinessException("refresh_reused", "Refresh token has been revoked");
            }
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
