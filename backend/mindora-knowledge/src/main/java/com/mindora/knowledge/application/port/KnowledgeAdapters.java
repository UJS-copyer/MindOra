package com.mindora.knowledge.application.port;

import com.mindora.knowledge.domain.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeAdapters {
    private KnowledgeAdapters() {
    }

    public record RemoteFile(
            String path,
            String title,
            String content,
            String contentHash,
            Map<String, Object> metadata,
            List<String> imageReferences) {
    }

    public interface GiteeRepositoryPort {
        List<RemoteFile> listMarkdownFiles(DataSource source);
    }

    public interface EmbeddingProviderPort {
        String provider();

        String model();

        List<List<Double>> embed(List<String> contents);
    }

    public record RerankResult(int index, double score, String document) {
    }

    public interface RerankProviderPort {
        String provider();

        String model();

        List<RerankResult> rerank(String query, List<String> documents, int topN);
    }

    /**
     * 向量点：只承载检索与权限过滤必需的最小字段。
     *
     * <p>正文不进入向量库，需要展示正文时按 {@code chunkId} 回查 MySQL，避免把业务
     * 模型复制进向量库。
     *
     * <p>{@code categoryId} / {@code tagIds} 暂未纳入：知识模块不得直接查询博客表，
     * 待后续引入博客侧查询端口后再补充。
     */
    public record VectorRecord(
            UUID chunkId,
            UUID documentId,
            UUID versionId,
            int sequence,
            String headingPath,
            String blockType,
            String codeLanguage,
            String sourceType,
            String sourceId,
            String sourcePath,
            String visibility,
            String status,
            boolean knowledgeEnabled,
            String articleId,
            String articleSlug,
            String contentHash) {
    }

    /**
     * 向量检索命中项。{@code payload} 保留原始 payload，便于上层按需读取过滤字段。
     */
    public record VectorHit(
            UUID chunkId,
            UUID documentId,
            UUID versionId,
            double score,
            Map<String, Object> payload) {
    }

    public interface VectorStorePort {
        void upsert(List<VectorRecord> points, List<List<Double>> embeddings);

        void deleteByVersion(UUID documentVersionId);

        /**
         * 向量相似度检索。
         *
         * @param queryVector      查询向量
         * @param topN             返回条数上限
         * @param exactMatchFilter 字段精确匹配过滤（AND 语义），例如
         *                         {@code {"visibility":"public","status":"active"}}；
         *                         空表示不过滤。具体向量库的过滤语法由 adapter 内部翻译，
         *                         不泄漏到应用层。
         */
        List<VectorHit> search(List<Double> queryVector, int topN, Map<String, Object> exactMatchFilter);
    }

    public interface AssetReferencePort {
        void attachMarkdownReference(String sourcePath, String reference, String documentId);
    }

    public interface SyncTaskExecutorPort {
        void execute(long taskId);
    }

    public record PublishedArticle(UUID articleId, String slug) {
    }

    public interface DocumentPublicationPort {
        PublishedArticle publishMarkdownDocument(
                UUID documentId,
                String title,
                String sourcePath,
                String content,
                String indexStatus);
    }
}
