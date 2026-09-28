package com.mindora.knowledge.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.KnowledgeRetrievalService;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeEnums;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeAdminController {
    private final KnowledgeService service;
    private final KnowledgeRetrievalService retrievalService;
    private final RuntimeConfigView runtimeConfig;

    public KnowledgeAdminController(
            KnowledgeService service,
            KnowledgeRetrievalService retrievalService,
            @Value("${mindora.knowledge.embedding.provider:bailian}") String embeddingProvider,
            @Value("${mindora.knowledge.embedding.model:text-embedding-v4}") String embeddingModel,
            @Value("${mindora.knowledge.embedding.dimensions:1024}") int embeddingDimensions,
            @Value("${mindora.knowledge.rerank.provider:siliconflow}") String rerankProvider,
            @Value("${mindora.knowledge.rerank.model:BAAI/bge-reranker-v2-m3}") String rerankModel,
            @Value("${mindora.qdrant.collection:mindora_chunk}") String qdrantCollection) {
        this.service = service;
        this.retrievalService = retrievalService;
        this.runtimeConfig = new RuntimeConfigView(
                embeddingProvider,
                embeddingModel,
                embeddingDimensions,
                rerankProvider,
                rerankModel,
                qdrantCollection);
    }

    @GetMapping("/sources")
    public ApiResponse<List<KnowledgeViews.DataSourceView>> listSources(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listDataSources().stream().map(KnowledgeViews::dataSource).toList(), traceId);
    }

    @PostMapping("/sources")
    public ApiResponse<KnowledgeViews.DataSourceView> createSource(
            @Valid @RequestBody DataSourceRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(KnowledgeViews.dataSource(service.createDataSource(
                request.name(), request.repositoryUrl(), request.branch(), request.accessToken(),
                request.rootPath(), request.defaultVisibility(), request.enabled() == null || request.enabled())), traceId);
    }

    @PutMapping("/sources/{id}")
    public ApiResponse<KnowledgeViews.DataSourceView> updateSource(
            @PathVariable String id,
            @Valid @RequestBody DataSourceRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(KnowledgeViews.dataSource(service.updateDataSource(PublicIds.toUuid(id),
                request.name(), request.repositoryUrl(), request.branch(), request.accessToken(),
                request.rootPath(), request.defaultVisibility(), request.enabled() == null || request.enabled())), traceId);
    }

    @PostMapping("/sources/{id}/sync")
    public ApiResponse<KnowledgeViews.SyncTaskView> sync(
            @PathVariable String id,
            @RequestBody SyncRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        KnowledgeEnums.SyncMode mode = request == null || request.mode() == null
                ? KnowledgeEnums.SyncMode.INCREMENTAL
                : KnowledgeEnums.SyncMode.valueOf(request.mode().toUpperCase());
        return success(KnowledgeViews.task(service.triggerSync(PublicIds.toUuid(id), mode, traceId)), traceId);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<KnowledgeViews.SyncTaskView>> listTasks(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listTasks().stream().map(KnowledgeViews::task).toList(), traceId);
    }

    @PostMapping("/tasks/{id}/retry")
    public ApiResponse<KnowledgeViews.SyncTaskView> retryTask(
            @PathVariable long id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(KnowledgeViews.task(service.retryTask(id)), traceId);
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeViews.DocumentView>> listDocuments(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listDocuments().stream().map(KnowledgeViews::document).toList(), traceId);
    }

    @GetMapping("/documents/{id}/versions")
    public ApiResponse<List<KnowledgeViews.VersionView>> listVersions(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listVersions(PublicIds.toUuid(id)).stream().map(KnowledgeViews::version).toList(), traceId);
    }

    @PutMapping("/documents/{id}")
    public ApiResponse<KnowledgeViews.DocumentView> updateDocument(
            @PathVariable String id,
            @RequestBody DocumentSettingsRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        return success(KnowledgeViews.document(service.updateDocumentSettings(
                PublicIds.toUuid(id), enabled, request == null ? null : request.visibility())), traceId);
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<KnowledgeViews.ChunkView>> listChunks(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listChunks(PublicIds.toUuid(id)).stream().map(KnowledgeViews::chunk).toList(), traceId);
    }

    @PostMapping("/documents/{id}/reindex")
    public ApiResponse<Void> reindexDocument(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.rebuildDocument(PublicIds.toUuid(id));
        return success(null, traceId);
    }

    @PostMapping("/documents/{id}/article-draft")
    public ApiResponse<KnowledgeViews.DocumentView> createArticleDraft(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(KnowledgeViews.document(service.publishDocument(PublicIds.toUuid(id))), traceId);
    }

    @PostMapping("/documents/{id}/publish")
    public ApiResponse<KnowledgeViews.DocumentView> publishDocument(
            @PathVariable String id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return createArticleDraft(id, traceId);
    }

    @PostMapping("/documents/reindex")
    public ApiResponse<Integer> reindexDocuments(
            @RequestBody BatchReindexRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<UUID> ids = request == null || request.documentIds() == null
                ? List.of()
                : request.documentIds().stream().map(PublicIds::toUuid).toList();
        return success(service.rebuildDocuments(ids), traceId);
    }

    /**
     * 全量重建关键词索引。
     *
     * <p>关键词索引是 MySQL 切片的派生数据：丢失、损坏、或首次引入（历史数据尚未建索引）时
     * 都可由此恢复。该操作直接读取数据库中的当前切片，**不调用 embedding**，因此不消耗额度。
     */
    @PostMapping("/keyword-index/rebuild")
    public ApiResponse<Integer> rebuildKeywordIndex(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.rebuildKeywordIndex(), traceId);
    }

    @GetMapping("/config/chunking")
    public ApiResponse<KnowledgeViews.ChunkingConfigView> getChunking(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(KnowledgeViews.chunkingConfig(service.getChunkingConfig()), traceId);
    }

    @PutMapping("/config/chunking")
    public ApiResponse<KnowledgeViews.ChunkingConfigView> updateChunking(
            @RequestBody ChunkingConfigRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ChunkingConfig config = new ChunkingConfig(
                request == null || request.strategy() == null
                        ? KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING
                        : KnowledgeEnums.ChunkStrategy.valueOf(request.strategy().toUpperCase()),
                request == null ? 800 : request.chunkSize(),
                request == null ? 120 : request.overlap(),
                request == null ? 40 : request.minimumSize(),
                request == null ? 1600 : request.maximumSize(),
                request == null || request.preserveHeadingHierarchy(),
                request == null || request.includeMetadata());
        return success(KnowledgeViews.chunkingConfig(service.updateChunkingConfig(config)), traceId);
    }

    @GetMapping("/config/runtime")
    public ApiResponse<RuntimeConfigView> getRuntimeConfig(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(runtimeConfig, traceId);
    }

    @PostMapping("/rerank/preview")
    public ApiResponse<List<RerankResultView>> rerankPreview(
            @RequestBody RerankPreviewRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<RerankResultView> results = service.rerankPreview(
                        request == null ? null : request.query(),
                        request == null ? List.of() : request.documents(),
                        request == null ? 0 : request.topN())
                .stream()
                .map(result -> new RerankResultView(result.index(), result.score(), result.document()))
                .toList();
        return success(results, traceId);
    }

    /**
     * 管理端检索调试：输入 query，返回带向量分与重排分的召回结果。
     *
     * <p>{@code scope} 必填（{@code all} / {@code public}）；缺失或非法时由服务层拒绝，
     * 不会退化成「无过滤条件」的检索。
     */
    @PostMapping("/retrieval/search")
    public ApiResponse<RetrievalResultView> retrievalSearch(
            @RequestBody(required = false) RetrievalSearchRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        KnowledgeRetrievalService.RetrievalOutcome outcome = retrievalService.search(
                new KnowledgeRetrievalService.RetrievalQuery(
                        request == null ? null : request.query(),
                        parseScope(request == null ? null : request.scope()),
                        request == null ? null : request.topN(),
                        request == null ? null : request.rerankTopK(),
                        request == null ? null : request.rerankEnabled(),
                        request == null ? null : request.keywordEnabled()));
        return success(retrievalResult(outcome), traceId);
    }

    private KnowledgeRetrievalService.Scope parseScope(String value) {
        if (value == null || value.isBlank()) {
            // 交给服务层统一执行 fail-closed 校验，避免两处判断不一致
            return null;
        }
        try {
            return KnowledgeRetrievalService.Scope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "retrieval_scope_invalid", "Unsupported retrieval scope: " + value);
        }
    }

    private static RetrievalResultView retrievalResult(KnowledgeRetrievalService.RetrievalOutcome outcome) {
        return new RetrievalResultView(
                outcome.scope(),
                outcome.topN(),
                outcome.keywordTopN(),
                outcome.fusionTopM(),
                outcome.rerankTopK(),
                outcome.rerankEnabled(),
                outcome.keywordEnabled(),
                outcome.vectorCandidates(),
                outcome.keywordCandidates(),
                outcome.fallback(),
                outcome.fallbackReason(),
                outcome.keywordFallback(),
                outcome.keywordFallbackReason(),
                outcome.latencyMs(),
                outcome.results().stream()
                        .map(hit -> new RetrievalHitView(
                                hit.chunkId(),
                                hit.documentId(),
                                hit.documentTitle(),
                                hit.sourceType(),
                                hit.sourcePath(),
                                hit.headingPath(),
                                hit.blockType(),
                                hit.codeLanguage(),
                                hit.vectorScore(),
                                hit.rerankScore(),
                                hit.rrfScore(),
                                hit.vectorRank(),
                                hit.keywordRank(),
                                hit.matchedBy(),
                                hit.contentPreview(),
                                hit.articleId(),
                                hit.articleSlug()))
                        .toList());
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(data, traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    public record DataSourceRequest(
            @NotBlank String name,
            @NotBlank String repositoryUrl,
            String branch,
            String accessToken,
            String rootPath,
            String defaultVisibility,
            Boolean enabled) {
    }

    public record SyncRequest(String mode) {
    }

    public record BatchReindexRequest(List<String> documentIds) {
    }

    public record DocumentSettingsRequest(Boolean enabled, String visibility) {
    }

    public record RuntimeConfigView(
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimensions,
            String rerankProvider,
            String rerankModel,
            String qdrantCollection) {
    }

    public record RerankPreviewRequest(String query, List<String> documents, int topN) {
    }

    public record RerankResultView(int index, double score, String document) {
    }

    public record RetrievalSearchRequest(
            String query,
            String scope,
            Integer topN,
            Integer rerankTopK,
            Boolean rerankEnabled,
            Boolean keywordEnabled) {
    }

    public record RetrievalResultView(
            String scope,
            int topN,
            int keywordTopN,
            int fusionTopM,
            int rerankTopK,
            boolean rerankEnabled,
            boolean keywordEnabled,
            int vectorCandidates,
            int keywordCandidates,
            boolean fallback,
            String fallbackReason,
            boolean keywordFallback,
            String keywordFallbackReason,
            long latencyMs,
            List<RetrievalHitView> results) {
    }

    public record RetrievalHitView(
            String chunkId,
            String documentId,
            String documentTitle,
            String sourceType,
            String sourcePath,
            String headingPath,
            String blockType,
            String codeLanguage,
            Double vectorScore,
            Double rerankScore,
            Double rrfScore,
            Integer vectorRank,
            Integer keywordRank,
            String matchedBy,
            String contentPreview,
            String articleId,
            String articleSlug) {
    }

    public record ChunkingConfigRequest(
            String strategy,
            int chunkSize,
            int overlap,
            int minimumSize,
            int maximumSize,
            boolean preserveHeadingHierarchy,
            boolean includeMetadata) {
    }
}
