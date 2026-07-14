ALTER TABLE blog_article DROP FOREIGN KEY fk_blog_article_category;
ALTER TABLE blog_article_tag DROP FOREIGN KEY fk_blog_article_tag_article;
ALTER TABLE blog_article_tag DROP FOREIGN KEY fk_blog_article_tag_tag;

UPDATE category SET id = REPLACE(id, '-', '');
UPDATE tag SET id = REPLACE(id, '-', '');
UPDATE blog_article
SET id = REPLACE(id, '-', ''),
    cover_asset_id = NULLIF(REPLACE(COALESCE(cover_asset_id, ''), '-', ''), ''),
    category_id = NULLIF(REPLACE(COALESCE(category_id, ''), '-', ''), '');
UPDATE blog_article_tag
SET article_id = REPLACE(article_id, '-', ''),
    tag_id = REPLACE(tag_id, '-', '');

ALTER TABLE category MODIFY id CHAR(32) NOT NULL;
ALTER TABLE tag MODIFY id CHAR(32) NOT NULL;
ALTER TABLE blog_article MODIFY id CHAR(32) NOT NULL;
ALTER TABLE blog_article MODIFY cover_asset_id CHAR(32) NULL;
ALTER TABLE blog_article MODIFY category_id CHAR(32) NULL;
ALTER TABLE blog_article_tag MODIFY article_id CHAR(32) NOT NULL;
ALTER TABLE blog_article_tag MODIFY tag_id CHAR(32) NOT NULL;

ALTER TABLE blog_article
    ADD CONSTRAINT fk_blog_article_category
        FOREIGN KEY (category_id) REFERENCES category (id);
ALTER TABLE blog_article_tag
    ADD CONSTRAINT fk_blog_article_tag_article
        FOREIGN KEY (article_id) REFERENCES blog_article (id);
ALTER TABLE blog_article_tag
    ADD CONSTRAINT fk_blog_article_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id);

CREATE TABLE user_account (
    id CHAR(32) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_account_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_account (
    id CHAR(32) NOT NULL,
    user_id CHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    password_hash VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_account_provider_subject (provider, provider_subject),
    KEY idx_auth_account_user_id (user_id),
    CONSTRAINT fk_auth_account_user
        FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role (
    id CHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_role_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permission (
    id CHAR(32) NOT NULL,
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permission_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
    user_id CHAR(32) NOT NULL,
    role_id CHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permission (
    role_id CHAR(32) NOT NULL,
    permission_id CHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES permission (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE asset (
    id CHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    asset_type VARCHAR(64) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    public_url VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_asset_type (asset_type),
    KEY idx_asset_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE asset_reference (
    id CHAR(32) NOT NULL,
    asset_id CHAR(32) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id CHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_asset_reference_asset_id (asset_id),
    KEY idx_asset_reference_source (source_type, source_id),
    CONSTRAINT fk_asset_reference_asset
        FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
