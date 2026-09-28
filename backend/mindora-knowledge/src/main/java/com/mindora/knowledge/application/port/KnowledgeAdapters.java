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
         * 按版本更新已有向量点的 payload 字段，不重新计算向量。
         *
         * <p>用于文档可见性或知识库开关变更后的同步：payload 中的 visibility / knowledgeEnabled
         * 是索引时写入的，若不回写就会残留旧值，导致权限隔离失效。
         */
        void updatePayload(UUID documentVersionId, Map<String, Object> payload);

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

    /**
     * 关键词索引中的一个切片。
     *
     * <p>权限字段（visibility / status / knowledgeEnabled）与向量 payload 保持一致，
     * 确保两路召回使用同一套隔离规则，融合时不会泄漏私有内容。
     */
    public record KeywordDocument(
            UUID chunkId,
            UUID documentId,
            String title,
            String headingPath,
            String blockType,
            String codeLanguage,
            String content,
            String visibility,
            String status,
            boolean knowledgeEnabled) {
    }

    /** 关键词检索命中项；{@code score} 为 BM25 相关度分数。 */
    public record KeywordHit(UUID chunkId, UUID documentId, double score) {
    }

    public interface KeywordIndexPort {
        /**
         * 以文档为单位原子替换其全部切片。
         *
         * <p>必须原子：重新切片时若先删后写之间存在窗口，或删除条件不精确，都会产生孤儿切片。
         */
        void replaceDocument(UUID documentId, List<KeywordDocument> documents);

        void deleteDocument(UUID documentId);

        /** 清空整个索引，用于全量重建。 */
        void deleteAll();

        /**
         * 关键词检索。
         *
         * @param exactMatchFilter 与 {@link VectorStorePort#search} 同语义的字段精确匹配过滤（AND）
         */
        List<KeywordHit> search(String query, int topN, Map<String, Object> exactMatchFilter);
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
