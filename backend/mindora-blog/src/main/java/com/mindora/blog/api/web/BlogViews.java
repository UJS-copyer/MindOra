package com.mindora.blog.api.web;

import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import com.mindora.common.id.PublicIds;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

final class BlogViews {
    private BlogViews() {
    }

    static ArticleView article(BlogArticle article) {
        return new ArticleView(
                PublicIds.toPublicId(article.id()),
                article.title(),
                article.slug(),
                article.summary(),
                article.body(),
                PublicIds.toPublicId(article.coverAssetId()),
                PublicIds.toPublicId(article.categoryId()),
                article.tagIds().stream()
                        .map(PublicIds::toPublicId)
                        .collect(Collectors.toUnmodifiableSet()),
                article.status().value(),
                article.visibility(),
                article.readCount(),
                article.createdAt(),
                article.updatedAt(),
                article.publishedAt());
    }

    static CategoryView category(Category category) {
        return new CategoryView(
                PublicIds.toPublicId(category.id()),
                category.name(),
                category.createdAt(),
                category.updatedAt());
    }

    static TagView tag(Tag tag) {
        return new TagView(PublicIds.toPublicId(tag.id()), tag.name(), tag.createdAt(), tag.updatedAt());
    }

    record ArticleView(
            String id,
            String title,
            String slug,
            String summary,
            String body,
            String coverAssetId,
            String categoryId,
            Set<String> tagIds,
            String status,
            String visibility,
            long readCount,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {
    }

    record CategoryView(String id, String name, Instant createdAt, Instant updatedAt) {
    }

    record TagView(String id, String name, Instant createdAt, Instant updatedAt) {
    }
}
