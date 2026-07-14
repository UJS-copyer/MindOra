CREATE TABLE category (
    id CHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_category_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tag (
    id CHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tag_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE blog_article (
    id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    summary TEXT NULL,
    body LONGTEXT NOT NULL,
    cover_asset_id CHAR(36) NULL,
    category_id CHAR(36) NULL,
    status VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    read_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_blog_article_slug (slug),
    KEY idx_blog_article_status_visibility (status, visibility),
    KEY idx_blog_article_category_id (category_id),
    KEY idx_blog_article_published_at (published_at),
    CONSTRAINT fk_blog_article_category
        FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE blog_article_tag (
    article_id CHAR(36) NOT NULL,
    tag_id CHAR(36) NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_blog_article_tag_article
        FOREIGN KEY (article_id) REFERENCES blog_article (id),
    CONSTRAINT fk_blog_article_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
