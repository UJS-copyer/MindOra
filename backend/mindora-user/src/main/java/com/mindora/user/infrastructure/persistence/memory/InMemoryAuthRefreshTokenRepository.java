package com.mindora.user.infrastructure.persistence.memory;

import com.mindora.user.application.port.AuthRefreshTokenRepository;
import com.mindora.user.domain.AuthRefreshToken;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** 测试与无 DB 场景的刷新令牌仓储（语义与 JDBC 实现一致）。 */
public class InMemoryAuthRefreshTokenRepository implements AuthRefreshTokenRepository {
    private final Map<String, AuthRefreshToken> byHash = new ConcurrentHashMap<>();

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
            if (!token.id().equals(id)) {
                continue;
            }
            if (token.rotatedAt() != null || token.revokedAt() != null) {
                return 0;
            }
            entry.setValue(copy(token, rotatedAt, token.revokedAt(), token.revokedReason(), replacedBy));
            return 1;
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
