ALTER TABLE knowledge_document
    ADD COLUMN public_article_id CHAR(32) NULL AFTER knowledge_enabled,
    ADD KEY idx_knowledge_document_public_article (public_article_id);
