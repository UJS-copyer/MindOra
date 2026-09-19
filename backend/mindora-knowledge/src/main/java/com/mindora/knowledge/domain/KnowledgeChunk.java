package com.mindora.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识切片。
 *
 * <p>除正文与索引状态外，还保留切片的结构化元数据（标题路径、块类型、代码语言、
 * 内容哈希）。这些字段用于检索 payload 组装、来源引用与章节定位。
 */
public record KnowledgeChunk(
        UUID id,
        UUID documentVersionId,
        int sequence,
        String content,
        int characterCount,
        String headingPath,
        int headingLevel,
        KnowledgeEnums.BlockType blockType,
        String codeLanguage,
        String contentHash,
        KnowledgeEnums.IndexStatus indexStatus,
        String embeddingModel,
        String vectorPointId,
        String failureReason,
        int retryCount,
        Instant createdAt) {

    public KnowledgeChunk {
        blockType = blockType == null ? KnowledgeEnums.BlockType.PARAGRAPH : blockType;
    }

    public KnowledgeChunk indexed(String model, String pointId) {
        return new KnowledgeChunk(id, documentVersionId, sequence, content, characterCount,
                headingPath, headingLevel, blockType, codeLanguage, contentHash,
                KnowledgeEnums.IndexStatus.INDEXED, model, pointId, null, retryCount, createdAt);
    }

    public KnowledgeChunk failed(String reason) {
        return new KnowledgeChunk(id, documentVersionId, sequence, content, characterCount,
                headingPath, headingLevel, blockType, codeLanguage, contentHash,
                KnowledgeEnums.IndexStatus.FAILED, embeddingModel, vectorPointId, reason,
                retryCount + 1, createdAt);
    }
}
