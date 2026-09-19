package com.mindora.knowledge.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.application.port.KnowledgeAdapters.EmbeddingProviderPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.RerankProviderPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.VectorHit;
import com.mindora.knowledge.application.port.KnowledgeAdapters.VectorStorePort;
import com.mindora.knowledge.application.port.KnowledgeRepositories.KnowledgeRepository;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 检索编排服务：查询向量化 → 向量召回 → 可选重排序 → 回表补全展示字段。
 *
 * <p>权限隔离原则（见 design-04）：{@code scope} 必须显式给出，缺失即报错，不存在
 * 「不带过滤条件」的检索路径；过滤无法确定时失败而不是放行。
 *
 * <p>降级原则：重排序属于质量特性，失败可按配置降级为向量相似度顺序；而嵌入服务、
 * 向量库不可用属于能力缺失，直接返回明确的临时不可用错误。
 */
public class KnowledgeRetrievalService {

    private static final int MAX_TOP_N = 200;
    private static final int PREVIEW_LIMIT = 300;
    private static final String PUBLIC_VISIBILITY = "public";

    /** 检索可见范围。 */
    public enum Scope {
        /** 管理端调试：不做 visibility 限制，但仍排除归档与已退出知识库的文档。 */
        ALL,
        /** 公开检索：仅 public 可见内容。 */
        PUBLIC
    }

    public record RetrievalSettings(int topN, int rerankTopK, boolean rerankEnabled, boolean fallbackEnabled) {
        public RetrievalSettings {
            topN = topN <= 0 ? 30 : Math.min(topN, MAX_TOP_N);
            rerankTopK = rerankTopK <= 0 ? Math.min(8, topN) : Math.min(rerankTopK, topN);
        }

        public static RetrievalSettings defaults() {
            return new RetrievalSettings(30, 8, true, true);
        }
    }

    public record RetrievalQuery(
            String query,
            Scope scope,
            Integer topN,
            Integer rerankTopK,
            Boolean rerankEnabled) {
    }

    public record RetrievalHit(
            String chunkId,
            String documentId,
            String documentTitle,
            String sourceType,
            String sourcePath,
            String headingPath,
            String blockType,
            String codeLanguage,
            double vectorScore,
            Double rerankScore,
            String contentPreview,
            String articleId,
            String articleSlug) {
    }

    public record RetrievalOutcome(
            String scope,
            int topN,
            int rerankTopK,
            boolean rerankEnabled,
            boolean fallback,
            String fallbackReason,
            long latencyMs,
            List<RetrievalHit> results) {
    }

    private final KnowledgeRepository repository;
    private final EmbeddingProviderPort embedding;
    private final RerankProviderPort rerank;
    private final VectorStorePort vectors;
    private final RetrievalSettings settings;

    public KnowledgeRetrievalService(
            KnowledgeRepository repository,
            EmbeddingProviderPort embedding,
            RerankProviderPort rerank,
            VectorStorePort vectors,
            RetrievalSettings settings) {
        this.repository = repository;
        this.embedding = embedding;
        this.rerank = rerank;
        this.vectors = vectors;
        this.settings = settings == null ? RetrievalSettings.defaults() : settings;
    }

    public RetrievalOutcome search(RetrievalQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            throw new BusinessException("retrieval_query_required", "Retrieval query is required");
        }
        Scope scope = query.scope();
        if (scope == null) {
            throw new BusinessException("retrieval_scope_required", "Retrieval scope is required");
        }
        String question = query.query().trim();
        int topN = query.topN() == null || query.topN() <= 0
                ? settings.topN()
                : Math.min(query.topN(), MAX_TOP_N);
        int rerankTopK = query.rerankTopK() == null || query.rerankTopK() <= 0
                ? Math.min(settings.rerankTopK(), topN)
                : Math.min(query.rerankTopK(), topN);
        boolean rerankEnabled = query.rerankEnabled() == null ? settings.rerankEnabled() : query.rerankEnabled();

        long startedAt = System.nanoTime();
        List<Candidate> candidates = loadCandidates(searchVectors(question, topN, scope));

