package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeChunk(
        UUID id,
        UUID documentVersionId,
        int sequence,
        String content,
        int characterCount,
        KnowledgeEnums.IndexStatus indexStatus,
        String embeddingModel,
        String vectorPointId,
        String failureReason,
        int retryCount,
        Instant createdAt) {
    public KnowledgeChunk indexed(String model, String pointId) {
        return new KnowledgeChunk(id, documentVersionId, sequence, content, characterCount,
                KnowledgeEnums.IndexStatus.INDEXED, model, pointId, null, retryCount, createdAt);
    }

    public KnowledgeChunk failed(String reason) {
        return new KnowledgeChunk(id, documentVersionId, sequence, content, characterCount,
                KnowledgeEnums.IndexStatus.FAILED, embeddingModel, vectorPointId, reason,
                retryCount + 1, createdAt);
    }
}
