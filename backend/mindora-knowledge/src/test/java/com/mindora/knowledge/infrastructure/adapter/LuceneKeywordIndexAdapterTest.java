package com.mindora.knowledge.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceneKeywordIndexAdapterTest {

    @TempDir
    Path tempDir;

    private final UUID documentId = UUID.randomUUID();
    private LuceneKeywordIndexAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LuceneKeywordIndexAdapter(tempDir.resolve("keyword"));
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) {
            adapter.close();
        }
    }

    @Test
    void findsChineseContentWithoutDictionary() {
        UUID relevant = UUID.randomUUID();
        adapter.replaceDocument(documentId, List.of(
                chunk(relevant, "向量数据库实践", "存储 > 索引", "本文介绍向量数据库的选型与调优经验", "private"),
                chunk(UUID.randomUUID(), "烘焙笔记", "厨房 > 面包", "无关内容：烘焙面包的温度控制", "private")));

        List<KnowledgeAdapters.KeywordHit> hits = adapter.search("向量数据库", 10, Map.of());

        assertThat(hits).extracting(KnowledgeAdapters.KeywordHit::chunkId).containsExactly(relevant);
    }

    @Test
    void splitsCamelCaseAndSnakeCaseIdentifiers() {
        UUID relevant = UUID.randomUUID();
        adapter.replaceDocument(documentId, List.of(
                chunk(relevant, "字段 headingPath 与 chunk_id 的写入规则说明", "private"),
                chunk(UUID.randomUUID(), "完全无关的段落", "private")));

        assertThat(adapter.search("headingPath", 10, Map.of()))
                .extracting(KnowledgeAdapters.KeywordHit::chunkId)
                .contains(relevant);
        assertThat(adapter.search("chunk_id", 10, Map.of()))
                .extracting(KnowledgeAdapters.KeywordHit::chunkId)
                .contains(relevant);
    }

    @Test
    void replaceDocumentIsAtomicAndRemovesStaleChunks() {
        UUID firstVersion = UUID.randomUUID();
        adapter.replaceDocument(documentId, List.of(chunk(firstVersion, "第一版内容说明", "private")));
        assertThat(adapter.chunkCount()).isEqualTo(1);

        UUID secondVersion = UUID.randomUUID();
        adapter.replaceDocument(documentId, List.of(
                chunk(secondVersion, "第二版内容说明", "private"),
                chunk(UUID.randomUUID(), "第二版补充内容", "private")));

        assertThat(adapter.chunkCount()).isEqualTo(2);
        assertThat(adapter.search("第一版内容说明", 10, Map.of()))
                .extracting(KnowledgeAdapters.KeywordHit::chunkId)
                .doesNotContain(firstVersion);
    }

    @Test
    void appliesPermissionFilterConsistentlyWithVectorSide() {
        UUID publicChunk = UUID.randomUUID();
        UUID privateChunk = UUID.randomUUID();
        adapter.replaceDocument(documentId, List.of(
                chunk(publicChunk, "公开的向量检索说明", "public"),
                chunk(privateChunk, "私有的向量检索说明", "private")));

        List<KnowledgeAdapters.KeywordHit> hits = adapter.search(
                "向量检索", 10, Map.of("visibility", "public", "status", "active", "knowledgeEnabled", true));

        assertThat(hits).extracting(KnowledgeAdapters.KeywordHit::chunkId).containsExactly(publicChunk);
    }

    @Test
    void deletesDocumentAndClearsWholeIndex() {
        adapter.replaceDocument(documentId, List.of(chunk(UUID.randomUUID(), "待删除内容", "private")));
        adapter.deleteDocument(documentId);
        assertThat(adapter.chunkCount()).isZero();

        adapter.replaceDocument(documentId, List.of(chunk(UUID.randomUUID(), "重建内容", "private")));
        adapter.deleteAll();
        assertThat(adapter.chunkCount()).isZero();
    }

    @Test
    void returnsEmptyResultForQueryWithoutUsableTokens() {
        adapter.replaceDocument(documentId, List.of(chunk(UUID.randomUUID(), "任意内容", "private")));

        assertThat(adapter.search("   ", 10, Map.of())).isEmpty();
        assertThat(adapter.search(null, 10, Map.of())).isEmpty();
    }

    private KnowledgeAdapters.KeywordDocument chunk(
            UUID chunkId, String title, String headingPath, String content, String visibility) {
        return new KnowledgeAdapters.KeywordDocument(
                chunkId, documentId, title, headingPath, "paragraph", null,
                content, visibility, "active", true);
    }

    private KnowledgeAdapters.KeywordDocument chunk(UUID chunkId, String content, String visibility) {
        return chunk(chunkId, "通用标题", "通用 > 章节", content, visibility);
    }
}
