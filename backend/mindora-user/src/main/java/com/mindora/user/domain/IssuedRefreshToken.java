package com.mindora.user.domain;

import java.time.Instant;

/**
 * 刚签发的刷新令牌。
 *
 * <p>{@code rawToken} 只在此刻存在一次，服务端只保存它的 SHA-256；调用方（a3 的端点）
 * 负责把它写进 HttpOnly Cookie。{@code familyId} 用于登出/重用时整族撤销。
 */
public record IssuedRefreshToken(
        String rawToken,
        String familyId,
        Instant expiresAt) {
}
