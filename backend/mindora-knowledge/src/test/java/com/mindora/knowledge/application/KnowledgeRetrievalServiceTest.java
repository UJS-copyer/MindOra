package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalHit;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalQuery;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalSettings;
import com.mindora.knowledge.application.KnowledgeRetrievalService.Scope;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.infrastructure.persistence.memory.InMemoryKnowledgeRepositories;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalServiceTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID CHUNK_A = UUID.randomUUID();
    private static final UUID CHUNK_B = UUID.randomUUID();
    private static final int RRF_K = 60;

    private final InMemoryKnowledgeRepositories.Knowledge repository = new InMemoryKnowledgeRepositories.Knowledge();
    private final List<Map<String, Object>> capturedFilters = new ArrayList<>();
    private final List<Map<String, Object>> capturedKeywordFilters = new ArrayList<>();
    private final List<String> capturedRerankDocuments = new ArrayList<>();

    private boolean embeddingFails;
    private boolean vectorFails;
    private boolean rerankFails;
    private boolean keywordFails;
    private List<KnowledgeAdapters.VectorHit> hits = List.of();
    private List<KnowledgeAdapters.KeywordHit> keywordHits = List.of();
    private List<KnowledgeAdapters.RerankResult> rerankResults = List.of();

    // ------------------------------------------------------------------
    // 权限过滤（fail-closed）
    // ------------------------------------------------------------------

    @Test
    void publicScopeFiltersVisibilityStatusAndKnowledgeFlag() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));

        service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(capturedFilters).hasSize(1);
        assertThat(capturedFilters.getFirst())
                .containsEntry("visibility", "public")
                .containsEntry("status", "active")
                .containsEntry("knowledgeEnabled", true);
    }

    @Test
    void allScopeRelaxesVisibilityButKeepsSafetyFilters() {
        seedDocument("private", true, KnowledgeEnums.DocumentStatus.ACTIVE, "docs/a.md", null);
        hits = List.of(hit(CHUNK_A, 0.9));

        service(defaultSettings()).search(query(Scope.ALL));

        assertThat(capturedFilters.getFirst())
                .doesNotContainKey("visibility")
                .containsEntry("status", "active")
                .containsEntry("knowledgeEnabled", true);
    }

    @Test
    void rejectsMissingScopeWithoutTouchingVectorStore() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");

        assertThatThrownBy(() -> service(defaultSettings()).search(new RetrievalQuery("问题", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scope");

        assertThat(capturedFilters).isEmpty();
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> service(defaultSettings())
                .search(new RetrievalQuery("   ", Scope.PUBLIC, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("query");
    }

    // ------------------------------------------------------------------
    // 双路召回与 RRF 融合
    // ------------------------------------------------------------------

    @Test
    void fusesBothPathsAndRanksDoubleMatchedChunkFirst() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        keywordHits = List.of(keywordHit(CHUNK_B, 12.0));

        var outcome = service(defaultSettings()).search(hybridQuery(Scope.ALL));

        assertThat(outcome.vectorCandidates()).isEqualTo(2);
        assertThat(outcome.keywordCandidates()).isEqualTo(1);
        // CHUNK_B 被两路命中，RRF = 1/(k+2) + 1/(k+1)，必然高于只被向量命中的 CHUNK_A
        RetrievalHit first = outcome.results().getFirst();
        assertThat(first.chunkId()).isEqualTo(CHUNK_B.toString());
        assertThat(first.matchedBy()).isEqualTo("both");
        assertThat(first.vectorRank()).isEqualTo(2);
        assertThat(first.keywordRank()).isEqualTo(1);
        assertThat(first.rrfScore())
                .isCloseTo(1.0 / (RRF_K + 2) + 1.0 / (RRF_K + 1), within(1e-9));
    }

    @Test
    void keepsKeywordOnlyHitsAndMarksTheirSource() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));
        keywordHits = List.of(keywordHit(CHUNK_B, 12.0));

        var outcome = service(defaultSettings()).search(hybridQuery(Scope.ALL));

        RetrievalHit keywordOnly = outcome.results().stream()
                .filter(result -> CHUNK_B.toString().equals(result.chunkId()))
                .findFirst()
                .orElseThrow();
        assertThat(keywordOnly.matchedBy()).isEqualTo("keyword");
        assertThat(keywordOnly.keywordRank()).isEqualTo(1);
        assertThat(keywordOnly.vectorRank()).isNull();
        // 仅关键词命中时没有向量分数，展示端需要能处理空值
        assertThat(keywordOnly.vectorScore()).isNull();
    }

    @Test
    void appliesSamePermissionFilterToKeywordRetrieval() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));
        keywordHits = List.of(keywordHit(CHUNK_B, 5.0));

        service(defaultSettings()).search(hybridQuery(Scope.PUBLIC));

        // 两路必须共用同一套隔离规则，否则融合结果会绕过权限
        assertThat(capturedKeywordFilters).hasSize(1);
        assertThat(capturedKeywordFilters.getFirst())
                .isEqualTo(capturedFilters.getFirst())
                .containsEntry("visibility", "public")
                .containsEntry("status", "active")
                .containsEntry("knowledgeEnabled", true);
    }

    @Test
    void skipsKeywordRetrievalWhenDisabled() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));

        var outcome = service(defaultSettings()).search(query(Scope.ALL));

        assertThat(capturedKeywordFilters).isEmpty();
        assertThat(outcome.keywordEnabled()).isFalse();
        assertThat(outcome.keywordCandidates()).isZero();
        assertThat(outcome.results()).extracting(RetrievalHit::chunkId)
                .containsExactly(CHUNK_A.toString());
    }

    @Test
    void degradesToVectorOnlyWhenKeywordIndexUnavailable() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));
        keywordFails = true;

        var outcome = service(defaultSettings()).search(hybridQuery(Scope.ALL));

        // 关键词检索是质量特性：不可用时降级为纯向量检索，而不是让整次检索失败
        assertThat(outcome.keywordFallback()).isTrue();
        assertThat(outcome.keywordFallbackReason()).contains("keyword down");
        assertThat(outcome.results()).extracting(RetrievalHit::chunkId)
                .containsExactly(CHUNK_A.toString());
    }

    @Test
    void respectsFusionTopMBeforeRerank() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        rerankResults = List.of(
                new KnowledgeAdapters.RerankResult(0, 0.95, "a"),
                new KnowledgeAdapters.RerankResult(1, 0.50, "b"));

        // fusionTopM=1：只有融合后的第一条进入重排，避免对全部候选逐条打分
        var outcome = service(new RetrievalSettings(10, 10, 1, 2, true, true, true, RRF_K))
                .search(hybridQuery(Scope.ALL));

        assertThat(capturedRerankDocuments).hasSize(1);
        assertThat(outcome.results()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 降级与失败语义
    // ------------------------------------------------------------------

    @Test
    void fallsBackToVectorOrderWhenRerankFails() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        rerankFails = true;

        var outcome = service(rerankSettings(true)).search(query(Scope.ALL));

        assertThat(outcome.fallback()).isTrue();
        assertThat(outcome.fallbackReason()).contains("rerank down");
        assertThat(outcome.results()).extracting(RetrievalHit::chunkId)
                .containsExactly(CHUNK_A.toString(), CHUNK_B.toString());
    }

    @Test
    void failsWhenRerankUnavailableAndFallbackDisabled() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        rerankFails = true;

        assertThatThrownBy(() -> service(rerankSettings(false)).search(query(Scope.ALL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Rerank service is temporarily unavailable");
    }

    @Test
    void reportsEmbeddingUnavailable() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        embeddingFails = true;

        assertThatThrownBy(() -> service(defaultSettings()).search(query(Scope.PUBLIC)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("embedding service is temporarily unavailable");
    }

    @Test
    void reportsVectorStoreUnavailable() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        vectorFails = true;

        assertThatThrownBy(() -> service(defaultSettings()).search(query(Scope.PUBLIC)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Knowledge retrieval is temporarily unavailable");
    }

    // ------------------------------------------------------------------
    // 重排与结果装配
    // ------------------------------------------------------------------

    @Test
    void rerankReordersResultsAndRecordsScores() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        rerankResults = List.of(
                new KnowledgeAdapters.RerankResult(1, 0.95, "b"),
                new KnowledgeAdapters.RerankResult(0, 0.50, "a"));

        var outcome = service(rerankSettings(true)).search(query(Scope.ALL));

        assertThat(outcome.fallback()).isFalse();
        assertThat(outcome.results()).extracting(RetrievalHit::chunkId)
                .containsExactly(CHUNK_B.toString(), CHUNK_A.toString());
        assertThat(outcome.results().getFirst().rerankScore()).isEqualTo(0.95);
        assertThat(capturedRerankDocuments).hasSize(2);
    }

    @Test
    void hydratesDisplayFieldsAndTruncatesPreview() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9));

        var outcome = service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(outcome.results()).hasSize(1);
        RetrievalHit result = outcome.results().getFirst();
        assertThat(result.documentId()).isEqualTo(DOCUMENT_ID.toString());
        assertThat(result.documentTitle()).isEqualTo("文章标题");
        assertThat(result.sourceType()).isEqualTo("blog");
        assertThat(result.sourcePath()).isEqualTo("blog:aqs-notes");
        assertThat(result.articleId()).isEqualTo("42");
        assertThat(result.articleSlug()).isEqualTo("aqs-notes");
        assertThat(result.headingPath()).isEqualTo("章节");
        assertThat(result.blockType()).isEqualTo("paragraph");
        assertThat(result.vectorScore()).isEqualTo(0.9);
        assertThat(result.matchedBy()).isEqualTo("vector");
        assertThat(result.contentPreview()).endsWith("…");
        assertThat(outcome.scope()).isEqualTo("public");
    }

    @Test
    void hydratesArticleSlugForNonBlogSourceWhenPersisted() {
        // 回归（Stage 3.4 §11.8-F1）：GITEE 来源文档发布为公开文章后，
        // slug 由发布动作持久化，不再依赖 sourcePath 的 "blog:" 前缀派生。
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "docs/阅读线路.md", "42", "knowledge-ab12cd34");
        hits = List.of(hit(CHUNK_A, 0.9));

        var outcome = service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.results().getFirst().articleSlug()).isEqualTo("knowledge-ab12cd34");
    }

    @Test
    void skipsHitsWithoutMatchingChunk() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(UUID.randomUUID(), 0.99));

        var outcome = service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(outcome.results()).extracting(RetrievalHit::chunkId).containsExactly(CHUNK_A.toString());
    }

    @Test
    void returnsEmptyResultWhenVectorStoreHasNoMatch() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of();

        var outcome = service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.fallback()).isFalse();
    }

    // ------------------------------------------------------------------
    // 权限权威来源（MySQL 复核）
    // ------------------------------------------------------------------

    @Test
    void dropsDocumentsThatAreNoLongerPermittedAccordingToDatabase() {
        // 索引 payload 声称 public，但 MySQL 中该文档已是 private 且已退出知识库：
        // 权限以数据库为准，必须被丢弃，否则会出现私有内容泄漏
        seedDocument("private", false, KnowledgeEnums.DocumentStatus.ACTIVE, "docs/a.md", null);
        hits = List.of(hit(CHUNK_A, 0.9));

        var outcome = service(defaultSettings()).search(query(Scope.PUBLIC));

        assertThat(outcome.results()).isEmpty();
    }

    @Test
    void dropsArchivedDocumentsEvenWhenIndexStillReturnsThem() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ARCHIVED, "docs/a.md", null);
        hits = List.of(hit(CHUNK_A, 0.9));

        assertThat(service(defaultSettings()).search(query(Scope.ALL)).results()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 测试替身
    // ------------------------------------------------------------------

    /** 默认关闭重排，便于直接观察 RRF 融合顺序。 */
    private RetrievalSettings defaultSettings() {
        return new RetrievalSettings(10, 10, 10, 2, false, true, true, RRF_K);
    }

    private RetrievalSettings rerankSettings(boolean fallbackEnabled) {
        return new RetrievalSettings(10, 10, 10, 2, true, true, fallbackEnabled, RRF_K);
    }

    /** 仅向量路径，保持既有用例聚焦。 */
    private RetrievalQuery query(Scope scope) {
        return new RetrievalQuery("AQS 是什么", scope, null, null, null, Boolean.FALSE);
    }

    /** 双路召回路径。 */
    private RetrievalQuery hybridQuery(Scope scope) {
        return new RetrievalQuery("AQS 是什么", scope, null, null, null, Boolean.TRUE);
    }

    private KnowledgeAdapters.VectorHit hit(UUID chunkId, double score) {
        return new KnowledgeAdapters.VectorHit(
                chunkId, DOCUMENT_ID, VERSION_ID, score,
                Map.of("chunkId", chunkId.toString(), "documentId", DOCUMENT_ID.toString()));
    }

    private KnowledgeAdapters.KeywordHit keywordHit(UUID chunkId, double score) {
        return new KnowledgeAdapters.KeywordHit(chunkId, DOCUMENT_ID, score);
    }

    private void seedDocument(
            String visibility,
            boolean knowledgeEnabled,
            KnowledgeEnums.DocumentStatus status,
            String sourcePath,
            String articleId) {
        seedDocument(visibility, knowledgeEnabled, status, sourcePath, articleId,
                sourcePath.startsWith("blog:") ? sourcePath.substring("blog:".length()) : null);
    }

    private void seedDocument(
            String visibility,
            boolean knowledgeEnabled,
            KnowledgeEnums.DocumentStatus status,
            String sourcePath,
            String articleId,
            String articleSlug) {
        Instant now = Instant.now();
        repository.saveDocument(new KnowledgeDocument(
                DOCUMENT_ID, KnowledgeEnums.SourceType.BLOG, "article-1", sourcePath, "文章标题",
                VERSION_ID.toString(), "hash", visibility, knowledgeEnabled, articleId, articleSlug,
                status, KnowledgeEnums.ParseStatus.PARSED, KnowledgeEnums.IndexStatus.INDEXED, null, 0,
                now, now));
        repository.saveChunk(chunk(CHUNK_A, 0, "章节", "正文内容 ".repeat(80)));
        repository.saveChunk(chunk(CHUNK_B, 1, null, "正文内容 B"));
    }

    private KnowledgeChunk chunk(UUID id, int sequence, String headingPath, String content) {
        return new KnowledgeChunk(
                id, VERSION_ID, sequence, content, content.length(),
                headingPath, headingPath == null ? 0 : 1, KnowledgeEnums.BlockType.PARAGRAPH, null,
                "hash-" + sequence, KnowledgeEnums.IndexStatus.INDEXED, "test-embedding",
                id.toString(), null, 0, Instant.now());
    }

    private KnowledgeRetrievalService service(RetrievalSettings settings) {
        return new KnowledgeRetrievalService(
                repository,
                new KnowledgeAdapters.EmbeddingProviderPort() {
                    @Override
                    public String provider() {
                        return "test";
                    }

                    @Override
                    public String model() {
                        return "test-embedding";
                    }

                    @Override
                    public List<List<Double>> embed(List<String> contents) {
                        if (embeddingFails) {
                            throw new IllegalStateException("embedding down");
                        }
                        return contents.stream().map(content -> List.of(0.1, 0.2)).toList();
                    }
                },
                new KnowledgeAdapters.RerankProviderPort() {
                    @Override
                    public String provider() {
                        return "test";
                    }

                    @Override
                    public String model() {
                        return "test-rerank";
                    }

                    @Override
                    public List<KnowledgeAdapters.RerankResult> rerank(
                            String query, List<String> documents, int topN) {
                        if (rerankFails) {
                            throw new IllegalStateException("rerank down");
                        }
                        capturedRerankDocuments.clear();
                        capturedRerankDocuments.addAll(documents);
                        return rerankResults;
                    }
                },
                new KnowledgeAdapters.VectorStorePort() {
                    @Override
                    public void upsert(List<KnowledgeAdapters.VectorRecord> points, List<List<Double>> embeddings) {
                    }

                    @Override
                    public void deleteByVersion(UUID documentVersionId) {
                    }

                    @Override
                    public void updatePayload(UUID documentVersionId, Map<String, Object> payload) {
                    }

                    @Override
                    public List<KnowledgeAdapters.VectorHit> search(
                            List<Double> queryVector, int topN, Map<String, Object> exactMatchFilter) {
                        if (vectorFails) {
                            throw new IllegalStateException("vector down");
                        }
                        capturedFilters.add(exactMatchFilter);
                        return hits;
                    }
                },
                new KnowledgeAdapters.KeywordIndexPort() {
                    @Override
                    public void replaceDocument(UUID documentId, List<KnowledgeAdapters.KeywordDocument> documents) {
                    }

                    @Override
                    public void deleteDocument(UUID documentId) {
                    }

                    @Override
                    public void deleteAll() {
                    }

                    @Override
                    public List<KnowledgeAdapters.KeywordHit> search(
                            String query, int topN, Map<String, Object> exactMatchFilter) {
                        if (keywordFails) {
                            throw new IllegalStateException("keyword down");
                        }
                        capturedKeywordFilters.add(exactMatchFilter);
                        return keywordHits;
                    }
                },
                settings);
    }
}
