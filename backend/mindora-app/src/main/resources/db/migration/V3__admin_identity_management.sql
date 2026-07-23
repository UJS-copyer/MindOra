ALTER TABLE user_account
    ADD COLUMN display_name VARCHAR(120) NULL AFTER email,
    ADD COLUMN phone VARCHAR(32) NULL AFTER password_hash,
    ADD COLUMN gender VARCHAR(32) NULL AFTER phone;

ALTER TABLE role
    ADD COLUMN display_name VARCHAR(120) NULL AFTER name,
    ADD COLUMN description VARCHAR(255) NULL AFTER display_name,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER description;

UPDATE role
SET display_name = CASE name
        WHEN 'super_admin' THEN '超级管理员'
        WHEN 'user' THEN '普通用户'
        ELSE name
    END,
    description = CASE name
        WHEN 'super_admin' THEN '拥有后台全部管理权限'
        WHEN 'user' THEN '默认注册用户角色'
        ELSE description
    END,
    enabled = TRUE
WHERE deleted = FALSE;

INSERT INTO permission (id, code, description, created_at, updated_at)
VALUES
    ('91000000000000000000000000000006', 'role:manage', 'Manage roles and role permissions', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('91000000000000000000000000000007', 'menu:manage', 'Manage menu and button permissions', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT '90000000000000000000000000000001', id, CURRENT_TIMESTAMP(6)
FROM permission
WHERE code IN ('role:manage', 'menu:manage')
ON DUPLICATE KEY UPDATE deleted = FALSE;
