package com.mindora.blog.api.web;

import com.mindora.blog.application.ArticleDraftCommand;
import com.mindora.blog.application.ArticleService;
import com.mindora.common.api.ApiResponse;
import com.mindora.common.id.PublicIds;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@SecurityRequirement(name = "bearerAuth")
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

    @PutMapping("/categories/{id}")
    public ApiResponse<BlogViews.CategoryView> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody NameRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(
                BlogViews.category(articleService.updateCategory(PublicIds.toUuid(id), request.name())),
                traceId);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        articleService.deleteCategory(PublicIds.toUuid(id));
        return success(null, traceId);
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

    @PutMapping("/tags/{id}")
    public ApiResponse<BlogViews.TagView> updateTag(
            @PathVariable String id,
            @Valid @RequestBody NameRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(
                BlogViews.tag(articleService.updateTag(PublicIds.toUuid(id), request.name())),
                traceId);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        articleService.deleteTag(PublicIds.toUuid(id));
        return success(null, traceId);
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
            @PathVariable String id,
            @RequestBody ArticleRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.updateArticle(PublicIds.toUuid(id), request.command())), traceId);
    }

    @PostMapping("/articles/{id}/publish")
    public ApiResponse<BlogViews.ArticleView> publish(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.publish(PublicIds.toUuid(id))), traceId);
    }

    @PostMapping("/articles/{id}/unpublish")
    public ApiResponse<BlogViews.ArticleView> unpublish(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.unpublish(PublicIds.toUuid(id))), traceId);
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
            String coverAssetId,
            String categoryId,
            Set<String> tagIds,
            String visibility) {
        ArticleDraftCommand command() {
            return new ArticleDraftCommand(
                    title,
                    slug,
                    summary,
                    body,
                    toUuid(coverAssetId),
                    toUuid(categoryId),
                    tagIds == null
                            ? Set.of()
                            : tagIds.stream().map(PublicIds::toUuid).collect(Collectors.toSet()),
                    visibility);
        }

        private UUID toUuid(String id) {
            return id == null || id.isBlank() ? null : PublicIds.toUuid(id);
        }
    }
}
