package com.mindora.app.web;

import com.mindora.blog.application.ArticleDraftCommand;
import com.mindora.blog.application.ArticleService;
import com.mindora.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class BlogAdminController {
    private final ArticleService articleService;

    public BlogAdminController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping("/categories")
    public ApiResponse<BlogViews.CategoryView> createCategory(
            @Valid @RequestBody NameRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.category(articleService.createCategory(request.name())), traceId);
    }

    @GetMapping("/categories")
    public ApiResponse<java.util.List<BlogViews.CategoryView>> listCategories(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(articleService.listCategories().stream()
                .map(BlogViews::category)
                .toList(), traceId);
    }

    @PostMapping("/tags")
    public ApiResponse<BlogViews.TagView> createTag(
            @Valid @RequestBody NameRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.tag(articleService.createTag(request.name())), traceId);
    }

    @GetMapping("/tags")
    public ApiResponse<java.util.List<BlogViews.TagView>> listTags(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(articleService.listTags().stream()
                .map(BlogViews::tag)
                .toList(), traceId);
    }

    @GetMapping("/articles")
    public ApiResponse<java.util.List<BlogViews.ArticleView>> listArticles(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(articleService.listAdmin().stream()
                .map(BlogViews::article)
                .toList(), traceId);
    }

    @PostMapping("/articles")
    public ApiResponse<BlogViews.ArticleView> createArticle(
            @RequestBody ArticleRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.createDraft(request.command())), traceId);
    }

    @PutMapping("/articles/{id}")
    public ApiResponse<BlogViews.ArticleView> updateArticle(
            @PathVariable UUID id,
            @RequestBody ArticleRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.updateArticle(id, request.command())), traceId);
    }

    @PostMapping("/articles/{id}/publish")
    public ApiResponse<BlogViews.ArticleView> publish(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.publish(id)), traceId);
    }

    @PostMapping("/articles/{id}/unpublish")
    public ApiResponse<BlogViews.ArticleView> unpublish(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.unpublish(id)), traceId);
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    public record NameRequest(@NotBlank String name) {
    }

    public record ArticleRequest(
            String title,
            String slug,
            String summary,
            String body,
            UUID coverAssetId,
            UUID categoryId,
            Set<UUID> tagIds,
            String visibility) {
        ArticleDraftCommand command() {
            return new ArticleDraftCommand(
                    title, slug, summary, body, coverAssetId, categoryId, tagIds, visibility);
        }
    }
}
