INSERT INTO role (id, name, created_at, updated_at)
VALUES
    ('90000000000000000000000000000001', 'super_admin', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('90000000000000000000000000000002', 'user', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO permission (id, code, description, created_at, updated_at)
VALUES
    ('91000000000000000000000000000001', 'blog:read', 'Read blog content', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('91000000000000000000000000000002', 'blog:write', 'Create and edit blog content', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('91000000000000000000000000000003', 'blog:publish', 'Publish and unpublish blog content', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('91000000000000000000000000000004', 'asset:manage', 'Upload and manage assets', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('91000000000000000000000000000005', 'user:manage', 'Manage users', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT '90000000000000000000000000000001', id, CURRENT_TIMESTAMP(6)
FROM permission
WHERE code IN ('blog:read', 'blog:write', 'blog:publish', 'asset:manage', 'user:manage')
ON DUPLICATE KEY UPDATE deleted = FALSE;

INSERT INTO user_account (id, email, password_hash, created_at, updated_at)
VALUES (
    '92000000000000000000000000000001',
    'admin@mindora.local',
    '3eb3fe66b31e3b4d10fa70b5cad49c7112294af6ae4e476a1c405155d45aa121',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO auth_account (id, user_id, provider, provider_subject, password_hash, created_at, updated_at)
VALUES (
    '93000000000000000000000000000001',
    '92000000000000000000000000000001',
    'password',
    'admin@mindora.local',
    '3eb3fe66b31e3b4d10fa70b5cad49c7112294af6ae4e476a1c405155d45aa121',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    updated_at = CURRENT_TIMESTAMP(6),
    deleted = FALSE;

INSERT INTO user_role (user_id, role_id, created_at)
VALUES ('92000000000000000000000000000001', '90000000000000000000000000000001', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE deleted = FALSE;
