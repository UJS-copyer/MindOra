package com.mindora.app.web;

import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

final class BlogViews {
    private BlogViews() {
    }

    static ArticleView article(BlogArticle article) {
        return new ArticleView(
                article.id(),
                article.title(),
                article.slug(),
                article.summary(),
                article.body(),
                article.coverAssetId(),
                article.categoryId(),
                article.tagIds(),
                article.status().value(),
                article.visibility(),
                article.readCount(),
                article.createdAt(),
                article.updatedAt(),
                article.publishedAt());
    }

    static CategoryView category(Category category) {
        return new CategoryView(category.id(), category.name(), category.createdAt(), category.updatedAt());
    }

    static TagView tag(Tag tag) {
        return new TagView(tag.id(), tag.name(), tag.createdAt(), tag.updatedAt());
    }

    record ArticleView(
            UUID id,
            String title,
            String slug,
            String summary,
            String body,
            UUID coverAssetId,
            UUID categoryId,
            Set<UUID> tagIds,
            String status,
            String visibility,
            long readCount,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {
    }

    record CategoryView(UUID id, String name, Instant createdAt, Instant updatedAt) {
    }

    record TagView(UUID id, String name, Instant createdAt, Instant updatedAt) {
    }
}