        boolean fallback = false;
        String fallbackReason = null;
        List<Candidate> ordered = candidates;
        if (rerankEnabled && candidates.size() > 1) {
            try {
                ordered = applyRerank(question, candidates, rerankTopK);
            } catch (RuntimeException exception) {
                if (!settings.fallbackEnabled()) {
                    throw new BusinessException(
                            "retrieval_rerank_unavailable",
                            "Rerank service is temporarily unavailable: " + message(exception));
                }
                fallback = true;
                fallbackReason = message(exception);
                ordered = candidates;
            }
        }
        if (ordered.size() > rerankTopK) {
            ordered = ordered.subList(0, rerankTopK);
        }
        List<RetrievalHit> results = ordered.stream().map(this::toHit).toList();
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        return new RetrievalOutcome(
                scope.name().toLowerCase(), topN, rerankTopK, rerankEnabled, fallback, fallbackReason,
                latencyMs, results);
    }

    private List<VectorHit> searchVectors(String question, int topN, Scope scope) {
        List<Double> queryVector = embedQuery(question);
        try {
            return vectors.search(queryVector, topN, vectorFilter(scope));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "retrieval_vector_unavailable",
                    "Knowledge retrieval is temporarily unavailable: " + message(exception));
        }
    }

    private List<Double> embedQuery(String question) {
        List<List<Double>> embeddings;
        try {
            embeddings = embedding.embed(List.of(question));
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "retrieval_embedding_unavailable",
                    "Retrieval embedding service is temporarily unavailable: " + message(exception));
        }
        if (embeddings == null || embeddings.isEmpty() || embeddings.getFirst() == null
                || embeddings.getFirst().isEmpty()) {
            throw new BusinessException(
                    "retrieval_embedding_unavailable", "Embedding provider returned no query vector");
        }
        return embeddings.getFirst();
    }

    /**
     * 构造向量过滤条件。任何 scope 都会排除归档文档与已退出知识库的文档；
     * {@code PUBLIC} 额外要求 {@code visibility=public}。
     */
    private Map<String, Object> vectorFilter(Scope scope) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("status", KnowledgeEnums.DocumentStatus.ACTIVE.name().toLowerCase());
        filter.put("knowledgeEnabled", true);
        if (scope == Scope.PUBLIC) {
            filter.put("visibility", PUBLIC_VISIBILITY);
        }
        return filter;
    }

    private List<Candidate> loadCandidates(List<VectorHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        Set<UUID> chunkIds = hits.stream().map(VectorHit::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> documentIds = hits.stream().map(VectorHit::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, KnowledgeChunk> chunks = repository.findChunksByIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::id, value -> value, (left, right) -> left));
        Map<UUID, KnowledgeDocument> documents = repository.findDocumentsByIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::id, value -> value, (left, right) -> left));
        List<Candidate> candidates = new ArrayList<>();
        for (VectorHit hit : hits) {
            KnowledgeChunk chunk = chunks.get(hit.chunkId());
            KnowledgeDocument document = documents.get(hit.documentId());
            if (chunk == null || document == null) {
                // 向量库与 MySQL 不一致时跳过，避免返回无法溯源的引用
                continue;
            }
            candidates.add(new Candidate(chunk, document, hit.score(), null));
        }
        return candidates;
    }

    private List<Candidate> applyRerank(String question, List<Candidate> candidates, int rerankTopK) {
        List<String> documents = candidates.stream().map(this::rerankText).toList();
        List<KnowledgeAdapters.RerankResult> results = rerank.rerank(question, documents, rerankTopK);
        List<Candidate> ordered = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (KnowledgeAdapters.RerankResult result : results) {
            int index = result.index();
            if (index < 0 || index >= candidates.size() || !seen.add(index)) {
                continue;
            }
            ordered.add(candidates.get(index).withRerankScore(result.score()));
        }
        // 重排序未返回可用结果时退回向量顺序，不视为失败
        return ordered.isEmpty() ? candidates : ordered;
    }

    private String rerankText(Candidate candidate) {
        String headingPath = candidate.chunk().headingPath();
        return headingPath == null || headingPath.isBlank()
                ? candidate.chunk().content()
                : headingPath + "\n" + candidate.chunk().content();
    }

    private RetrievalHit toHit(Candidate candidate) {
        KnowledgeChunk chunk = candidate.chunk();
        KnowledgeDocument document = candidate.document();
        return new RetrievalHit(
                chunk.id().toString(),
                document.id().toString(),
                document.title(),
                document.sourceType().name().toLowerCase(),
                document.sourcePath(),
                chunk.headingPath(),
                chunk.blockType().name().toLowerCase(),
                chunk.codeLanguage(),
                candidate.vectorScore(),
                candidate.rerankScore(),
                preview(chunk.content()),
                document.publicArticleId(),
                document.articleSlug());
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= PREVIEW_LIMIT
                ? trimmed
                : trimmed.substring(0, PREVIEW_LIMIT) + "…";
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record Candidate(
            KnowledgeChunk chunk,
            KnowledgeDocument document,
            double vectorScore,
            Double rerankScore) {

        Candidate withRerankScore(double score) {
            return new Candidate(chunk, document, vectorScore, score);
        }
    }
}
