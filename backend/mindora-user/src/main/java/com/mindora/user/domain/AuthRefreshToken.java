package com.mindora.user.domain;

import java.time.Instant;

public record AuthRefreshToken(
        String id,
        String userId,
        String tokenHash,
        String familyId,
        Instant familyIssuedAt,
        int tokenVersion,
        Instant issuedAt,
        Instant expiresAt,
        Instant rotatedAt,
        Instant revokedAt,
        String revokedReason,
        String replacedBy) {
}
