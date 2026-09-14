package com.mindora.knowledge.application.port;

import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.SyncTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class KnowledgeRepositories {
    private KnowledgeRepositories() {
    }

    public interface DataSourceRepository {
        DataSource save(DataSource source);

        Optional<DataSource> find(UUID id);

        List<DataSource> list();
    }

    public interface KnowledgeRepository {
        KnowledgeDocument saveDocument(KnowledgeDocument document);

        Optional<KnowledgeDocument> findDocument(UUID id);

        Optional<KnowledgeDocument> findDocumentBySource(String sourceType, String sourceId, String sourcePath);

        List<KnowledgeDocument> listDocuments();

        KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version);

        Optional<KnowledgeDocumentVersion> findVersion(UUID id);

        List<KnowledgeDocumentVersion> listVersions(UUID documentId);

        KnowledgeChunk saveChunk(KnowledgeChunk chunk);

        List<KnowledgeChunk> listChunks(UUID versionId);

        void archiveChunks(UUID versionId);

        SyncTask saveTask(SyncTask task);

        Optional<SyncTask> findTask(long id);

        List<SyncTask> listTasks();

        long nextTaskId();

        ChunkingConfig getChunkingConfig();

        void saveChunkingConfig(ChunkingConfig config);
    }
}
