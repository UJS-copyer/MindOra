package com.mindora.blog.application;

import com.mindora.blog.domain.ArticleStatus;
import com.mindora.blog.domain.BlogArticle;
import com.mindora.blog.domain.Category;
import com.mindora.blog.domain.Tag;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JdbcBlogRepository implements BlogRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcBlogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public BlogArticle saveArticle(BlogArticle article) {
        jdbcTemplate.update(
                """
                INSERT INTO blog_article (
                    id, title, slug, summary, body, cover_asset_id, category_id,
                    status, visibility, read_count, created_at, updated_at, published_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    slug = VALUES(slug),
                    summary = VALUES(summary),
                    body = VALUES(body),
                    cover_asset_id = VALUES(cover_asset_id),
                    category_id = VALUES(category_id),
                    status = VALUES(status),
                    visibility = VALUES(visibility),
                    read_count = VALUES(read_count),
                    updated_at = VALUES(updated_at),
                    published_at = VALUES(published_at)
                """,
                article.id().toString(),
                article.title(),
                article.slug(),
                article.summary(),
                article.body(),
                uuidValue(article.coverAssetId()),
                uuidValue(article.categoryId()),
                article.status().value(),
                article.visibility(),
                article.readCount(),
                timestamp(article.createdAt()),
                timestamp(article.updatedAt()),
                timestamp(article.publishedAt()));
        jdbcTemplate.update("DELETE FROM blog_article_tag WHERE article_id = ?", article.id().toString());
        for (UUID tagId : article.tagIds()) {
            jdbcTemplate.update(
                    "INSERT INTO blog_article_tag (article_id, tag_id) VALUES (?, ?)",
                    article.id().toString(),
                    tagId.toString());
        }
        return article;
    }

    @Override
    public Optional<BlogArticle> findArticleById(UUID id) {
        return queryArticles("WHERE a.id = ? AND a.deleted = FALSE", id.toString()).stream().findFirst();
    }

    @Override
    public Optional<BlogArticle> findArticleBySlug(String slug) {
        return queryArticles("WHERE a.slug = ? AND a.deleted = FALSE", slug).stream().findFirst();
    }

    @Override
    public List<BlogArticle> listArticles() {
        return queryArticles("WHERE a.deleted = FALSE", new Object[0]);
    }

    @Override
    public Category saveCategory(Category category) {
        jdbcTemplate.update(
                """
                INSERT INTO category (id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = VALUES(updated_at)
                """,
                category.id().toString(),
                category.name(),
                timestamp(category.createdAt()),
                timestamp(category.updatedAt()));
        return category;
    }

    @Override
    public Optional<Category> findCategoryById(UUID id) {
        return jdbcTemplate.query(
                        "SELECT id, name, created_at, updated_at FROM category WHERE id = ? AND deleted = FALSE",
                        (rs, rowNum) -> mapCategory(rs),
                        id.toString())
                .stream()
                .findFirst();
    }

    @Override
    public Tag saveTag(Tag tag) {
        jdbcTemplate.update(
                """
                INSERT INTO tag (id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = VALUES(updated_at)
                """,
                tag.id().toString(),
                tag.name(),
                timestamp(tag.createdAt()),
                timestamp(tag.updatedAt()));
        return tag;
    }

    @Override
    public Optional<Tag> findTagById(UUID id) {
        return jdbcTemplate.query(
                        "SELECT id, name, created_at, updated_at FROM tag WHERE id = ? AND deleted = FALSE",
                        (rs, rowNum) -> mapTag(rs),
                        id.toString())
                .stream()
                .findFirst();
    }

    @Override
    public List<Category> listCategories() {
        return jdbcTemplate.query(
                "SELECT id, name, created_at, updated_at FROM category WHERE deleted = FALSE ORDER BY name",
                (rs, rowNum) -> mapCategory(rs));
    }

    @Override
    public List<Tag> listTags() {
        return jdbcTemplate.query(
                "SELECT id, name, created_at, updated_at FROM tag WHERE deleted = FALSE ORDER BY name",
                (rs, rowNum) -> mapTag(rs));
    }

    private List<BlogArticle> queryArticles(String clause, Object... args) {
        List<BlogArticle> articles = jdbcTemplate.query(
                """
                SELECT a.id, a.title, a.slug, a.summary, a.body, a.cover_asset_id,
                       a.category_id, a.status, a.visibility, a.read_count,
                       a.created_at, a.updated_at, a.published_at
                FROM blog_article a
                """
                        + clause
                        + " ORDER BY a.updated_at DESC",
                (rs, rowNum) -> mapArticle(rs),
                args);
        return articles.stream().map(this::attachTags).toList();
    }

    private BlogArticle attachTags(BlogArticle article) {
        Set<UUID> tagIds = jdbcTemplate.query(
                        "SELECT tag_id FROM blog_article_tag WHERE article_id = ?",
                        (rs, rowNum) -> UUID.fromString(rs.getString("tag_id")),
                        article.id().toString())
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new BlogArticle(
                article.id(),
                article.title(),
                article.slug(),
                article.summary(),
                article.body(),
                article.coverAssetId(),
                article.categoryId(),
                tagIds,
                article.status(),
                article.visibility(),
                article.readCount(),
                article.createdAt(),
                article.updatedAt(),
                article.publishedAt());
    }

    private BlogArticle mapArticle(ResultSet rs) throws SQLException {
        return new BlogArticle(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("summary"),
                rs.getString("body"),
                uuidValue(rs.getString("cover_asset_id")),
                uuidValue(rs.getString("category_id")),
                Set.of(),
                ArticleStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getString("visibility"),
                rs.getLong("read_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("published_at")));
    }

    private Category mapCategory(ResultSet rs) throws SQLException {
        return new Category(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Tag mapTag(ResultSet rs) throws SQLException {
        return new Tag(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String uuidValue(UUID value) {
        return value == null ? null : value.toString();
    }

    private UUID uuidValue(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
