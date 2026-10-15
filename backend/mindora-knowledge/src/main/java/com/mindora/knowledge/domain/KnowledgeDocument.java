package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocument(
        UUID id,
        KnowledgeEnums.SourceType sourceType,
        String sourceId,
        String sourcePath,
        String title,
        String currentVersionId,
        String sourceHash,
        String visibility,
        boolean knowledgeEnabled,
        String publicArticleId,
        String articleSlug,
        KnowledgeEnums.DocumentStatus status,
        KnowledgeEnums.ParseStatus parseStatus,
        KnowledgeEnums.IndexStatus indexStatus,
        String indexFailureReason,
        int indexRetryCount,
        Instant createdAt,
        Instant updatedAt) {
    public KnowledgeDocument {
        sourcePath = sourcePath == null ? "" : sourcePath;
        title = title == null || title.isBlank() ? sourcePath : title;
        visibility = visibility == null || visibility.isBlank() ? "private" : visibility;
        articleSlug = articleSlug == null || articleSlug.isBlank() ? null : articleSlug;
        status = status == null ? KnowledgeEnums.DocumentStatus.ACTIVE : status;
        parseStatus = parseStatus == null ? KnowledgeEnums.ParseStatus.PENDING : parseStatus;
        indexStatus = indexStatus == null ? KnowledgeEnums.IndexStatus.PENDING : indexStatus;
    }

    public KnowledgeDocument versioned(
            String versionId,
            String sourceHash,
            String title,
            Instant at) {
        return new KnowledgeDocument(
                id,
                sourceType,
                sourceId,
                sourcePath,
                title,
                versionId,
                sourceHash,
                visibility,
                knowledgeEnabled,
                publicArticleId,
                articleSlug,
                status,
                KnowledgeEnums.ParseStatus.PARSED,
                KnowledgeEnums.IndexStatus.PENDING,
                null,
                indexRetryCount,
                createdAt,
                at);
    }

    public KnowledgeDocument index(KnowledgeEnums.IndexStatus status, String failureReason, Instant at) {
        return new KnowledgeDocument(
                id, sourceType, sourceId, sourcePath, title, currentVersionId, sourceHash, visibility,
                knowledgeEnabled, publicArticleId, articleSlug, this.status, parseStatus, status, failureReason,
                failureReason == null ? indexRetryCount : indexRetryCount + 1, createdAt, at);
    }

    public KnowledgeDocument archived(Instant at) {
        return new KnowledgeDocument(
                id, sourceType, sourceId, sourcePath, title, currentVersionId, sourceHash, visibility,
                false, publicArticleId, articleSlug, KnowledgeEnums.DocumentStatus.ARCHIVED, parseStatus, indexStatus,
                indexFailureReason, indexRetryCount, createdAt, at);
    }

    public KnowledgeDocument published(String articleId, String slug, Instant at) {
        return new KnowledgeDocument(
                id, sourceType, sourceId, sourcePath, title, currentVersionId, sourceHash, "public",
                knowledgeEnabled, articleId, slug, status, parseStatus, indexStatus,
                indexFailureReason, indexRetryCount, createdAt, at);
    }

    public KnowledgeDocument linkedArticle(String articleId, String slug, Instant at) {
        return new KnowledgeDocument(
                id, sourceType, sourceId, sourcePath, title, currentVersionId, sourceHash, visibility,
                knowledgeEnabled, articleId, slug, status, parseStatus, indexStatus,
                indexFailureReason, indexRetryCount, createdAt, at);
    }

    /**
     * 文章 slug（发布为公开文章或博客文章反向入库时由 blog 模块写入）。
     * 供检索结果直接生成引用链接；未关联文章时返回 {@code null}。
     */
    public String articleSlug() {
        return articleSlug;
    }
}
