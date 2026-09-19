package com.mindora.knowledge.domain;

public final class KnowledgeEnums {
    private KnowledgeEnums() {
    }

    public enum SourceType {
        GITEE,
        BLOG
    }

    public enum SyncMode {
        INCREMENTAL,
        FULL
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELED
    }

    public enum DocumentStatus {
        ACTIVE,
        ARCHIVED,
        DELETED
    }

    public enum IndexStatus {
        PENDING,
        CHUNKED,
        VECTORIZING,
        INDEXED,
        FAILED
    }

    public enum ParseStatus {
        PENDING,
        PARSED,
        FAILED
    }

    public enum ChunkStrategy {
        MARKDOWN_HEADING,
        FIXED_SIZE
    }

    /**
     * 切片的结构类型。用于检索时的精准引用与后续按块类型调优。
     */
    public enum BlockType {
        PARAGRAPH,
        CODE,
        TABLE,
        LIST,
        QUOTE
    }
}
