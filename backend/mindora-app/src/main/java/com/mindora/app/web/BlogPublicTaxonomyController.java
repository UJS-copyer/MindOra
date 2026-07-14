package com.mindora.app.web;

import com.mindora.blog.application.ArticleService;
import com.mindora.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class BlogPublicTaxonomyController {
    private final ArticleService articleService;

    public BlogPublicTaxonomyController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/categories")
    public ApiResponse<java.util.List<BlogViews.CategoryView>> categories(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(articleService.listCategories().stream()
                .map(BlogViews::category)
                .toList(), traceId);
    }

    @GetMapping("/tags")
    public ApiResponse<java.util.List<BlogViews.TagView>> tags(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(articleService.listTags().stream()
                .map(BlogViews::tag)
                .toList(), traceId);
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }
}
