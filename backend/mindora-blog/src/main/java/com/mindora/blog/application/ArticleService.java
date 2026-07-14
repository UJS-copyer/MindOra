package com.mindora.blog.application;

import com.mindora.blog.domain.ArticleStatus;
import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import com.mindora.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArticleService {
    private static final String PUBLIC_VISIBILITY = "public";

    private final BlogRepository repository;
    private final Clock clock;

    public ArticleService(BlogRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public ArticleService(BlogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Category createCategory(String name) {
        String normalizedName = requireText(name, "category_name_required", "Category name is required");
        Instant now = Instant.now(clock);
        return repository.saveCategory(new Category(UUID.randomUUID(), normalizedName, now, now));
    }

    public Tag createTag(String name) {
        String normalizedName = requireText(name, "tag_name_required", "Tag name is required");
        Instant now = Instant.now(clock);
        return repository.saveTag(new Tag(UUID.randomUUID(), normalizedName, now, now));
    }

    public BlogArticle createDraft(ArticleDraftCommand command) {
        validateCommand(command);
        ensureReferences(command);
        ensureSlugAvailable(command.slug(), null);
        Instant now = Instant.now(clock);
        BlogArticle article = new BlogArticle(
                UUID.randomUUID(),
                command.title().trim(),
                command.slug().trim(),
                trimNullable(command.summary()),
                command.body().trim(),
                command.coverAssetId(),
                command.categoryId(),
                command.tagIds(),
                ArticleStatus.DRAFT,
                normalizeVisibility(command.visibility()),
                0,
                now,
                now,
                null);
        return repository.saveArticle(article);
    }

    public BlogArticle updateDraft(UUID id, ArticleDraftCommand command) {
        BlogArticle article = get(id);
        if (article.status() != ArticleStatus.DRAFT) {
            throw new BusinessException(
                    "article_invalid_transition", "Only draft articles can be edited by this command");
        }
        return updateArticle(article, command);
    }

    public BlogArticle updateArticle(UUID id, ArticleDraftCommand command) {
        BlogArticle article = get(id);
        if (article.status() == ArticleStatus.PUBLISHED) {
            throw new BusinessException(
                    "article_invalid_transition", "Published articles must be unpublished before editing");
        }
        return updateArticle(article, command);
    }

    private BlogArticle updateArticle(BlogArticle article, ArticleDraftCommand command) {
        validateCommand(command);
        ensureReferences(command);
        ensureSlugAvailable(command.slug(), article.id());
        BlogArticle updated = article.updateDraft(
                command.title().trim(),
                command.slug().trim(),
                trimNullable(command.summary()),
                command.body().trim(),
                command.coverAssetId(),
                command.categoryId(),
                command.tagIds(),
                normalizeVisibility(command.visibility()),
                Instant.now(clock));
        return repository.saveArticle(updated);
    }

    public BlogArticle publish(UUID id) {
        BlogArticle article = get(id);
        if (article.status() == ArticleStatus.PUBLISHED) {
            return article;
        }
        Instant now = Instant.now(clock);
        return repository.saveArticle(article.publish(now));
    }

    public BlogArticle unpublish(UUID id) {
        BlogArticle article = get(id);
        if (article.status() != ArticleStatus.PUBLISHED) {
            throw new BusinessException(
                    "article_invalid_transition", "Only published articles can be unpublished");
        }
        return repository.saveArticle(article.unpublish(Instant.now(clock)));
    }

    public BlogArticle get(UUID id) {
        return repository.findArticleById(id)
                .orElseThrow(() -> new BusinessException("article_not_found", "Article not found"));
    }

    public List<BlogArticle> listPublic(UUID categoryId, UUID tagId) {
        return repository.listArticles().stream()
                .filter(this::isPubliclyVisible)
                .filter(article -> categoryId == null || categoryId.equals(article.categoryId()))
                .filter(article -> tagId == null || article.tagIds().contains(tagId))
                .sorted(Comparator.comparing(BlogArticle::publishedAt).reversed())
                .toList();
    }

    public List<BlogArticle> listAdmin() {
        return repository.listArticles().stream()
                .sorted(Comparator.comparing(BlogArticle::updatedAt).reversed())
                .toList();
    }

    public List<Category> listCategories() {
        return repository.listCategories();
    }

    public List<Tag> listTags() {
        return repository.listTags();
    }

    public BlogArticle getPublicBySlug(String slug) {
        BlogArticle article = repository.findArticleBySlug(requireText(
                        slug, "article_slug_required", "Article slug is required"))
                .filter(this::isPubliclyVisible)
                .orElseThrow(() -> new BusinessException("article_not_found", "Article not found"));
        return repository.saveArticle(article.incrementReadCount(Instant.now(clock)));
    }

    private void validateCommand(ArticleDraftCommand command) {
        if (command == null) {
            throw new BusinessException("validation_error", "Article payload is required");
        }
        requireText(command.title(), "article_title_required", "Article title is required");
        requireText(command.slug(), "article_slug_required", "Article slug is required");
        requireText(command.body(), "article_body_required", "Article body is required");
        normalizeVisibility(command.visibility());
    }

    private void ensureReferences(ArticleDraftCommand command) {
        if (command.categoryId() != null && repository.findCategoryById(command.categoryId()).isEmpty()) {
            throw new BusinessException("category_not_found", "Category not found");
        }
        Set<UUID> tagIds = command.tagIds();
        for (UUID tagId : tagIds) {
            if (repository.findTagById(tagId).isEmpty()) {
                throw new BusinessException("tag_not_found", "Tag not found");
            }
        }
    }

    private void ensureSlugAvailable(String slug, UUID currentArticleId) {
        repository.findArticleBySlug(slug.trim())
                .filter(article -> !article.id().equals(currentArticleId))
                .ifPresent(article -> {
                    throw new BusinessException("article_slug_exists", "Article slug already exists");
                });
    }

    private boolean isPubliclyVisible(BlogArticle article) {
        return article.status() == ArticleStatus.PUBLISHED
                && PUBLIC_VISIBILITY.equals(article.visibility());
    }

    private String normalizeVisibility(String visibility) {
        String normalized = visibility == null || visibility.isBlank()
                ? PUBLIC_VISIBILITY
                : visibility.trim().toLowerCase();
        if (!PUBLIC_VISIBILITY.equals(normalized) && !"private".equals(normalized)) {
            throw new BusinessException("article_visibility_invalid", "Article visibility is invalid");
        }
        return normalized;
    }

    private String requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
