-- 契约来源 design-12 §5；本文件只追加、回填幂等。

ALTER TABLE user_account ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE auth_refresh_token (
  id               CHAR(32)     NOT NULL,
  user_id          CHAR(32)     NOT NULL,
  token_hash       CHAR(64)     NOT NULL,
  family_id        CHAR(32)     NOT NULL,
  family_issued_at TIMESTAMP(6) NOT NULL,
  token_version    INT          NOT NULL,
  issued_at        TIMESTAMP(6) NOT NULL,
  expires_at       TIMESTAMP(6) NOT NULL,
  rotated_at       TIMESTAMP(6) NULL,
  revoked_at       TIMESTAMP(6) NULL,
  revoked_reason   VARCHAR(32)  NULL,
  replaced_by      CHAR(32)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_family (family_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 密码身份回填（幂等）：一个账号一条 provider='password'
INSERT INTO auth_account (id, user_id, provider, provider_subject, password_hash, created_at, updated_at, deleted)
SELECT REPLACE(UUID(), '-', ''), u.id, 'password', u.id, u.password_hash, u.created_at, u.updated_at, FALSE
FROM user_account u
WHERE u.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM auth_account a WHERE a.user_id = u.id AND a.provider = 'password');
