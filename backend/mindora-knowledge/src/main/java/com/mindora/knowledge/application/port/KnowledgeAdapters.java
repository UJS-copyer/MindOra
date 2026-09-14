package com.mindora.knowledge.application.port;

import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
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

    public interface VectorStorePort {
        void upsert(UUID documentId, List<KnowledgeChunk> chunks, List<List<Double>> embeddings);

        void deleteByVersion(UUID documentVersionId);
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
