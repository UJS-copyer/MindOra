package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
import java.util.List;
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
        public void upsert(UUID documentId, List<KnowledgeChunk> chunks, List<List<Double>> embeddings) {
            throw new BusinessException(
                    "vector_adapter_unavailable",
                    "Vector store is not configured in this runtime");
        }

        @Override
        public void deleteByVersion(UUID documentVersionId) {
        }
    }

    public static class AssetReference implements KnowledgeAdapters.AssetReferencePort {
        @Override
        public void attachMarkdownReference(String sourcePath, String reference, String documentId) {
        }
    }
}
