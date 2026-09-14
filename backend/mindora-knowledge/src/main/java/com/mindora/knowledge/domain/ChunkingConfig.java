package com.mindora.knowledge.domain;

public record ChunkingConfig(
        KnowledgeEnums.ChunkStrategy strategy,
        int chunkSize,
        int overlap,
        int minimumSize,
        int maximumSize,
        boolean preserveHeadingHierarchy,
        boolean includeMetadata) {
    public ChunkingConfig {
        strategy = strategy == null ? KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING : strategy;
        chunkSize = Math.max(100, chunkSize <= 0 ? 800 : chunkSize);
        overlap = Math.max(0, Math.min(overlap, chunkSize / 2));
        minimumSize = Math.max(1, minimumSize <= 0 ? 40 : minimumSize);
        maximumSize = Math.max(chunkSize, maximumSize <= 0 ? chunkSize * 2 : maximumSize);
    }

    public static ChunkingConfig defaults() {
        return new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, 800, 120, 40, 1600, true, true);
    }
}
