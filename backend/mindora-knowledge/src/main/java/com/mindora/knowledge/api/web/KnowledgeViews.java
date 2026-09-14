package com.mindora.knowledge.api.web;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.SyncTask;
import java.time.Instant;

final class KnowledgeViews {
    private KnowledgeViews() {
    }

    static DataSourceView dataSource(DataSource source) {
        return new DataSourceView(
                PublicIds.toPublicId(source.id()), source.name(), source.sourceType().name().toLowerCase(),
                source.repositoryUrl(), source.branch(), source.accessToken() != null && !source.accessToken().isBlank(),
                source.rootPath(), source.defaultVisibility(), source.enabled(), source.createdAt(), source.updatedAt());
    }

    static SyncTaskView task(SyncTask task) {
        return new SyncTaskView(task.id(), PublicIds.toPublicId(task.sourceId()),
                task.mode().name().toLowerCase(), task.status().name().toLowerCase(), task.totalFiles(),
                task.processedFiles(), task.createdDocuments(), task.updatedDocuments(), task.archivedDocuments(),
                task.retryCount(), task.errorMessage(), task.traceId(), task.createdAt(), task.startedAt(),
                task.finishedAt());
    }

    static DocumentView document(KnowledgeDocument document) {
        return new DocumentView(PublicIds.toPublicId(document.id()), document.sourceType().name().toLowerCase(),
                document.sourceId(), document.sourcePath(), document.title(), compact(document.currentVersionId()),
                document.sourceHash(), document.visibility(), document.knowledgeEnabled(),
                document.publicArticleId(),
                document.status().name().toLowerCase(), document.parseStatus().name().toLowerCase(),
                document.indexStatus().name().toLowerCase(), document.indexFailureReason(),
                document.indexRetryCount(), document.createdAt(), document.updatedAt());
    }

    static VersionView version(KnowledgeDocumentVersion version) {
        return new VersionView(PublicIds.toPublicId(version.id()), PublicIds.toPublicId(version.documentId()),
                version.sourceType().name().toLowerCase(), version.sourcePath(), version.contentHash(),
                version.createdAt());
    }

    static ChunkView chunk(KnowledgeChunk chunk) {
        return new ChunkView(PublicIds.toPublicId(chunk.id()), PublicIds.toPublicId(chunk.documentVersionId()),
                chunk.sequence(), chunk.content(), chunk.characterCount(), chunk.indexStatus().name().toLowerCase(),
                chunk.embeddingModel(), chunk.vectorPointId(), chunk.failureReason(), chunk.retryCount(),
                chunk.createdAt());
    }

    static ChunkingConfigView chunkingConfig(ChunkingConfig config) {
        return new ChunkingConfigView(config.strategy().name().toLowerCase(), config.chunkSize(), config.overlap(),
                config.minimumSize(), config.maximumSize(), config.preserveHeadingHierarchy(),
                config.includeMetadata());
    }

    private static String compact(String uuid) {
        return uuid == null || uuid.isBlank() ? null : PublicIds.toPublicId(java.util.UUID.fromString(uuid));
    }

    record DataSourceView(
            String id,
            String name,
            String sourceType,
            String repositoryUrl,
            String branch,
            boolean tokenConfigured,
            String rootPath,
            String defaultVisibility,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
    }

    record SyncTaskView(
            long id,
            String sourceId,
            String mode,
            String status,
            int totalFiles,
            int processedFiles,
            int createdDocuments,
            int updatedDocuments,
            int archivedDocuments,
            int retryCount,
            String errorMessage,
            String traceId,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
    }

    record DocumentView(
            String id,
            String sourceType,
            String sourceId,
            String sourcePath,
            String title,
            String currentVersionId,
            String sourceHash,
            String visibility,
            boolean knowledgeEnabled,
            String publicArticleId,
            String status,
            String parseStatus,
            String indexStatus,
            String indexFailureReason,
            int indexRetryCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    record VersionView(String id, String documentId, String sourceType, String sourcePath, String contentHash,
            Instant createdAt) {
    }

    record ChunkView(
            String id,
            String documentVersionId,
            int sequence,
            String content,
            int characterCount,
            String indexStatus,
            String embeddingModel,
            String vectorPointId,
            String failureReason,
            int retryCount,
            Instant createdAt) {
    }

    record ChunkingConfigView(
            String strategy,
            int chunkSize,
            int overlap,
            int minimumSize,
            int maximumSize,
            boolean preserveHeadingHierarchy,
            boolean includeMetadata) {
    }
}
