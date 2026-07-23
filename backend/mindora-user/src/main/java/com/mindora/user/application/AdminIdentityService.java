package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.user.domain.RoleName;
import com.mindora.user.infrastructure.PasswordHasher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class AdminIdentityService {
    private static final String DEFAULT_PASSWORD = "Admin123!";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;

    public AdminIdentityService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    public PageResult<UserRow> listUsers(UserSearch search) {
        ensureJdbc();
        int current = positive(search.current(), 1);
        int size = positive(search.size(), 20);
        QueryParts query = userQuery(search);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account u " + query.where(),
                Long.class,
                query.args().toArray());

        List<Object> args = new ArrayList<>(query.args());
        args.add((current - 1) * size);
        args.add(size);
        List<UserRow> records = jdbcTemplate.query(
                """
                SELECT u.id, u.email, u.display_name, u.phone, u.gender, u.status, u.created_at, u.updated_at
                FROM user_account u
                %s
                ORDER BY u.created_at DESC
                LIMIT ?, ?
                """.formatted(query.where()),
                this::mapUser,
                args.toArray());
        return new PageResult<>(records, current, size, total);
    }

    @Transactional
    public UserRow createUser(UserRequest request) {
        ensureJdbc();
        String email = normalizeEmail(request.userEmail());
        if (emailExists(email, null)) {
            throw new BusinessException("user_email_exists", "Email already exists");
        }

        String userId = PublicIds.newId();
        String password = request.password() == null || request.password().isBlank()
                ? DEFAULT_PASSWORD
                : request.password();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO user_account
                    (id, email, password_hash, display_name, phone, gender, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                email,
                passwordHasher.hash(password),
                blankToNull(request.userName()),
                blankToNull(request.userPhone()),
                normalizeGender(request.userGender()),
                normalizeStatus(request.status()),
                now,
                now);
        replaceUserRoles(userId, request.userRoles());
        return getUser(userId);
    }

    @Transactional
    public UserRow updateUser(String id, UserRequest request) {
        ensureJdbc();
        String userId = normalizePublicId(id);
        String email = normalizeEmail(request.userEmail());
        if (emailExists(email, userId)) {
            throw new BusinessException("user_email_exists", "Email already exists");
        }

        jdbcTemplate.update(
                """
                UPDATE user_account
                SET email = ?,
                    display_name = ?,
                    phone = ?,
                    gender = ?,
                    status = ?,
                    updated_at = ?
                WHERE id = ? AND deleted = FALSE
                """,
                email,
                blankToNull(request.userName()),
                blankToNull(request.userPhone()),
                normalizeGender(request.userGender()),
                normalizeStatus(request.status()),
                Instant.now(),
                userId);
        replaceUserRoles(userId, request.userRoles());
        return getUser(userId);
    }

    public void deleteUser(String id) {
        ensureJdbc();
        jdbcTemplate.update(
                "UPDATE user_account SET deleted = TRUE, status = 'disabled', updated_at = ? WHERE id = ?",
                Instant.now(),
                normalizePublicId(id));
    }

    public PageResult<RoleRow> listRoles(RoleSearch search) {
        ensureJdbc();
        int current = positive(search.current(), 1);
        int size = positive(search.size(), 20);
        QueryParts query = roleQuery(search);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role r " + query.where(),
                Long.class,
                query.args().toArray());

        List<Object> args = new ArrayList<>(query.args());
        args.add((current - 1) * size);
        args.add(size);
        List<RoleRow> records = jdbcTemplate.query(
                """
                SELECT r.id, r.name, r.display_name, r.description, r.enabled, r.created_at
                FROM role r
                %s
                ORDER BY r.created_at ASC
                LIMIT ?, ?
                """.formatted(query.where()),
                this::mapRole,
                args.toArray());
        return new PageResult<>(records, current, size, total);
    }

    @Transactional
    public RoleRow createRole(RoleRequest request) {
        ensureJdbc();
        String roleName = normalizeRoleName(request.roleCode());
        if (roleName.isBlank()) {
            throw new BusinessException("validation_error", "Role code is required");
        }
        if (roleExists(roleName, null)) {
            throw new BusinessException("role_exists", "Role already exists");
        }
        String roleId = PublicIds.newId();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO role (id, name, display_name, description, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                roleId,
                roleName,
                blankToNull(request.roleName()),
                blankToNull(request.description()),
                request.enabled() == null || request.enabled(),
                now,
                now);
        replaceRolePermissions(roleId, request.permissions());
        return getRole(roleId);
    }

    @Transactional
    public RoleRow updateRole(String id, RoleRequest request) {
        ensureJdbc();
        String roleId = normalizePublicId(id);
        String roleName = normalizeRoleName(request.roleCode());
        if (roleName.isBlank()) {
            throw new BusinessException("validation_error", "Role code is required");
        }
        if (roleExists(roleName, roleId)) {
            throw new BusinessException("role_exists", "Role already exists");
        }
        jdbcTemplate.update(
                """
                UPDATE role
                SET name = ?, display_name = ?, description = ?, enabled = ?, updated_at = ?
                WHERE id = ? AND deleted = FALSE
                """,
                roleName,
                blankToNull(request.roleName()),
                blankToNull(request.description()),
                request.enabled() == null || request.enabled(),
                Instant.now(),
                roleId);
        if (request.permissions() != null) {
            replaceRolePermissions(roleId, request.permissions());
        }
        return getRole(roleId);
    }

    public void deleteRole(String id) {
        ensureJdbc();
        String roleId = normalizePublicId(id);
        RoleRow role = getRole(roleId);
        if (Set.of("R_SUPER", "R_USER").contains(role.roleCode())) {
            throw new BusinessException("role_protected", "Default roles cannot be deleted");
        }
        jdbcTemplate.update(
                "UPDATE role SET deleted = TRUE, updated_at = ? WHERE id = ?",
                Instant.now(),
                roleId);
    }

    public List<PermissionRow> listPermissions() {
        ensureJdbc();
        return jdbcTemplate.query(
                """
                SELECT code, description
                FROM permission
                WHERE deleted = FALSE
                ORDER BY code
                """,
                (rs, rowNum) -> new PermissionRow(
                        rs.getString("code"),
                        rs.getString("description")));
    }

    public Set<String> rolePermissions(String roleId) {
        ensureJdbc();
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                SELECT p.code
                FROM role_permission rp
                JOIN permission p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                  AND rp.deleted = FALSE
                  AND p.deleted = FALSE
                ORDER BY p.code
                """,
                (rs, rowNum) -> rs.getString("code"),
                normalizePublicId(roleId)));
    }

    @Transactional
    public Set<String> updateRolePermissions(String roleId, PermissionUpdateRequest request) {
        ensureJdbc();
        String normalizedRoleId = normalizePublicId(roleId);
        replaceRolePermissions(normalizedRoleId, request.permissions());
        return rolePermissions(normalizedRoleId);
    }

    private void replaceUserRoles(String userId, Set<String> roleCodes) {
        Set<String> roles = roleCodes == null || roleCodes.isEmpty()
                ? Set.of(RoleName.USER.value())
                : roleCodes;
        jdbcTemplate.update("UPDATE user_role SET deleted = TRUE WHERE user_id = ?", userId);
        Instant now = Instant.now();
        for (String roleCode : roles) {
            String roleName = normalizeRoleName(roleCode);
            String roleId = jdbcTemplate.query(
                            "SELECT id FROM role WHERE name = ? AND deleted = FALSE AND enabled = TRUE",
                            (rs, rowNum) -> rs.getString("id"),
                            roleName)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("role_not_found", "Role not found"));
            jdbcTemplate.update(
                    """
                    INSERT INTO user_role (user_id, role_id, created_at, deleted)
                    VALUES (?, ?, ?, FALSE)
                    ON DUPLICATE KEY UPDATE deleted = FALSE
                    """,
                    userId,
                    roleId,
                    now);
        }
    }

    private void replaceRolePermissions(String roleId, Set<String> permissions) {
        if (permissions == null) {
            return;
        }
        jdbcTemplate.update("UPDATE role_permission SET deleted = TRUE WHERE role_id = ?", roleId);
        Instant now = Instant.now();
        for (String permissionCode : permissions) {
            String permissionId = jdbcTemplate.query(
                            "SELECT id FROM permission WHERE code = ? AND deleted = FALSE",
                            (rs, rowNum) -> rs.getString("id"),
                            permissionCode)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("permission_not_found", "Permission not found"));
            jdbcTemplate.update(
                    """
                    INSERT INTO role_permission (role_id, permission_id, created_at, deleted)
                    VALUES (?, ?, ?, FALSE)
                    ON DUPLICATE KEY UPDATE deleted = FALSE
                    """,
                    roleId,
                    permissionId,
                    now);
        }
    }

    private UserRow getUser(String userId) {
        return jdbcTemplate.query(
                        """
                        SELECT u.id, u.email, u.display_name, u.phone, u.gender, u.status, u.created_at, u.updated_at
                        FROM user_account u
                        WHERE u.id = ? AND u.deleted = FALSE
                        """,
                        this::mapUser,
                        normalizePublicId(userId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("user_not_found", "User not found"));
    }

    private RoleRow getRole(String roleId) {
        return jdbcTemplate.query(
                        """
                        SELECT r.id, r.name, r.display_name, r.description, r.enabled, r.created_at
                        FROM role r
                        WHERE r.id = ? AND r.deleted = FALSE
                        """,
                        this::mapRole,
                        normalizePublicId(roleId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("role_not_found", "Role not found"));
    }

    private UserRow mapUser(ResultSet rs, int rowNum) throws SQLException {
        String userId = rs.getString("id");
        String email = rs.getString("email");
        String displayName = rs.getString("display_name");
        return new UserRow(
                userId,
                displayName == null || displayName.isBlank() ? nameFromEmail(email) : displayName,
                normalizeGender(rs.getString("gender")),
                rs.getString("phone"),
                email,
                rs.getString("status"),
                roleCodesForUser(userId),
                time(rs.getTimestamp("created_at")),
                time(rs.getTimestamp("updated_at")));
    }

    private RoleRow mapRole(ResultSet rs, int rowNum) throws SQLException {
        String roleId = rs.getString("id");
        String name = rs.getString("name");
        String displayName = rs.getString("display_name");
        return new RoleRow(
                roleId,
                displayName == null || displayName.isBlank() ? labelForRole(name) : displayName,
                roleCodeFor(name),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                time(rs.getTimestamp("created_at")),
                rolePermissions(roleId));
    }

    private Set<String> roleCodesForUser(String userId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                SELECT r.name
                FROM user_role ur
                JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND ur.deleted = FALSE AND r.deleted = FALSE
                ORDER BY r.name
                """,
                (rs, rowNum) -> roleCodeFor(rs.getString("name")),
                userId));
    }

    private QueryParts userQuery(UserSearch search) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        conditions.add("u.deleted = FALSE");
        like(conditions, args, "COALESCE(u.display_name, '')", search.userName());
        like(conditions, args, "u.phone", search.userPhone());
        like(conditions, args, "u.email", search.userEmail());
        equals(conditions, args, "u.status", normalizeNullableStatus(search.status()));
        equals(conditions, args, "u.gender", normalizeNullableGender(search.userGender()));
        return new QueryParts("WHERE " + String.join(" AND ", conditions), args);
    }

    private QueryParts roleQuery(RoleSearch search) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        conditions.add("r.deleted = FALSE");
        like(conditions, args, "COALESCE(r.display_name, '')", search.roleName());
        like(conditions, args, "r.name", normalizeNullableRoleName(search.roleCode()));
        like(conditions, args, "COALESCE(r.description, '')", search.description());
        if (search.enabled() != null) {
            conditions.add("r.enabled = ?");
            args.add(search.enabled());
        }
        return new QueryParts("WHERE " + String.join(" AND ", conditions), args);
    }

    private void like(List<String> conditions, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column + " LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }

    private void equals(List<String> conditions, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column + " = ?");
            args.add(value);
        }
    }

    private boolean emailExists(String email, String excludedUserId) {
        List<Object> args = new ArrayList<>();
        args.add(email);
        String sql = "SELECT COUNT(*) FROM user_account WHERE email = ? AND deleted = FALSE";
        if (excludedUserId != null) {
            sql += " AND id <> ?";
            args.add(excludedUserId);
        }
        return jdbcTemplate.queryForObject(sql, Long.class, args.toArray()) > 0;
    }

    private boolean roleExists(String roleName, String excludedRoleId) {
        List<Object> args = new ArrayList<>();
        args.add(roleName);
        String sql = "SELECT COUNT(*) FROM role WHERE name = ? AND deleted = FALSE";
        if (excludedRoleId != null) {
            sql += " AND id <> ?";
            args.add(excludedRoleId);
        }
        return jdbcTemplate.queryForObject(sql, Long.class, args.toArray()) > 0;
    }

    private void ensureJdbc() {
        if (jdbcTemplate == null) {
            throw new BusinessException("admin_storage_unavailable", "Admin storage is unavailable");
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private String normalizePublicId(String id) {
        return PublicIds.toPublicId(PublicIds.toUuid(id));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new BusinessException("validation_error", "Valid email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRoleName(String roleCode) {
        String value = roleCode == null ? "" : roleCode.trim().toLowerCase(Locale.ROOT);
        if ("r_super".equals(value)) {
            return RoleName.SUPER_ADMIN.value();
        }
        if ("r_user".equals(value)) {
            return RoleName.USER.value();
        }
        if ("r_admin".equals(value)) {
            return "admin";
        }
        return value.startsWith("r_") ? value.substring(2) : value;
    }

    private String normalizeNullableRoleName(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return normalizeRoleName(roleCode);
    }

    private String roleCodeFor(String roleName) {
        String normalized = normalizeRoleName(roleName);
        if (RoleName.SUPER_ADMIN.value().equals(normalized)) {
            return "R_SUPER";
        }
        if (RoleName.USER.value().equals(normalized)) {
            return "R_USER";
        }
        return "R_" + normalized.toUpperCase(Locale.ROOT);
    }

    private String labelForRole(String roleName) {
        String normalized = normalizeRoleName(roleName);
        if (RoleName.SUPER_ADMIN.value().equals(normalized)) {
            return "超级管理员";
        }
        if (RoleName.USER.value().equals(normalized)) {
            return "普通用户";
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "active" : status.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(value)) {
            return "active";
        }
        if ("2".equals(value) || "4".equals(value)) {
            return "disabled";
        }
        return value;
    }

    private String normalizeNullableStatus(String status) {
        return status == null || status.isBlank() ? null : normalizeStatus(status);
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String value = gender.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(value) || "male".equals(value) || "男".equals(value)) {
            return "male";
        }
        if ("2".equals(value) || "female".equals(value) || "女".equals(value)) {
            return "female";
        }
        return value;
    }

    private String normalizeNullableGender(String gender) {
        return gender == null || gender.isBlank() ? null : normalizeGender(gender);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nameFromEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String time(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private record QueryParts(String where, List<Object> args) {
    }

    public record PageResult<T>(
            List<T> records,
            int current,
            int size,
            long total) {
    }

    public record UserSearch(
            Integer current,
            Integer size,
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status) {
    }

    public record UserRequest(
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status,
            Set<String> userRoles,
            String password) {
    }

    public record UserRow(
            String id,
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status,
            Set<String> userRoles,
            String createTime,
            String updateTime) {
    }

    public record RoleSearch(
            Integer current,
            Integer size,
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            String startTime,
            String endTime) {
    }

    public record RoleRequest(
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            Set<String> permissions) {
    }

    public record RoleRow(
            String roleId,
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            String createTime,
            Set<String> permissions) {
    }

    public record PermissionRow(
            String code,
            String description) {
    }

    public record PermissionUpdateRequest(
            Set<String> permissions) {
    }
}
