package com.mindora.user.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindora.user.domain.AuthRefreshToken;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcAuthRefreshTokenRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAuthRefreshTokenRepository repository;

    private static class CapturedUpdate {
        String sql;
        Object[] args;
    }

    private CapturedUpdate capturedUpdate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new JdbcAuthRefreshTokenRepository(jdbcTemplate);
        capturedUpdate = new CapturedUpdate();

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenAnswer(this::captureUpdateAnswer);
    }

    private Object captureUpdateAnswer(InvocationOnMock invocation) {
        capturedUpdate.sql = invocation.getArgument(0);
        Object[] invocationArgs = invocation.getArguments();
        if (invocationArgs.length == 2 && invocationArgs[1] instanceof Object[] array) {
            capturedUpdate.args = array;
        } else {
            capturedUpdate.args = Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length);
        }
        return 1;
    }

    private static String normalizeSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    @Test
    void save_insertsAllColumnsInCorrectOrder() {
        Instant now = Instant.parse("2026-10-18T10:00:00Z");
        Instant expires = Instant.parse("2026-11-01T10:00:00Z");
        Instant rotated = Instant.parse("2026-10-18T10:30:00Z");
        Instant revoked = Instant.parse("2026-10-18T11:00:00Z");

        AuthRefreshToken token = new AuthRefreshToken(
                "id-001",
                "user-001",
                "hash-001",
                "fam-001",
                now,
                1,
                now,
                expires,
                rotated,
                revoked,
                "token_rotated",
                "id-002");

        repository.save(token);

        String sql = normalizeSql(capturedUpdate.sql);
        assertTrue(sql.contains("INSERT INTO auth_refresh_token"));
        assertTrue(sql.contains("id, user_id, token_hash, family_id, family_issued_at, token_version, issued_at, expires_at, rotated_at, revoked_at, revoked_reason, replaced_by"));
        assertTrue(sql.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"));

        assertEquals(12, capturedUpdate.args.length);
        assertEquals("id-001", capturedUpdate.args[0]);
        assertEquals("user-001", capturedUpdate.args[1]);
        assertEquals("hash-001", capturedUpdate.args[2]);
        assertEquals("fam-001", capturedUpdate.args[3]);
        assertEquals(Timestamp.from(now), capturedUpdate.args[4]);
        assertEquals(1, capturedUpdate.args[5]);
        assertEquals(Timestamp.from(now), capturedUpdate.args[6]);
        assertEquals(Timestamp.from(expires), capturedUpdate.args[7]);
        assertEquals(Timestamp.from(rotated), capturedUpdate.args[8]);
        assertEquals(Timestamp.from(revoked), capturedUpdate.args[9]);
        assertEquals("token_rotated", capturedUpdate.args[10]);
        assertEquals("id-002", capturedUpdate.args[11]);
    }

    @Test
    void findByTokenHash_queriesTableAndReturnsMappedResult() {
        AuthRefreshToken expected = new AuthRefreshToken(
                "id-001", "user-001", "hash-001", "fam-001",
                Instant.now(), 0, Instant.now(), Instant.now().plusSeconds(3600),
                null, null, null, null);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = normalizeSql(invocation.getArgument(0));
                    assertTrue(sql.contains("SELECT"));
                    assertTrue(sql.contains("FROM auth_refresh_token"));
                    assertTrue(sql.contains("WHERE token_hash = ?"));

                    Object[] invocationArgs = invocation.getArguments();
                    Object param = invocationArgs.length == 3 && invocationArgs[2] instanceof Object[] array
                            ? array[0]
                            : invocationArgs[2];
                    assertEquals("hash-001", param);
                    return List.of(expected);
                });

        Optional<AuthRefreshToken> actual = repository.findByTokenHash("hash-001");
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    @Test
    void mapRow_nullableColumnsAllNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        Instant exp = Instant.parse("2026-11-01T12:00:00Z");

        when(rs.getString("id")).thenReturn("id-null-test");
        when(rs.getString("user_id")).thenReturn("user-null-test");
        when(rs.getString("token_hash")).thenReturn("hash-null-test");
        when(rs.getString("family_id")).thenReturn("fam-null-test");
        when(rs.getTimestamp("family_issued_at")).thenReturn(Timestamp.from(now));
        when(rs.getInt("token_version")).thenReturn(0);
        when(rs.getTimestamp("issued_at")).thenReturn(Timestamp.from(now));
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(exp));
        when(rs.getTimestamp("rotated_at")).thenReturn(null);
        when(rs.getTimestamp("revoked_at")).thenReturn(null);
        when(rs.getString("revoked_reason")).thenReturn(null);
        when(rs.getString("replaced_by")).thenReturn(null);

        AuthRefreshToken token = JdbcAuthRefreshTokenRepository.mapRow(rs, 1);

        assertEquals("id-null-test", token.id());
        assertEquals("user-null-test", token.userId());
        assertEquals("hash-null-test", token.tokenHash());
        assertEquals("fam-null-test", token.familyId());
        assertEquals(now, token.familyIssuedAt());
        assertEquals(0, token.tokenVersion());
        assertEquals(now, token.issuedAt());
        assertEquals(exp, token.expiresAt());
        assertNull(token.rotatedAt());
        assertNull(token.revokedAt());
        assertNull(token.revokedReason());
        assertNull(token.replacedBy());
    }

    @Test
    void mapRow_nullableColumnsHaveValues() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        Instant exp = Instant.parse("2026-11-01T12:00:00Z");
        Instant rotated = Instant.parse("2026-10-18T12:30:00Z");
        Instant revoked = Instant.parse("2026-10-18T13:00:00Z");

        when(rs.getString("id")).thenReturn("id-val-test");
        when(rs.getString("user_id")).thenReturn("user-val-test");
        when(rs.getString("token_hash")).thenReturn("hash-val-test");
        when(rs.getString("family_id")).thenReturn("fam-val-test");
        when(rs.getTimestamp("family_issued_at")).thenReturn(Timestamp.from(now));
        when(rs.getInt("token_version")).thenReturn(2);
        when(rs.getTimestamp("issued_at")).thenReturn(Timestamp.from(now));
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(exp));
        when(rs.getTimestamp("rotated_at")).thenReturn(Timestamp.from(rotated));
        when(rs.getTimestamp("revoked_at")).thenReturn(Timestamp.from(revoked));
        when(rs.getString("revoked_reason")).thenReturn("reuse_detected");
        when(rs.getString("replaced_by")).thenReturn("id-next");

        AuthRefreshToken token = JdbcAuthRefreshTokenRepository.mapRow(rs, 1);

        assertEquals("id-val-test", token.id());
        assertEquals("user-val-test", token.userId());
        assertEquals("hash-val-test", token.tokenHash());
        assertEquals("fam-val-test", token.familyId());
        assertEquals(now, token.familyIssuedAt());
        assertEquals(2, token.tokenVersion());
        assertEquals(now, token.issuedAt());
        assertEquals(exp, token.expiresAt());
        assertEquals(rotated, token.rotatedAt());
        assertEquals(revoked, token.revokedAt());
        assertEquals("reuse_detected", token.revokedReason());
        assertEquals("id-next", token.replacedBy());
    }

    @Test
    void markRotated_updatesRotatedAtAndReplacedByWhereNull() {
        Instant rotatedAt = Instant.parse("2026-10-18T14:00:00Z");
        int rows = repository.markRotated("token-old", "token-new", rotatedAt);

        assertEquals(1, rows);
        String sql = normalizeSql(capturedUpdate.sql);
        assertTrue(sql.contains("UPDATE auth_refresh_token"));
        assertTrue(sql.contains("SET rotated_at = ?, replaced_by = ?"));
        assertTrue(sql.contains("WHERE id = ? AND rotated_at IS NULL"));

        assertEquals(3, capturedUpdate.args.length);
        assertEquals(Timestamp.from(rotatedAt), capturedUpdate.args[0]);
        assertEquals("token-new", capturedUpdate.args[1]);
        assertEquals("token-old", capturedUpdate.args[2]);
    }

    @Test
    void revokeFamily_updatesRevokedAtAndReasonWhereNull() {
        Instant revokedAt = Instant.parse("2026-10-18T15:00:00Z");
        int rows = repository.revokeFamily("family-001", "refresh_reused", revokedAt);

        assertEquals(1, rows);
        String sql = normalizeSql(capturedUpdate.sql);
        assertTrue(sql.contains("UPDATE auth_refresh_token"));
        assertTrue(sql.contains("SET revoked_at = ?, revoked_reason = ?"));
        assertTrue(sql.contains("WHERE family_id = ? AND revoked_at IS NULL"));

        assertEquals(3, capturedUpdate.args.length);
        assertEquals(Timestamp.from(revokedAt), capturedUpdate.args[0]);
        assertEquals("refresh_reused", capturedUpdate.args[1]);
        assertEquals("family-001", capturedUpdate.args[2]);
    }

    @Test
    void revokeAllForUser_updatesRevokedAtAndReasonWhereNull() {
        Instant revokedAt = Instant.parse("2026-10-18T16:00:00Z");
        int rows = repository.revokeAllForUser("user-001", "user_logout", revokedAt);

        assertEquals(1, rows);
        String sql = normalizeSql(capturedUpdate.sql);
        assertTrue(sql.contains("UPDATE auth_refresh_token"));
        assertTrue(sql.contains("SET revoked_at = ?, revoked_reason = ?"));
        assertTrue(sql.contains("WHERE user_id = ? AND revoked_at IS NULL"));

        assertEquals(3, capturedUpdate.args.length);
        assertEquals(Timestamp.from(revokedAt), capturedUpdate.args[0]);
        assertEquals("user_logout", capturedUpdate.args[1]);
        assertEquals("user-001", capturedUpdate.args[2]);
    }
}
