package com.mindora.blog.infrastructure.persistence.memory;

import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.BlogRepository;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryBlogRepository implements BlogRepository {
    private final Map<UUID, BlogArticle> articles = new LinkedHashMap<>();
    private final Map<UUID, Category> categories = new LinkedHashMap<>();
    private final Map<UUID, Tag> tags = new LinkedHashMap<>();

    @Override
    public BlogArticle saveArticle(BlogArticle article) {
        articles.put(article.id(), article);
        return article;
    }

    @Override
    public Optional<BlogArticle> findArticleById(UUID id) {
        return Optional.ofNullable(articles.get(id));
    }

    @Override
    public Optional<BlogArticle> findArticleBySlug(String slug) {
        return articles.values().stream()
                .filter(article -> article.slug().equals(slug))
                .findFirst();
    }

    @Override
    public Optional<BlogArticle> findArticleByKnowledgeDocumentId(UUID documentId) {
        return articles.values().stream()
                .filter(article -> documentId.equals(article.knowledgeDocumentId()))
                .findFirst();
    }

    @Override
    public List<BlogArticle> listArticles() {
        return new ArrayList<>(articles.values());
    }

    @Override
    public Category saveCategory(Category category) {
        categories.put(category.id(), category);
        return category;
    }

    @Override
    public Optional<Category> findCategoryById(UUID id) {
        return Optional.ofNullable(categories.get(id));
    }

    @Override
    public void deleteCategory(UUID id) {
        categories.remove(id);
    }

    @Override
    public Tag saveTag(Tag tag) {
        tags.put(tag.id(), tag);
        return tag;
    }

    @Override
    public Optional<Tag> findTagById(UUID id) {
        return Optional.ofNullable(tags.get(id));
    }

    @Override
    public void deleteTag(UUID id) {
        tags.remove(id);
    }

    @Override
    public List<Category> listCategories() {
        return new ArrayList<>(categories.values());
    }

    @Override
    public List<Tag> listTags() {
        return new ArrayList<>(tags.values());
    }
}
