ALTER TABLE knowledge_chunk
    ADD COLUMN heading_path VARCHAR(767) NULL AFTER content,
    ADD COLUMN heading_level INT NOT NULL DEFAULT 0 AFTER heading_path,
    ADD COLUMN block_type VARCHAR(32) NOT NULL DEFAULT 'paragraph' AFTER heading_level,
    ADD COLUMN code_language VARCHAR(32) NULL AFTER block_type,
    ADD COLUMN content_hash VARCHAR(128) NULL AFTER character_count;
