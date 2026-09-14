package com.mindora.knowledge.infrastructure.persistence.memory;

import com.mindora.knowledge.application.port.KnowledgeRepositories;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.domain.SyncTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryKnowledgeRepositories {
    private InMemoryKnowledgeRepositories() {
    }

    public static class DataSources implements KnowledgeRepositories.DataSourceRepository {
        private final Map<UUID, DataSource> values = new LinkedHashMap<>();

        @Override
        public synchronized DataSource save(DataSource source) {
            values.put(source.id(), source);
            return source;
        }

        @Override
        public synchronized Optional<DataSource> find(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public synchronized List<DataSource> list() {
            return new ArrayList<>(values.values());
        }
    }

    public static class Knowledge implements KnowledgeRepositories.KnowledgeRepository {
        private final Map<UUID, KnowledgeDocument> documents = new LinkedHashMap<>();
        private final Map<UUID, KnowledgeDocumentVersion> versions = new LinkedHashMap<>();
        private final Map<UUID, KnowledgeChunk> chunks = new LinkedHashMap<>();
        private final Map<Long, SyncTask> tasks = new LinkedHashMap<>();
        private final AtomicLong taskId = new AtomicLong(1);
        private ChunkingConfig chunkingConfig = ChunkingConfig.defaults();

        @Override
        public synchronized KnowledgeDocument saveDocument(KnowledgeDocument document) {
            documents.put(document.id(), document);
            return document;
        }

        @Override
        public synchronized Optional<KnowledgeDocument> findDocument(UUID id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public synchronized Optional<KnowledgeDocument> findDocumentBySource(
                String sourceType, String sourceId, String sourcePath) {
            return documents.values().stream()
                    .filter(value -> value.sourceType().name().equalsIgnoreCase(sourceType))
                    .filter(value -> value.sourceId().equals(sourceId))
                    .filter(value -> value.sourcePath().equals(sourcePath))
                    .findFirst();
        }

        @Override
        public synchronized List<KnowledgeDocument> listDocuments() {
            return new ArrayList<>(documents.values());
        }

        @Override
        public synchronized KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version) {
            versions.put(version.id(), version);
            return version;
        }

        @Override
        public synchronized Optional<KnowledgeDocumentVersion> findVersion(UUID id) {
            return Optional.ofNullable(versions.get(id));
        }

        @Override
        public synchronized List<KnowledgeDocumentVersion> listVersions(UUID documentId) {
            return versions.values().stream()
                    .filter(value -> value.documentId().equals(documentId))
                    .toList();
        }

        @Override
        public synchronized KnowledgeChunk saveChunk(KnowledgeChunk chunk) {
            chunks.put(chunk.id(), chunk);
            return chunk;
        }

        @Override
        public synchronized List<KnowledgeChunk> listChunks(UUID versionId) {
            return chunks.values().stream()
                    .filter(value -> value.documentVersionId().equals(versionId))
                    .filter(value -> !"archived".equals(value.failureReason()))
                    .sorted(java.util.Comparator.comparingInt(KnowledgeChunk::sequence))
                    .toList();
        }

        @Override
        public synchronized void archiveChunks(UUID versionId) {
            chunks.replaceAll((id, value) -> value.documentVersionId().equals(versionId)
                    ? new KnowledgeChunk(value.id(), value.documentVersionId(), value.sequence(),
                    value.content(), value.characterCount(), KnowledgeEnums.IndexStatus.PENDING,
                    value.embeddingModel(), value.vectorPointId(), "archived", value.retryCount(),
                    value.createdAt())
                    : value);
        }

        @Override
        public synchronized SyncTask saveTask(SyncTask task) {
            tasks.put(task.id(), task);
            return task;
        }

        @Override
        public synchronized Optional<SyncTask> findTask(long id) {
            return Optional.ofNullable(tasks.get(id));
        }

        @Override
        public synchronized List<SyncTask> listTasks() {
            return new ArrayList<>(tasks.values());
        }

        @Override
        public long nextTaskId() {
            return taskId.getAndIncrement();
        }

        @Override
        public synchronized ChunkingConfig getChunkingConfig() {
            return chunkingConfig;
        }

        @Override
        public synchronized void saveChunkingConfig(ChunkingConfig config) {
            chunkingConfig = config;
        }
    }
}
