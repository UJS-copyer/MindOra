package com.mindora.blog.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record BlogArticle(
        UUID id,
        String title,
        String slug,
        String summary,
        String body,
        UUID coverAssetId,
        UUID categoryId,
        Set<UUID> tagIds,
        ArticleStatus status,
        String visibility,
        boolean knowledgeEnabled,
        UUID knowledgeDocumentId,
        String knowledgeIndexStatus,
        long readCount,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {
    public BlogArticle {
        tagIds = Set.copyOf(tagIds);
    }

    public BlogArticle updateDraft(
            String title,
            String slug,
            String summary,
            String body,
            UUID coverAssetId,
            UUID categoryId,
            Set<UUID> tagIds,
            String visibility,
            Instant updatedAt) {
        return new BlogArticle(
                id,
                title,
                slug,
                summary,
                body,
                coverAssetId,
                categoryId,
                tagIds,
                status,
                visibility,
                knowledgeEnabled,
                knowledgeDocumentId,
                knowledgeIndexStatus,
                readCount,
                createdAt,
                updatedAt,
                publishedAt);
    }

    public BlogArticle publish(Instant publishedAt) {
        return new BlogArticle(
                id,
                title,
                slug,
                summary,
                body,
                coverAssetId,
                categoryId,
                tagIds,
                ArticleStatus.PUBLISHED,
                visibility,
                knowledgeEnabled,
                knowledgeDocumentId,
                knowledgeIndexStatus,
                readCount,
                createdAt,
                publishedAt,
                publishedAt);
    }

    public BlogArticle unpublish(Instant updatedAt) {
        return new BlogArticle(
                id,
                title,
                slug,
                summary,
                body,
                coverAssetId,
                categoryId,
                tagIds,
                ArticleStatus.UNPUBLISHED,
                visibility,
                knowledgeEnabled,
                knowledgeDocumentId,
                knowledgeIndexStatus,
                readCount,
                createdAt,
                updatedAt,
                publishedAt);
    }

    public BlogArticle incrementReadCount(Instant updatedAt) {
        return new BlogArticle(
                id,
                title,
                slug,
                summary,
                body,
                coverAssetId,
                categoryId,
                tagIds,
                status,
                visibility,
                knowledgeEnabled,
                knowledgeDocumentId,
                knowledgeIndexStatus,
                readCount + 1,
                createdAt,
                updatedAt,
                publishedAt);
    }

    public BlogArticle withKnowledgeState(boolean enabled, UUID documentId, String indexStatus, Instant updatedAt) {
        return new BlogArticle(
                id,
                title,
                slug,
                summary,
                body,
                coverAssetId,
                categoryId,
                tagIds,
                status,
                visibility,
                enabled,
                documentId,
                indexStatus,
                readCount,
                createdAt,
                updatedAt,
                publishedAt);
    }
}
