package com.mindora.blog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogRepository {
    BlogArticle saveArticle(BlogArticle article);

    Optional<BlogArticle> findArticleById(UUID id);

    Optional<BlogArticle> findArticleBySlug(String slug);

    Optional<BlogArticle> findArticleByKnowledgeDocumentId(UUID documentId);

    List<BlogArticle> listArticles();

    Category saveCategory(Category category);

    Optional<Category> findCategoryById(UUID id);

    void deleteCategory(UUID id);

    Tag saveTag(Tag tag);

    Optional<Tag> findTagById(UUID id);

    void deleteTag(UUID id);

    List<Category> listCategories();

    List<Tag> listTags();
}
