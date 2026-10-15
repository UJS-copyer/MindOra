-- Stage 3.4 缺陷修复：Gitee 来源文档发布为公开文章后，slug 此前靠
-- sourceType==BLOG && sourcePath 以 "blog:" 开头 派生，GITEE 文档恒为 null，
-- 导致引用回链不可达。改为发布时持久化（见 KnowledgeDocument.articleSlug）。
ALTER TABLE knowledge_document
    ADD COLUMN article_slug VARCHAR(255) NULL AFTER public_article_id;
