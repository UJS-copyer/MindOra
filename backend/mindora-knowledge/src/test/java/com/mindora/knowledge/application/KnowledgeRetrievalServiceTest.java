package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private final InMemoryKnowledgeRepositories.Knowledge repository = new InMemoryKnowledgeRepositories.Knowledge();
    private final List<Map<String, Object>> capturedFilters = new ArrayList<>();
    private final List<String> capturedRerankDocuments = new ArrayList<>();

    private boolean embeddingFails;
    private boolean vectorFails;
    private boolean rerankFails;
    private List<KnowledgeAdapters.VectorHit> hits = List.of();
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

        assertThatThrownBy(() -> service(defaultSettings()).search(new RetrievalQuery("问题", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scope");

        assertThat(capturedFilters).isEmpty();
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> service(defaultSettings())
                .search(new RetrievalQuery("   ", Scope.PUBLIC, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("query");
    }

    // ------------------------------------------------------------------
    // 降级与失败语义
    // ------------------------------------------------------------------

    @Test
    void fallsBackToVectorOrderWhenRerankFails() {
        seedDocument("public", true, KnowledgeEnums.DocumentStatus.ACTIVE, "blog:aqs-notes", "42");
        hits = List.of(hit(CHUNK_A, 0.9), hit(CHUNK_B, 0.8));
        rerankFails = true;

        var outcome = service(new RetrievalSettings(10, 2, true, true)).search(query(Scope.ALL));

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

        assertThatThrownBy(() -> service(new RetrievalSettings(10, 2, true, false)).search(query(Scope.ALL)))
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

        var outcome = service(new RetrievalSettings(10, 2, true, true)).search(query(Scope.ALL));

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
        assertThat(result.contentPreview()).endsWith("…");
        assertThat(outcome.scope()).isEqualTo("public");
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
    // 测试替身
    // ------------------------------------------------------------------

    private RetrievalSettings defaultSettings() {
        return new RetrievalSettings(10, 2, true, true);
    }

    private RetrievalQuery query(Scope scope) {
        return new RetrievalQuery("AQS 是什么", scope, null, null, null);
    }

    private KnowledgeAdapters.VectorHit hit(UUID chunkId, double score) {
        return new KnowledgeAdapters.VectorHit(
                chunkId, DOCUMENT_ID, VERSION_ID, score,
                Map.of("chunkId", chunkId.toString(), "documentId", DOCUMENT_ID.toString()));
    }

    private void seedDocument(
            String visibility,
            boolean knowledgeEnabled,
            KnowledgeEnums.DocumentStatus status,
            String sourcePath,
            String articleId) {
        Instant now = Instant.now();
        repository.saveDocument(new KnowledgeDocument(
                DOCUMENT_ID, KnowledgeEnums.SourceType.BLOG, "article-1", sourcePath, "文章标题",
                VERSION_ID.toString(), "hash", visibility, knowledgeEnabled, articleId,
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
                    public List<KnowledgeAdapters.VectorHit> search(
                            List<Double> queryVector, int topN, Map<String, Object> exactMatchFilter) {
                        if (vectorFails) {
                            throw new IllegalStateException("vector down");
                        }
                        capturedFilters.add(exactMatchFilter);
                        return hits;
                    }
                },
                settings);
    }
}
