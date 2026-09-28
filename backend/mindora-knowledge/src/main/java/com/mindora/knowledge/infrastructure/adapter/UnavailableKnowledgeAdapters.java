package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UnavailableKnowledgeAdapters {
    private UnavailableKnowledgeAdapters() {
    }

    public static class Gitee implements KnowledgeAdapters.GiteeRepositoryPort {
        @Override
        public List<KnowledgeAdapters.RemoteFile> listMarkdownFiles(DataSource source) {
            throw new BusinessException(
                    "gitee_adapter_unavailable",
                    "Gitee adapter is not configured in this runtime");
        }
    }

    public static class Embedding implements KnowledgeAdapters.EmbeddingProviderPort {
        @Override
        public String provider() {
            return "bailian";
        }

        @Override
        public String model() {
            return "text-embedding-v4";
        }

        @Override
        public List<List<Double>> embed(List<String> contents) {
            throw new BusinessException(
                    "embedding_adapter_unavailable",
                    "Embedding provider is not configured in this runtime");
        }
    }

    public static class Rerank implements KnowledgeAdapters.RerankProviderPort {
        @Override
        public String provider() {
            return "siliconflow";
        }

        @Override
        public String model() {
            return "BAAI/bge-reranker-v2-m3";
        }

        @Override
        public List<KnowledgeAdapters.RerankResult> rerank(String query, List<String> documents, int topN) {
            throw new BusinessException(
                    "rerank_adapter_unavailable",
                    "Rerank provider is not configured in this runtime");
        }
    }

    public static class VectorStore implements KnowledgeAdapters.VectorStorePort {
        @Override
        public void upsert(List<KnowledgeAdapters.VectorRecord> points, List<List<Double>> embeddings) {
            throw new BusinessException(
                    "vector_adapter_unavailable",
                    "Vector store is not configured in this runtime");
        }

        @Override
        public void deleteByVersion(UUID documentVersionId) {
        }

        @Override
        public void updatePayload(UUID documentVersionId, Map<String, Object> payload) {
            // 向量库未配置时无需回写：此时向量检索本身不可用，不存在残留旧权限字段的问题
        }

        @Override
        public List<KnowledgeAdapters.VectorHit> search(
                List<Double> queryVector, int topN, Map<String, Object> exactMatchFilter) {
            throw new BusinessException(
                    "vector_adapter_unavailable",
                    "Vector store is not configured in this runtime");
        }
    }

    /**
     * 关键词索引未配置时的占位实现。
     *
     * <p>写入一律跳过：关键词索引是可选派生索引，其缺席不应让切片、向量索引或文章发布流程失败；
     * 检索则抛错，由检索编排层按 design-04 的降级策略退化为纯向量检索。
     */
    public static class KeywordIndex implements KnowledgeAdapters.KeywordIndexPort {
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
            throw new BusinessException(
                    "keyword_adapter_unavailable",
                    "Keyword index is not configured in this runtime");
        }
    }

    public static class AssetReference implements KnowledgeAdapters.AssetReferencePort {
        @Override
        public void attachMarkdownReference(String sourcePath, String reference, String documentId) {
        }
    }
}
