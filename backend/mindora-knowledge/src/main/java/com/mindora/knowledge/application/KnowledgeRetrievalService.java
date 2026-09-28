package com.mindora.knowledge.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.application.port.KnowledgeAdapters.EmbeddingProviderPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.KeywordHit;
import com.mindora.knowledge.application.port.KnowledgeAdapters.KeywordIndexPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.RerankProviderPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.VectorHit;
import com.mindora.knowledge.application.port.KnowledgeAdapters.VectorStorePort;
import com.mindora.knowledge.application.port.KnowledgeRepositories.KnowledgeRepository;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 检索编排服务：双路召回（向量 + 关键词）→ RRF 融合 → 可选重排序 → 回表补全展示字段。
 *
 * <p><b>为什么需要两路召回</b>：向量检索擅长语义相近但用词不同的表达，却容易漏掉精确的
 * 术语、错误码与代码标识符；关键词检索（BM25）恰好相反。两者互补，是业界 Hybrid Search 的
 * 基本做法。
 *
 * <p><b>为什么用 RRF 融合</b>：向量的余弦相似度与 BM25 分数量纲完全不同，直接加权需要归一化
 * 且很难调；RRF（Reciprocal Rank Fusion）只用「排名」不用「分数」，因此天然跨量纲可比。
 *
 * <p>权限隔离原则（见 design-04）：{@code scope} 必须显式给出，缺失即报错，不存在
 * 「不带过滤条件」的检索路径；过滤无法确定时失败而不是放行。**MySQL 是权限的权威来源**：
 * 索引里的权限字段只用于缩小召回范围，最终仍按数据库中的文档状态复核。
 *
 * <p>降级原则：重排序与关键词检索属于质量特性，失败可按配置降级（重排退化为融合顺序、
 * 关键词退化为纯向量检索）；而嵌入服务、向量库不可用属于能力缺失，直接返回明确的临时不可用错误。
 */
public class KnowledgeRetrievalService {

    private static final int MAX_TOP_N = 200;
    private static final int PREVIEW_LIMIT = 300;
    private static final int DEFAULT_RRF_K = 60;
    private static final int MAX_RANK = Integer.MAX_VALUE;
    private static final String PUBLIC_VISIBILITY = "public";

    /** 检索可见范围。 */
    public enum Scope {
        /** 管理端调试：不做 visibility 限制，但仍排除归档与已退出知识库的文档。 */
        ALL,
        /** 公开检索：仅 public 可见内容。 */
        PUBLIC
    }

    public record RetrievalSettings(
            int topN,
            int keywordTopN,
            int fusionTopM,
            int rerankTopK,
            boolean rerankEnabled,
            boolean keywordEnabled,
            boolean fallbackEnabled,
            int rrfK) {
        public RetrievalSettings {
            topN = topN <= 0 ? 30 : Math.min(topN, MAX_TOP_N);
            keywordTopN = keywordTopN <= 0 ? 30 : Math.min(keywordTopN, MAX_TOP_N);
            fusionTopM = fusionTopM <= 0 ? 20 : Math.min(fusionTopM, MAX_TOP_N);
            rerankTopK = rerankTopK <= 0 ? 8 : Math.min(rerankTopK, MAX_TOP_N);
            rrfK = rrfK <= 0 ? DEFAULT_RRF_K : rrfK;
        }

        public static RetrievalSettings defaults() {
            return new RetrievalSettings(30, 30, 20, 8, true, true, true, DEFAULT_RRF_K);
        }
    }

