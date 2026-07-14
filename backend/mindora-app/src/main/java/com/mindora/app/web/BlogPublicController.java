package com.mindora.app.web;

import com.mindora.blog.application.ArticleService;
import com.mindora.common.api.ApiResponse;
import com.mindora.common.id.PublicIds;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/articles")
public class BlogPublicController {
    private final ArticleService articleService;

    public BlogPublicController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<java.util.List<BlogViews.ArticleView>> list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String tagId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(
                articleService.listPublic(toUuid(categoryId), toUuid(tagId)).stream()
                        .map(BlogViews::article)
                        .toList(),
                traceId);
    }

    @GetMapping("/{slug}")
    public ApiResponse<BlogViews.ArticleView> detail(
            @PathVariable String slug,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(BlogViews.article(articleService.getPublicBySlug(slug)), traceId);
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    private UUID toUuid(String id) {
        return id == null || id.isBlank() ? null : PublicIds.toUuid(id);
    }
}
