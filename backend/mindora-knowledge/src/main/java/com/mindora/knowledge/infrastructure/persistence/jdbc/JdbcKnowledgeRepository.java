package com.mindora.knowledge.infrastructure.persistence.jdbc;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.port.KnowledgeRepositories;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.domain.SyncTask;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcKnowledgeRepository implements KnowledgeRepositories.KnowledgeRepository {
    private static final String CHUNK_CONFIG_KEY = "knowledge.chunking";

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeDocument saveDocument(KnowledgeDocument document) {
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_document (
                    id, source_type, source_id, source_path, title, current_version_id,
                    source_hash, visibility, knowledge_enabled, status, parse_status,
                    public_article_id, index_status, index_failure_reason, index_retry_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    source_id = VALUES(source_id),
                    source_path = VALUES(source_path),
                    title = VALUES(title),
                    current_version_id = VALUES(current_version_id),
                    source_hash = VALUES(source_hash),
                    visibility = VALUES(visibility),
                    knowledge_enabled = VALUES(knowledge_enabled),
                    status = VALUES(status),
                    parse_status = VALUES(parse_status),
                    public_article_id = VALUES(public_article_id),
                    index_status = VALUES(index_status),
                    index_failure_reason = VALUES(index_failure_reason),
                    index_retry_count = VALUES(index_retry_count),
                    updated_at = VALUES(updated_at)
                """,
                PublicIds.toPublicId(document.id()),
                document.sourceType().name().toLowerCase(),
                document.sourceId(),
                document.sourcePath(),
                document.title(),
                compactUuid(document.currentVersionId()),
                document.sourceHash(),
                document.visibility(),
                document.knowledgeEnabled(),
                document.status().name().toLowerCase(),
                document.parseStatus().name().toLowerCase(),
                compactUuid(document.publicArticleId()),
                document.indexStatus().name().toLowerCase(),
                document.indexFailureReason(),
                document.indexRetryCount(),
                timestamp(document.createdAt()),
                timestamp(document.updatedAt()));
        return document;
    }

    @Override
    public Optional<KnowledgeDocument> findDocument(UUID id) {
        return queryDocuments("WHERE id = ?", PublicIds.toPublicId(id)).stream().findFirst();
    }

    @Override
    public Optional<KnowledgeDocument> findDocumentBySource(String sourceType, String sourceId, String sourcePath) {
        return queryDocuments("WHERE source_type = ? AND source_id = ? AND source_path = ?",
                sourceType.toLowerCase(), sourceId, sourcePath).stream().findFirst();
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        return queryDocuments("ORDER BY updated_at DESC");
    }

    @Override
    public KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version) {
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_document_version (
                    id, document_id, source_type, source_id, source_path, content_snapshot,
                    metadata_snapshot, content_hash, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                PublicIds.toPublicId(version.id()),
                PublicIds.toPublicId(version.documentId()),
                version.sourceType().name().toLowerCase(),
                version.sourceId(),
                version.sourcePath(),
                version.contentSnapshot(),
                version.metadataSnapshot(),
                version.contentHash(),
                timestamp(version.createdAt()));
        return version;
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findVersion(UUID id) {
        return jdbcTemplate.query("SELECT * FROM knowledge_document_version WHERE id = ?",
                        (rs, rowNum) -> mapVersion(rs), PublicIds.toPublicId(id))
                .stream()
                .findFirst();
    }

    @Override
    public List<KnowledgeDocumentVersion> listVersions(UUID documentId) {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_document_version WHERE document_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> mapVersion(rs),
                PublicIds.toPublicId(documentId));
    }

    @Override
    public KnowledgeChunk saveChunk(KnowledgeChunk chunk) {
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_chunk (
                    id, document_version_id, sequence_no, content, character_count,
                    index_status, embedding_model, vector_point_id, failure_reason,
                    retry_count, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    index_status = VALUES(index_status),
                    embedding_model = VALUES(embedding_model),
                    vector_point_id = VALUES(vector_point_id),
                    failure_reason = VALUES(failure_reason),
                    retry_count = VALUES(retry_count)
                """,
                PublicIds.toPublicId(chunk.id()),
                PublicIds.toPublicId(chunk.documentVersionId()),
                chunk.sequence(),
                chunk.content(),
                chunk.characterCount(),
                chunk.indexStatus().name().toLowerCase(),
                chunk.embeddingModel(),
                chunk.vectorPointId(),
                chunk.failureReason(),
                chunk.retryCount(),
                timestamp(chunk.createdAt()));
        return chunk;
    }

    @Override
    public List<KnowledgeChunk> listChunks(UUID versionId) {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_chunk WHERE document_version_id = ? AND (failure_reason IS NULL OR failure_reason <> 'archived') ORDER BY sequence_no",
                (rs, rowNum) -> mapChunk(rs),
                PublicIds.toPublicId(versionId));
    }

    @Override
    public void archiveChunks(UUID versionId) {
        jdbcTemplate.update(
                "UPDATE knowledge_chunk SET index_status = 'pending', failure_reason = 'archived' WHERE document_version_id = ?",
                PublicIds.toPublicId(versionId));
    }

    @Override
    public SyncTask saveTask(SyncTask task) {
        if (task.id() <= 0) {
            throw new IllegalArgumentException("Task id must be allocated before save");
        }
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_sync_task (
                    id, source_id, sync_mode, status, total_files, processed_files,
                    created_documents, updated_documents, archived_documents, retry_count,
                    error_message, trace_id, created_at, started_at, finished_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    total_files = VALUES(total_files),
                    processed_files = VALUES(processed_files),
                    created_documents = VALUES(created_documents),
                    updated_documents = VALUES(updated_documents),
                    archived_documents = VALUES(archived_documents),
                    retry_count = VALUES(retry_count),
                    error_message = VALUES(error_message),
                    started_at = VALUES(started_at),
                    finished_at = VALUES(finished_at)
                """,
                task.id(),
                PublicIds.toPublicId(task.sourceId()),
                task.mode().name().toLowerCase(),
                task.status().name().toLowerCase(),
                task.totalFiles(),
                task.processedFiles(),
                task.createdDocuments(),
                task.updatedDocuments(),
                task.archivedDocuments(),
                task.retryCount(),
                task.errorMessage(),
                task.traceId(),
                timestamp(task.createdAt()),
                timestamp(task.startedAt()),
                timestamp(task.finishedAt()));
        return task;
    }

    @Override
    public Optional<SyncTask> findTask(long id) {
        return jdbcTemplate.query("SELECT * FROM knowledge_sync_task WHERE id = ?",
                        (rs, rowNum) -> mapTask(rs), id)
                .stream()
                .findFirst();
    }

    @Override
    public List<SyncTask> listTasks() {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_sync_task ORDER BY created_at DESC LIMIT 200",
                (rs, rowNum) -> mapTask(rs));
    }

    @Override
    public long nextTaskId() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM knowledge_sync_task", Long.class);
    }

    @Override
    public ChunkingConfig getChunkingConfig() {
        return jdbcTemplate.query(
                        "SELECT config_value FROM knowledge_config WHERE config_key = ?",
                        (rs, rowNum) -> parseConfig(rs.getString("config_value")),
                        CHUNK_CONFIG_KEY)
                .stream()
                .findFirst()
                .orElse(ChunkingConfig.defaults());
    }

    @Override
    public void saveChunkingConfig(ChunkingConfig config) {
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_config (config_key, config_value, updated_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = VALUES(updated_at)
                """,
                CHUNK_CONFIG_KEY,
                config.strategy().name() + "," + config.chunkSize() + "," + config.overlap() + ","
                        + config.minimumSize() + "," + config.maximumSize() + ","
                        + config.preserveHeadingHierarchy() + "," + config.includeMetadata(),
                timestamp(Instant.now()));
    }

    private List<KnowledgeDocument> queryDocuments(String clause, Object... args) {
        return jdbcTemplate.query("SELECT * FROM knowledge_document " + clause, (rs, rowNum) -> mapDocument(rs), args);
    }

    private KnowledgeDocument mapDocument(ResultSet rs) throws SQLException {
        return new KnowledgeDocument(
                PublicIds.toUuid(rs.getString("id")),
                KnowledgeEnums.SourceType.valueOf(rs.getString("source_type").toUpperCase()),
                rs.getString("source_id"),
                rs.getString("source_path"),
                rs.getString("title"),
                dashedUuid(rs.getString("current_version_id")),
                rs.getString("source_hash"),
                rs.getString("visibility"),
                rs.getBoolean("knowledge_enabled"),
                dashedUuid(rs.getString("public_article_id")),
                KnowledgeEnums.DocumentStatus.valueOf(rs.getString("status").toUpperCase()),
                KnowledgeEnums.ParseStatus.valueOf(rs.getString("parse_status").toUpperCase()),
                KnowledgeEnums.IndexStatus.valueOf(rs.getString("index_status").toUpperCase()),
                rs.getString("index_failure_reason"),
                rs.getInt("index_retry_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private KnowledgeDocumentVersion mapVersion(ResultSet rs) throws SQLException {
        return new KnowledgeDocumentVersion(
                PublicIds.toUuid(rs.getString("id")),
                PublicIds.toUuid(rs.getString("document_id")),
                KnowledgeEnums.SourceType.valueOf(rs.getString("source_type").toUpperCase()),
                rs.getString("source_id"),
                rs.getString("source_path"),
                rs.getString("content_snapshot"),
                rs.getString("metadata_snapshot"),
                rs.getString("content_hash"),
                instant(rs.getTimestamp("created_at")));
    }

    private KnowledgeChunk mapChunk(ResultSet rs) throws SQLException {
        return new KnowledgeChunk(
                PublicIds.toUuid(rs.getString("id")),
                PublicIds.toUuid(rs.getString("document_version_id")),
                rs.getInt("sequence_no"),
                rs.getString("content"),
                rs.getInt("character_count"),
                KnowledgeEnums.IndexStatus.valueOf(rs.getString("index_status").toUpperCase()),
                rs.getString("embedding_model"),
                rs.getString("vector_point_id"),
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                instant(rs.getTimestamp("created_at")));
    }

    private SyncTask mapTask(ResultSet rs) throws SQLException {
        return new SyncTask(
                rs.getLong("id"),
                PublicIds.toUuid(rs.getString("source_id")),
                KnowledgeEnums.SyncMode.valueOf(rs.getString("sync_mode").toUpperCase()),
                KnowledgeEnums.TaskStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getInt("total_files"),
                rs.getInt("processed_files"),
                rs.getInt("created_documents"),
                rs.getInt("updated_documents"),
                rs.getInt("archived_documents"),
                rs.getInt("retry_count"),
                rs.getString("error_message"),
                rs.getString("trace_id"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("finished_at")));
    }

    private ChunkingConfig parseConfig(String value) {
        String[] parts = value.split(",");
        return new ChunkingConfig(KnowledgeEnums.ChunkStrategy.valueOf(parts[0]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]), Boolean.parseBoolean(parts[5]), Boolean.parseBoolean(parts[6]));
    }

    private String compactUuid(String id) {
        return id == null || id.isBlank() ? null : PublicIds.toPublicId(PublicIds.toUuid(id));
    }

    private String dashedUuid(String id) {
        return id == null || id.isBlank() ? null : PublicIds.toUuid(id).toString();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
