CREATE TABLE knowledge_data_source (
    id CHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    repository_url VARCHAR(512) NOT NULL,
    branch_name VARCHAR(120) NOT NULL,
    access_token VARCHAR(512) NULL,
    root_path VARCHAR(255) NOT NULL DEFAULT '',
    default_visibility VARCHAR(32) NOT NULL DEFAULT 'private',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_data_source_type_enabled (source_type, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_sync_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id CHAR(32) NOT NULL,
    sync_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_files INT NOT NULL DEFAULT 0,
    processed_files INT NOT NULL DEFAULT 0,
    created_documents INT NOT NULL DEFAULT 0,
    updated_documents INT NOT NULL DEFAULT 0,
    archived_documents INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_sync_task_source (source_id),
    KEY idx_knowledge_sync_task_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_document (
    id CHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    source_path VARCHAR(512) NOT NULL,
    title VARCHAR(255) NOT NULL,
    current_version_id CHAR(32) NULL,
    source_hash VARCHAR(128) NULL,
    visibility VARCHAR(32) NOT NULL,
    knowledge_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    index_status VARCHAR(32) NOT NULL,
    index_failure_reason TEXT NULL,
    index_retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_knowledge_document_source_path (source_type, source_id, source_path),
    KEY idx_knowledge_document_visibility (visibility),
    KEY idx_knowledge_document_status (status),
    KEY idx_knowledge_document_index_status (index_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_document_version (
    id CHAR(32) NOT NULL,
    document_id CHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    source_path VARCHAR(512) NOT NULL,
    content_snapshot LONGTEXT NOT NULL,
    metadata_snapshot JSON NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_document_version_document (document_id),
    KEY idx_knowledge_document_version_hash (content_hash),
    CONSTRAINT fk_knowledge_document_version_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_chunk (
    id CHAR(32) NOT NULL,
    document_version_id CHAR(32) NOT NULL,
    sequence_no INT NOT NULL,
    content LONGTEXT NOT NULL,
    character_count INT NOT NULL,
    index_status VARCHAR(32) NOT NULL,
    embedding_model VARCHAR(120) NULL,
    vector_point_id VARCHAR(120) NULL,
    failure_reason TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_chunk_version (document_version_id),
    KEY idx_knowledge_chunk_index_status (index_status),
    CONSTRAINT fk_knowledge_chunk_version
        FOREIGN KEY (document_version_id) REFERENCES knowledge_document_version (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_config (
    config_key VARCHAR(120) NOT NULL,
    config_value TEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE blog_article
    ADD COLUMN knowledge_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER visibility,
    ADD COLUMN knowledge_document_id CHAR(32) NULL AFTER knowledge_enabled,
    ADD COLUMN knowledge_index_status VARCHAR(32) NOT NULL DEFAULT 'pending' AFTER knowledge_document_id;
