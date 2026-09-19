package com.mindora.knowledge.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeViewsTest {

    @Test
    void exposesChunkStructureMetadata() {
        KnowledgeChunk chunk = new KnowledgeChunk(
                UUID.randomUUID(), UUID.randomUUID(), 3, "正文片段", 4,
                "后端技术 > 并发", 2, KnowledgeEnums.BlockType.CODE, "java", "hash-value",
                KnowledgeEnums.IndexStatus.INDEXED, "text-embedding-v4", "point-1", null, 0, Instant.now());

        KnowledgeViews.ChunkView view = KnowledgeViews.chunk(chunk);

        assertThat(view.sequence()).isEqualTo(3);
        assertThat(view.characterCount()).isEqualTo(4);
        assertThat(view.headingPath()).isEqualTo("后端技术 > 并发");
        assertThat(view.headingLevel()).isEqualTo(2);
        assertThat(view.blockType()).isEqualTo("code");
        assertThat(view.codeLanguage()).isEqualTo("java");
        assertThat(view.indexStatus()).isEqualTo("indexed");
        assertThat(view.vectorPointId()).isEqualTo("point-1");
    }
}
