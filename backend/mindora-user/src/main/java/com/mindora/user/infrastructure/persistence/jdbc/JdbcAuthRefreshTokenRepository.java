package com.mindora.user.infrastructure.persistence.jdbc;

import com.mindora.user.application.port.AuthRefreshTokenRepository;
import com.mindora.user.domain.AuthRefreshToken;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcAuthRefreshTokenRepository implements AuthRefreshTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AuthRefreshToken token) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_refresh_token (
                    id, user_id, token_hash, family_id, family_issued_at,
                    token_version, issued_at, expires_at, rotated_at, revoked_at,
                    revoked_reason, replaced_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.familyId(),
                timestamp(token.familyIssuedAt()),
                token.tokenVersion(),
                timestamp(token.issuedAt()),
                timestamp(token.expiresAt()),
                timestamp(token.rotatedAt()),
                timestamp(token.revokedAt()),
                token.revokedReason(),
                token.replacedBy());
    }

    @Override
    public Optional<AuthRefreshToken> findByTokenHash(String tokenHash) {
        List<AuthRefreshToken> results = jdbcTemplate.query(
                """
                SELECT id, user_id, token_hash, family_id, family_issued_at,
                       token_version, issued_at, expires_at, rotated_at, revoked_at,
                       revoked_reason, replaced_by
                FROM auth_refresh_token
                WHERE token_hash = ?
                """,
                JdbcAuthRefreshTokenRepository::mapRow,
                tokenHash);
        return results.stream().findFirst();
    }

    @Override
    public int markRotated(String id, String replacedBy, Instant rotatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE auth_refresh_token
                SET rotated_at = ?, replaced_by = ?
                WHERE id = ? AND rotated_at IS NULL
                """,
                timestamp(rotatedAt),
                replacedBy,
                id);
    }

    @Override
    public int revokeFamily(String familyId, String reason, Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE auth_refresh_token
                SET revoked_at = ?, revoked_reason = ?
                WHERE family_id = ? AND revoked_at IS NULL
                """,
                timestamp(now),
                reason,
                familyId);
    }

    @Override
    public int revokeAllForUser(String userId, String reason, Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE auth_refresh_token
                SET revoked_at = ?, revoked_reason = ?
                WHERE user_id = ? AND revoked_at IS NULL
                """,
                timestamp(now),
                reason,
                userId);
    }

    static AuthRefreshToken mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuthRefreshToken(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("token_hash"),
                rs.getString("family_id"),
                instant(rs.getTimestamp("family_issued_at")),
                rs.getInt("token_version"),
                instant(rs.getTimestamp("issued_at")),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("rotated_at")),
                instant(rs.getTimestamp("revoked_at")),
                rs.getString("revoked_reason"),
                rs.getString("replaced_by"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
