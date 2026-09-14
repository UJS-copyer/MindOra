package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentVersion(
        UUID id,
        UUID documentId,
        KnowledgeEnums.SourceType sourceType,
        String sourceId,
        String sourcePath,
        String contentSnapshot,
        String metadataSnapshot,
        String contentHash,
        Instant createdAt) {
}
