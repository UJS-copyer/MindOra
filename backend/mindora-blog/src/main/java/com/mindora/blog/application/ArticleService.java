package com.mindora.blog.application;

import com.mindora.blog.domain.ArticleStatus;
import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.BlogRepository;
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
    private final ArticleKnowledgePort knowledge;
    private final Clock clock;

    public ArticleService(BlogRepository repository) {
        this(repository, ArticleKnowledgePort.noop(), Clock.systemUTC());
    }

    public ArticleService(BlogRepository repository, Clock clock) {
        this(repository, ArticleKnowledgePort.noop(), clock);
    }

    public ArticleService(BlogRepository repository, ArticleKnowledgePort knowledge) {
        this(repository, knowledge, Clock.systemUTC());
    }

    public ArticleService(BlogRepository repository, ArticleKnowledgePort knowledge, Clock clock) {
        this.repository = repository;
        this.knowledge = knowledge == null ? ArticleKnowledgePort.noop() : knowledge;
        this.clock = clock;
    }

    public Category createCategory(String name) {
        String normalizedName = requireText(name, "category_name_required", "Category name is required");
        Instant now = Instant.now(clock);
        return repository.saveCategory(new Category(UUID.randomUUID(), normalizedName, now, now));
    }

    public Category updateCategory(UUID id, String name) {
        Category category = repository.findCategoryById(id)
                .orElseThrow(() -> new BusinessException("category_not_found", "Category not found"));
        String normalizedName = requireText(name, "category_name_required", "Category name is required");
        return repository.saveCategory(new Category(
                category.id(),
                normalizedName,
                category.createdAt(),
                Instant.now(clock)));
    }

    public void deleteCategory(UUID id) {
        if (repository.findCategoryById(id).isEmpty()) {
            throw new BusinessException("category_not_found", "Category not found");
        }
        repository.deleteCategory(id);
    }

    public Tag createTag(String name) {
        String normalizedName = requireText(name, "tag_name_required", "Tag name is required");
        Instant now = Instant.now(clock);
        return repository.saveTag(new Tag(UUID.randomUUID(), normalizedName, now, now));
    }

    public Tag updateTag(UUID id, String name) {
        Tag tag = repository.findTagById(id)
                .orElseThrow(() -> new BusinessException("tag_not_found", "Tag not found"));
        String normalizedName = requireText(name, "tag_name_required", "Tag name is required");
        return repository.saveTag(new Tag(tag.id(), normalizedName, tag.createdAt(), Instant.now(clock)));
    }

    public void deleteTag(UUID id) {
        if (repository.findTagById(id).isEmpty()) {
            throw new BusinessException("tag_not_found", "Tag not found");
        }
        repository.deleteTag(id);
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
                command.knowledgeEnabledOrDefault(),
                null,
                "pending",
                0,
                now,
                now,
                null);
        BlogArticle saved = repository.saveArticle(article);
        return saved;
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
        BlogArticle saved = repository.saveArticle(updated.withKnowledgeState(
                command.knowledgeEnabledOrDefault(),
                article.knowledgeDocumentId(),
                article.knowledgeIndexStatus(),
                updated.updatedAt()));
        return saved;
    }

    public BlogArticle publish(UUID id) {
        BlogArticle article = get(id);
        if (article.status() == ArticleStatus.PUBLISHED) {
            return article;
        }
        Instant now = Instant.now(clock);
        BlogArticle saved = repository.saveArticle(article.publish(now));
        return syncKnowledge(saved);
    }

    public BlogArticle unpublish(UUID id) {
        BlogArticle article = get(id);
        if (article.status() != ArticleStatus.PUBLISHED) {
            throw new BusinessException(
                    "article_invalid_transition", "Only published articles can be unpublished");
        }
        BlogArticle saved = repository.saveArticle(article.unpublish(Instant.now(clock)));
        ArticleKnowledgePort.ArticleKnowledgeState state = knowledge.disable(saved);
        return saveKnowledgeState(saved, saved.knowledgeEnabled(), state);
    }

    public BlogArticle updateKnowledgeEnabled(UUID id, boolean enabled) {
        BlogArticle article = get(id);
        BlogArticle saved = repository.saveArticle(article.withKnowledgeState(
                enabled,
                article.knowledgeDocumentId(),
                article.knowledgeIndexStatus(),
                Instant.now(clock)));
        if (saved.status() == ArticleStatus.PUBLISHED) {
            return enabled ? syncKnowledge(saved) : disableKnowledge(saved);
        }
        return saved;
    }

    public BlogArticle publishKnowledgeDocument(
            UUID documentId,
            String title,
            String sourcePath,
            String body,
            String indexStatus) {
        if (documentId == null) {
            throw new BusinessException("knowledge_document_id_required", "Knowledge document id is required");
        }
        String normalizedTitle = requireText(title, "article_title_required", "Article title is required");
        String normalizedBody = requireText(body, "article_body_required", "Article body is required");
        Instant now = Instant.now(clock);
        BlogArticle existing = repository.findArticleByKnowledgeDocumentId(documentId).orElse(null);
        String slug = existing == null
                ? uniqueSlug(slugBase(sourcePath, normalizedTitle), documentId, null)
                : existing.slug();
        BlogArticle article = new BlogArticle(
                existing == null ? UUID.randomUUID() : existing.id(),
                normalizedTitle,
                slug,
                summarize(normalizedBody),
                normalizedBody,
                existing == null ? null : existing.coverAssetId(),
                existing == null ? null : existing.categoryId(),
                existing == null ? Set.of() : existing.tagIds(),
                existing == null ? ArticleStatus.DRAFT : existing.status(),
                PUBLIC_VISIBILITY,
                true,
                documentId,
                indexStatus == null || indexStatus.isBlank() ? "pending" : indexStatus,
                existing == null ? 0 : existing.readCount(),
                existing == null ? now : existing.createdAt(),
                now,
                existing == null ? null : existing.publishedAt());
        return repository.saveArticle(article);
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

    private String uniqueSlug(String base, UUID documentId, UUID currentArticleId) {
        String normalized = base == null || base.isBlank()
                ? "knowledge-" + documentId.toString().substring(0, 8)
                : base;
        String candidate = normalized;
        int suffix = 2;
        while (repository.findArticleBySlug(candidate)
                .filter(article -> !article.id().equals(currentArticleId))
                .isPresent()) {
            candidate = normalized + "-" + suffix++;
        }
        return candidate;
    }

    private String slugBase(String sourcePath, String title) {
        String value = sourcePath == null || sourcePath.isBlank() ? title : sourcePath;
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceFirst("(?i)\\.md$", "");
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "knowledge" : slug;
    }

    private String summarize(String body) {
        String text = body.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[[^]]*]\\(([^)]*)\\)", "")
                .replaceAll("[*_`>\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    private boolean isPubliclyVisible(BlogArticle article) {
        return article.status() == ArticleStatus.PUBLISHED
                && PUBLIC_VISIBILITY.equals(article.visibility());
    }

    private BlogArticle syncKnowledge(BlogArticle article) {
        if (!article.knowledgeEnabled()) {
            return disableKnowledge(article);
        }
        ArticleKnowledgePort.ArticleKnowledgeState state = knowledge.upsert(article);
        return saveKnowledgeState(article, true, state);
    }

    private BlogArticle disableKnowledge(BlogArticle article) {
        ArticleKnowledgePort.ArticleKnowledgeState state = knowledge.disable(article);
        return saveKnowledgeState(article, false, state);
    }

    private BlogArticle saveKnowledgeState(
            BlogArticle article,
            boolean enabled,
            ArticleKnowledgePort.ArticleKnowledgeState state) {
        if (state == null) {
            return article;
        }
        String indexStatus = state.indexStatus() == null || state.indexStatus().isBlank()
                ? article.knowledgeIndexStatus()
                : state.indexStatus();
        if (enabled == article.knowledgeEnabled()
                && java.util.Objects.equals(state.documentId(), article.knowledgeDocumentId())
                && java.util.Objects.equals(indexStatus, article.knowledgeIndexStatus())) {
            return article;
        }
        return repository.saveArticle(article.withKnowledgeState(
                enabled,
                state.documentId(),
                indexStatus,
                Instant.now(clock)));
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
