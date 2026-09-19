package com.mindora.knowledge.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QdrantVectorStoreAdapterPayloadTest {

    @Test
    void buildsMinimalPayloadWithPermissionFields() {
        QdrantVectorStoreAdapter adapter =
                new QdrantVectorStoreAdapter("http://localhost:6333", "", "test_collection");
        UUID chunkId = UUID.randomUUID();
        KnowledgeAdapters.VectorRecord record = new KnowledgeAdapters.VectorRecord(
                chunkId, UUID.randomUUID(), UUID.randomUUID(), 2,
                "后端技术 > 并发", "code", "java", "gitee", "source-1", "docs/a.md",
                "public", "active", true, "42", "aqs-notes", "hash-1");

        Map<String, Object> payload = adapter.payload(record);

        assertThat(payload).containsEntry("chunkId", chunkId.toString());
        assertThat(payload).containsEntry("sequence", 2);
        assertThat(payload).containsEntry("visibility", "public");
        assertThat(payload).containsEntry("status", "active");
        assertThat(payload).containsEntry("knowledgeEnabled", true);
        assertThat(payload).containsEntry("sourceType", "gitee");
        assertThat(payload).containsEntry("sourcePath", "docs/a.md");
        assertThat(payload).containsEntry("headingPath", "后端技术 > 并发");
        assertThat(payload).containsEntry("blockType", "code");
        assertThat(payload).containsEntry("codeLanguage", "java");
        assertThat(payload).containsEntry("articleId", "42");
        assertThat(payload).containsEntry("articleSlug", "aqs-notes");
        assertThat(payload).containsEntry("contentHash", "hash-1");
        // 正文不进入向量库，需要正文时按 chunkId 回查 MySQL
        assertThat(payload).doesNotContainKey("content");
    }

    @Test
    void omitsBlankOptionalFields() {
        QdrantVectorStoreAdapter adapter =
                new QdrantVectorStoreAdapter("http://localhost:6333", "", "test_collection");
        KnowledgeAdapters.VectorRecord record = new KnowledgeAdapters.VectorRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0,
                null, "paragraph", null, "blog", "1", "blog:slug",
                "private", "active", false, null, null, null);

        Map<String, Object> payload = adapter.payload(record);

        assertThat(payload).doesNotContainKeys(
                "headingPath", "codeLanguage", "articleId", "articleSlug", "contentHash");
        assertThat(payload).containsEntry("knowledgeEnabled", false);
        assertThat(payload).containsEntry("sourcePath", "blog:slug");
        assertThat(payload).containsEntry("blockType", "paragraph");
    }
}
