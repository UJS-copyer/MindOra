package com.mindora.user.application.port;

import com.mindora.user.domain.AuthRefreshToken;
import java.time.Instant;
import java.util.Optional;

public interface AuthRefreshTokenRepository {

    void save(AuthRefreshToken token);

    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

    int markRotated(String id, String replacedBy, Instant rotatedAt);

    int revokeFamily(String familyId, String reason, Instant now);

    int revokeAllForUser(String userId, String reason, Instant now);
}
