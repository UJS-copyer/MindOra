package com.mindora.user.infrastructure;

import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JdbcUserRepository implements UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query(
                        """
                        SELECT u.id, u.email, u.password_hash
                        FROM user_account u
                        WHERE u.email = ? AND u.deleted = FALSE AND u.status = 'active'
                        """,
                        this::mapUser,
                        email)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount user) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO user_account (id, email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    password_hash = VALUES(password_hash),
                    updated_at = VALUES(updated_at),
                    deleted = FALSE,
                    status = 'active'
                """,
                publicId(user.id()),
                user.email(),
                user.passwordHash(),
                now,
                now);

        for (RoleName role : user.roles()) {
            String roleId = jdbcTemplate.query(
                            "SELECT id FROM role WHERE name = ? AND deleted = FALSE",
                            (rs, rowNum) -> rs.getString("id"),
                            role.name().toLowerCase())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing role: " + role));
            jdbcTemplate.update(
                    """
                    INSERT INTO user_role (user_id, role_id, created_at)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE deleted = FALSE
                    """,
                    publicId(user.id()),
                    roleId,
                    now);
        }
        return user;
    }

    private UserAccount mapUser(ResultSet rs, int rowNum) throws SQLException {
        String userId = rs.getString("id");
        EnumSet<RoleName> roles = EnumSet.noneOf(RoleName.class);
        jdbcTemplate.query(
                        """
                        SELECT r.name
                        FROM user_role ur
                        JOIN role r ON r.id = ur.role_id
                        WHERE ur.user_id = ? AND ur.deleted = FALSE AND r.deleted = FALSE
                        """,
                        (roleRs, ignored) -> {
                            roles.add(RoleName.valueOf(roleRs.getString("name").toUpperCase()));
                            return null;
                        },
                        userId);
        return new UserAccount(
                uuid(userId),
                rs.getString("email"),
                rs.getString("password_hash"),
                roles);
    }

    private String publicId(UUID id) {
        return id.toString().replace("-", "");
    }

    private UUID uuid(String id) {
        String value = id;
        return UUID.fromString(
                value.substring(0, 8)
                        + "-"
                        + value.substring(8, 12)
                        + "-"
                        + value.substring(12, 16)
                        + "-"
                        + value.substring(16, 20)
                        + "-"
                        + value.substring(20));
    }
}
