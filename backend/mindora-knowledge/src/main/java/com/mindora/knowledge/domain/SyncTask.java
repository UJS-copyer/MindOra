package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

public record SyncTask(
        long id,
        UUID sourceId,
        KnowledgeEnums.SyncMode mode,
        KnowledgeEnums.TaskStatus status,
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
    public SyncTask start(Instant at) {
        return with(KnowledgeEnums.TaskStatus.RUNNING, totalFiles, processedFiles, createdDocuments,
                updatedDocuments, archivedDocuments, retryCount, errorMessage, at, finishedAt);
    }

    public SyncTask success(
            int totalFiles,
            int processedFiles,
            int createdDocuments,
            int updatedDocuments,
            int archivedDocuments,
            Instant at) {
        return with(KnowledgeEnums.TaskStatus.SUCCESS, totalFiles, processedFiles, createdDocuments,
                updatedDocuments, archivedDocuments, retryCount, null, startedAt, at);
    }

    public SyncTask failed(String message, Instant at) {
        return with(KnowledgeEnums.TaskStatus.FAILED, totalFiles, processedFiles, createdDocuments,
                updatedDocuments, archivedDocuments, retryCount + 1, message, startedAt, at);
    }

    private SyncTask with(
            KnowledgeEnums.TaskStatus nextStatus,
            int nextTotal,
            int nextProcessed,
            int nextCreated,
            int nextUpdated,
            int nextArchived,
            int nextRetries,
            String nextError,
            Instant nextStarted,
            Instant nextFinished) {
        return new SyncTask(
                id,
                sourceId,
                mode,
                nextStatus,
                nextTotal,
                nextProcessed,
                nextCreated,
                nextUpdated,
                nextArchived,
                nextRetries,
                nextError,
                traceId,
                createdAt,
                nextStarted,
                nextFinished);
    }
}
