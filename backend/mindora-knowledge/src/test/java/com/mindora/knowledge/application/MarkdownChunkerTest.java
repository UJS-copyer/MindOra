package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeEnums;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {
    @Test
    void chunksByMarkdownHeadingAndSize() {
        MarkdownChunker chunker = new MarkdownChunker();
        ChunkingConfig config = new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, 40, 5, 1, 60, true, true);

        assertThat(chunker.chunk("""
                # A
                alpha

                ## B
                beta
                """, config))
                .hasSize(2)
                .first()
                .asString()
                .contains("# A");
    }
}