    public record RetrievalQuery(
            String query,
            Scope scope,
            Integer topN,
            Integer rerankTopK,
            Boolean rerankEnabled,
            Boolean keywordEnabled) {
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

    public record RetrievalOutcome(
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
            List<RetrievalHit> results) {
    }

    private final KnowledgeRepository repository;
    private final EmbeddingProviderPort embedding;
    private final RerankProviderPort rerank;
    private final VectorStorePort vectors;
    private final KeywordIndexPort keywordIndex;
    private final RetrievalSettings settings;

    public KnowledgeRetrievalService(
            KnowledgeRepository repository,
            EmbeddingProviderPort embedding,
            RerankProviderPort rerank,
            VectorStorePort vectors,
            KeywordIndexPort keywordIndex,
            RetrievalSettings settings) {
        this.repository = repository;
        this.embedding = embedding;
        this.rerank = rerank;
        this.vectors = vectors;
        this.keywordIndex = keywordIndex;
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
        int keywordTopN = Math.min(settings.keywordTopN(), MAX_TOP_N);
        int fusionTopM = Math.min(settings.fusionTopM(), MAX_TOP_N);
        int rerankTopK = query.rerankTopK() == null || query.rerankTopK() <= 0
                ? settings.rerankTopK()
                : Math.min(query.rerankTopK(), MAX_TOP_N);
        boolean rerankEnabled = query.rerankEnabled() == null ? settings.rerankEnabled() : query.rerankEnabled();
        boolean keywordEnabled = query.keywordEnabled() == null ? settings.keywordEnabled() : query.keywordEnabled();

        long startedAt = System.nanoTime();
        List<VectorHit> vectorHits = searchVectors(question, topN, scope);

        boolean keywordFallback = false;
        String keywordFallbackReason = null;
        List<KeywordHit> keywordHits = List.of();
        if (keywordEnabled) {
            try {
                keywordHits = keywordIndex.search(question, keywordTopN, vectorFilter(scope));
            } catch (RuntimeException exception) {
                // 关键词检索是质量特性：不可用时退化为纯向量检索，而不是让整次检索失败
                keywordFallback = true;
                keywordFallbackReason = message(exception);
            }
        }

        List<Fused> fused = fuse(vectorHits, keywordHits, settings.rrfK());
        List<Candidate> candidates = loadCandidates(fused, scope);

        boolean fallback = false;
        String fallbackReason = null;
        List<Candidate> ordered = candidates;
        if (rerankEnabled && candidates.size() > 1) {
            // 重排是逐条打分的开销大头，只让融合后的前列进入重排
            List<Candidate> toRerank = truncate(candidates, fusionTopM);
            try {
                ordered = applyRerank(question, toRerank, rerankTopK);
            } catch (RuntimeException exception) {
                if (!settings.fallbackEnabled()) {
                    throw new BusinessException(
                            "retrieval_rerank_unavailable",
                            "Rerank service is temporarily unavailable: " + message(exception));
                }
                fallback = true;
                fallbackReason = message(exception);
                ordered = toRerank;
            }
        } else if (candidates.size() > fusionTopM) {
            ordered = truncate(candidates, fusionTopM);
        }
        ordered = truncate(ordered, rerankTopK);
        List<RetrievalHit> results = ordered.stream().map(this::toHit).toList();
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        return new RetrievalOutcome(
                scope.name().toLowerCase(),
                topN,
                keywordTopN,
                fusionTopM,
                rerankTopK,
                rerankEnabled,
                keywordEnabled,
                vectorHits.size(),
                keywordHits.size(),
                fallback,
                fallbackReason,
                keywordFallback,
                keywordFallbackReason,
                latencyMs,
                results);
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
     * 构造索引过滤条件。任何 scope 都会排除归档文档与已退出知识库的文档；
     * {@code PUBLIC} 额外要求 {@code visibility=public}。向量与关键词两侧共用，保证隔离规则一致。
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

    /**
     * Reciprocal Rank Fusion：对每一路的排名取 {@code 1 / (k + rank)} 后求和。
     *
     * <p>只用排名不用分数，因此 BM25 与余弦相似度这两个不同量纲的分数无需归一化即可融合；
     * {@code k} 用于压低「排名极其靠前」的单项优势，使两路都能贡献候选，通用取值为 60。
     */
    private List<Fused> fuse(List<VectorHit> vectorHits, List<KeywordHit> keywordHits, int rrfK) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        Map<UUID, UUID> documentIds = new LinkedHashMap<>();
        Map<UUID, Double> vectorScores = new LinkedHashMap<>();
        Map<UUID, Integer> vectorRanks = new LinkedHashMap<>();
        Map<UUID, Integer> keywordRanks = new LinkedHashMap<>();
        for (int index = 0; index < vectorHits.size(); index++) {
            VectorHit hit = vectorHits.get(index);
            accumulate(scores, documentIds, hit.chunkId(), hit.documentId(), rrfK, index);
            vectorScores.putIfAbsent(hit.chunkId(), hit.score());
            vectorRanks.putIfAbsent(hit.chunkId(), index + 1);
        }
        for (int index = 0; index < keywordHits.size(); index++) {
            KeywordHit hit = keywordHits.get(index);
            accumulate(scores, documentIds, hit.chunkId(), hit.documentId(), rrfK, index);
            keywordRanks.putIfAbsent(hit.chunkId(), index + 1);
        }
        return scores.entrySet().stream()
                .map(entry -> new Fused(
                        entry.getKey(),
                        documentIds.get(entry.getKey()),
                        vectorScores.get(entry.getKey()),
                        entry.getValue(),
                        vectorRanks.get(entry.getKey()),
                        keywordRanks.get(entry.getKey())))
                .sorted(Comparator.comparingDouble(Fused::rrfScore)
                        .reversed()
                        .thenComparing(fused -> fused.vectorRank() == null ? MAX_RANK : fused.vectorRank())
                        .thenComparing(Fused::chunkId))
                .toList();
    }

    private void accumulate(
            Map<UUID, Double> scores,
            Map<UUID, UUID> documentIds,
            UUID chunkId,
            UUID documentId,
            int rrfK,
            int index) {
        scores.merge(chunkId, 1.0 / (rrfK + index + 1.0), Double::sum);
        documentIds.putIfAbsent(chunkId, documentId);
    }

    private List<Candidate> loadCandidates(List<Fused> fused, Scope scope) {
        if (fused.isEmpty()) {
            return List.of();
        }
        Set<UUID> chunkIds = fused.stream().map(Fused::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> documentIds = fused.stream().map(Fused::documentId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, KnowledgeChunk> chunks = repository.findChunksByIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::id, value -> value, (left, right) -> left));
        Map<UUID, KnowledgeDocument> documents = repository.findDocumentsByIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::id, value -> value, (left, right) -> left));
        List<Candidate> candidates = new ArrayList<>();
        for (Fused entry : fused) {
            KnowledgeChunk chunk = chunks.get(entry.chunkId());
            KnowledgeDocument document = entry.documentId() == null ? null : documents.get(entry.documentId());
            if (chunk == null || document == null) {
                // 索引与 MySQL 不一致时跳过，避免返回无法溯源的引用
                continue;
            }
            // MySQL 是权限的权威来源：索引里的权限字段可能因设置变更尚未回写而残留旧值，
            // 这里按文档当前状态复核，确保权限隔离不依赖派生索引的新鲜度
            if (!permitted(document, scope)) {
                continue;
            }
            candidates.add(new Candidate(
                    chunk,
                    document,
                    entry.vectorScore(),
                    entry.rrfScore(),
                    entry.vectorRank(),
                    entry.keywordRank(),
                    null));
        }
        return candidates;
    }

    /** 与索引过滤条件同义的权威校验，作用于 MySQL 中的文档状态。 */
    private boolean permitted(KnowledgeDocument document, Scope scope) {
        if (document.status() != KnowledgeEnums.DocumentStatus.ACTIVE || !document.knowledgeEnabled()) {
            return false;
        }
        return scope != Scope.PUBLIC || PUBLIC_VISIBILITY.equalsIgnoreCase(document.visibility());
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
        // 重排序未返回可用结果时退回融合顺序，不视为失败
        return ordered.isEmpty() ? candidates : ordered;
    }

    private String rerankText(Candidate candidate) {
        String headingPath = candidate.chunk().headingPath();
        return headingPath == null || headingPath.isBlank()
                ? candidate.chunk().content()
                : headingPath + "\n" + candidate.chunk().content();
    }

    private <T> List<T> truncate(List<T> values, int limit) {
        return values.size() <= limit ? values : values.subList(0, limit);
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
                candidate.rrfScore(),
                candidate.vectorRank(),
                candidate.keywordRank(),
                candidate.matchedBy(),
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

    /** 融合后的候选：保留两路各自的排名，便于解释「这条为什么被召回」。 */
    private record Fused(
            UUID chunkId,
            UUID documentId,
            Double vectorScore,
            double rrfScore,
            Integer vectorRank,
            Integer keywordRank) {
    }

    private record Candidate(
            KnowledgeChunk chunk,
            KnowledgeDocument document,
            Double vectorScore,
            Double rrfScore,
            Integer vectorRank,
            Integer keywordRank,
            Double rerankScore) {

        Candidate withRerankScore(double score) {
            return new Candidate(chunk, document, vectorScore, rrfScore, vectorRank, keywordRank, score);
        }

        /** 命中来源：仅向量、仅关键词、或两路都命中（两路都命中通常说明结果更可信）。 */
        String matchedBy() {
            if (vectorRank != null && keywordRank != null) {
                return "both";
            }
            return keywordRank != null ? "keyword" : "vector";
        }
    }
}
