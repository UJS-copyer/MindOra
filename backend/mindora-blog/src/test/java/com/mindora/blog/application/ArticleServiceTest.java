package com.mindora.blog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.blog.domain.ArticleStatus;
import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import com.mindora.blog.infrastructure.persistence.memory.InMemoryBlogRepository;
import com.mindora.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArticleServiceTest {
    private InMemoryBlogRepository repository;
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBlogRepository();
        articleService = new ArticleService(repository);
    }

    @Test
    void createsDraftWithCategoryAndTags() {
        Category category = articleService.createCategory("Engineering");
        Tag tag = articleService.createTag("Java");

        BlogArticle article = articleService.createDraft(new ArticleDraftCommand(
                "Spring Notes",
                "spring-notes",
                "A short summary",
                "# Spring",
                null,
                category.id(),
                Set.of(tag.id()),
                "public"));

        assertEquals(ArticleStatus.DRAFT, article.status());
        assertEquals(category.id(), article.categoryId());
        assertEquals(Set.of(tag.id()), article.tagIds());
        assertEquals("spring-notes", article.slug());
    }

    @Test
    void editsDraftBeforePublishing() {
        BlogArticle draft = articleService.createDraft(command("First title", "first-title"));

        BlogArticle edited = articleService.updateDraft(
                draft.id(),
                command("Updated title", "updated-title"));

        assertEquals("Updated title", edited.title());
        assertEquals("updated-title", edited.slug());
        assertEquals(ArticleStatus.DRAFT, edited.status());
    }

    @Test
    void editsUnpublishedArticleBeforeRepublishing() {
        BlogArticle draft = articleService.createDraft(command("First title", "first-title"));
        articleService.publish(draft.id());
        articleService.unpublish(draft.id());

        BlogArticle edited = articleService.updateArticle(
                draft.id(),
                command("Edited title", "edited-title"));
        BlogArticle republished = articleService.publish(draft.id());

        assertEquals("Edited title", edited.title());
        assertEquals(ArticleStatus.UNPUBLISHED, edited.status());
        assertEquals("edited-title", republished.slug());
        assertEquals(ArticleStatus.PUBLISHED, republished.status());
    }

    @Test
    void publishesAndUnpublishesArticle() {
        BlogArticle draft = articleService.createDraft(command("First title", "first-title"));

        BlogArticle published = articleService.publish(draft.id());
        assertEquals(ArticleStatus.PUBLISHED, published.status());
        assertEquals(List.of(published), articleService.listPublic(null, null));

        BlogArticle unpublished = articleService.unpublish(draft.id());
        assertEquals(ArticleStatus.UNPUBLISHED, unpublished.status());
        assertTrue(articleService.listPublic(null, null).isEmpty());
    }

    @Test
    void rejectsUnpublishingDraft() {
        BlogArticle draft = articleService.createDraft(command("First title", "first-title"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.unpublish(draft.id()));

        assertEquals("article_invalid_transition", exception.code());
    }

    @Test
    void filtersPublicArticlesByCategoryAndTag() {
        Category category = articleService.createCategory("Engineering");
        Tag tag = articleService.createTag("Java");
        BlogArticle article = articleService.createDraft(new ArticleDraftCommand(
                "Spring Notes",
                "spring-notes",
                "A short summary",
                "# Spring",
                null,
                category.id(),
                Set.of(tag.id()),
                "public"));
        articleService.publish(article.id());

        assertEquals(
                List.of(articleService.get(article.id())),
                articleService.listPublic(category.id(), tag.id()));
    }

    @Test
    void listsAdminArticlesRegardlessOfPublicationStatus() {
        BlogArticle draft = articleService.createDraft(command("Draft", "draft"));
        BlogArticle published = articleService.publish(
                articleService.createDraft(command("Published", "published")).id());

        List<BlogArticle> articles = articleService.listAdmin();
        assertEquals(2, articles.size());
        assertTrue(articles.contains(draft));
        assertTrue(articles.contains(published));
    }

    @Test
    void readingPublicArticleIncrementsReadCount() {
        BlogArticle draft = articleService.createDraft(command("First title", "first-title"));
        articleService.publish(draft.id());

        BlogArticle firstRead = articleService.getPublicBySlug("first-title");
        BlogArticle secondRead = articleService.getPublicBySlug("first-title");

        assertEquals(1, firstRead.readCount());
        assertEquals(2, secondRead.readCount());
    }

    @Test
    void publishesKnowledgeDocumentAsLinkedDraftArticle() {
        UUID documentId = UUID.randomUUID();

        BlogArticle first = articleService.publishKnowledgeDocument(
                documentId, "公开笔记", "notes/public-note.md", "# 公开笔记\n正文", "indexed");
        BlogArticle updated = articleService.publishKnowledgeDocument(
                documentId, "公开笔记更新", "notes/public-note.md", "# 公开笔记更新\n正文", "indexed");

        assertEquals(first.id(), updated.id());
        assertEquals(documentId, updated.knowledgeDocumentId());
        assertEquals(ArticleStatus.DRAFT, updated.status());
        assertEquals("public", updated.visibility());
        assertEquals("public-note", updated.slug());
        assertTrue(articleService.listPublic(null, null).isEmpty());
    }

    @Test
    void syncsKnowledgeOnlyAfterPublish() {
        List<UUID> upserts = new java.util.ArrayList<>();
        ArticleService service = new ArticleService(repository, new ArticleKnowledgePort() {
            @Override
            public ArticleKnowledgeState upsert(BlogArticle article) {
                upserts.add(article.id());
                return new ArticleKnowledgeState(UUID.randomUUID(), "indexed");
            }

            @Override
            public ArticleKnowledgeState disable(BlogArticle article) {
                return new ArticleKnowledgeState(article.knowledgeDocumentId(), "pending");
            }
        });

        BlogArticle draft = service.createDraft(command("First title", "first-title"));
        assertTrue(upserts.isEmpty());

        BlogArticle published = service.publish(draft.id());

        assertEquals(List.of(draft.id()), upserts);
        assertEquals("indexed", published.knowledgeIndexStatus());
    }

    private ArticleDraftCommand command(String title, String slug) {
        return new ArticleDraftCommand(
                title,
                slug,
                "A short summary",
                "# " + title,
                null,
                null,
                Set.of(),
                "public");
    }
}
