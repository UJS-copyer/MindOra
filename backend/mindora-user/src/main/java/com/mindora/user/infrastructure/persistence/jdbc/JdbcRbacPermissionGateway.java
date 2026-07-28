package com.mindora.user.infrastructure.persistence.jdbc;

import com.mindora.user.application.port.RbacPermissionGateway;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcRbacPermissionGateway implements RbacPermissionGateway {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRbacPermissionGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> permissionsForRoles(Set<String> roles) {
        try {
            String placeholders = roles.stream().map(role -> "?").collect(Collectors.joining(", "));
            Object[] args = roles.stream().map(this::normalizeRoleName).toArray();
            return new LinkedHashSet<>(jdbcTemplate.query(
                    """
                    SELECT DISTINCT p.code
                    FROM role_permission rp
                    JOIN role r ON r.id = rp.role_id
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE r.name IN (%s)
                      AND r.deleted = FALSE
                      AND rp.deleted = FALSE
                      AND p.deleted = FALSE
                    """.formatted(placeholders),
                    (rs, rowNum) -> rs.getString("code"),
                    args));
        } catch (DataAccessException ignored) {
            // Keep local development usable before the RBAC tables are initialized.
            return Set.of();
        }
    }

    private String normalizeRoleName(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
