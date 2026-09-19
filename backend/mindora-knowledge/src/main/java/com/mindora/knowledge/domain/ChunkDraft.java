package com.mindora.knowledge.domain;

/**
 * 切片草稿：切片过程的中间产物，携带结构化元数据。
 *
 * <p>元数据在进入向量库前会被用于组装检索 payload，用于权限过滤、来源引用与
 * 章节定位。{@code content} 只保存原始切片文本，送模型的文本由
 * {@link #embeddingText()} 组装。
 *
 * @param sequence      切片在版本文档内的顺序号
 * @param content       原始切片文本
 * @param headingPath   完整标题层级路径，例如 {@code 后端技术 > Spring Boot > AQS}；无标题时为 null
 * @param headingLevel  最近标题层级（1-6）；前言区为 0
 * @param blockType     结构类型
 * @param codeLanguage  代码块语言；非代码块为 null
 */
public record ChunkDraft(
        int sequence,
        String content,
        String headingPath,
        int headingLevel,
        KnowledgeEnums.BlockType blockType,
        String codeLanguage) {

    public ChunkDraft {
        content = content == null ? "" : content;
        blockType = blockType == null ? KnowledgeEnums.BlockType.PARAGRAPH : blockType;
        headingLevel = Math.max(0, Math.min(headingLevel, 6));
    }

    /**
     * 送 Embedding 的文本：带标题路径前缀以提升召回质量。
     *
     * <p>当 {@code preserveHeadingHierarchy=false} 时 {@code headingPath} 为空，
     * 此方法退化为返回原始正文。
     */
    public String embeddingText() {
        if (headingPath == null || headingPath.isBlank()) {
            return content;
        }
        return headingPath + "\n" + content;
    }
}
