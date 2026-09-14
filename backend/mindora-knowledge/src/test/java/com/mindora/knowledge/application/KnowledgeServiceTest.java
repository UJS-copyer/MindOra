package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.application.port.KnowledgeRepositories;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.domain.SyncTask;
import com.mindora.knowledge.infrastructure.persistence.memory.InMemoryKnowledgeRepositories;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {
    @Test
    void syncsMarkdownIntoVersionChunksAndIndexStatus() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/a.md", null, "---\ntitle: A\n---\n# A\nhello ![](a.png)",
                "hash-a", Map.of("sha", "hash-a"), List.of())));

        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);
        SyncTask task = fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");

        assertThat(fixture.service.getTask(task.id()).status()).isEqualTo(KnowledgeEnums.TaskStatus.SUCCESS);
        assertThat(fixture.service.listDocuments()).hasSize(1);
        var document = fixture.service.listDocuments().getFirst();
        assertThat(document.title()).isEqualTo("A");
        assertThat(document.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.INDEXED);
        assertThat(fixture.service.listVersions(document.id())).hasSize(1);
        assertThat(fixture.service.listChunks(document.id())).isNotEmpty();
        assertThat(fixture.assets).containsExactly("docs/a.md:a.png:" + document.id());
    }

    @Test
    void incrementalSyncSkipsUnchangedDocuments() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/a.md", null, "# A\nhello", "same", Map.of(), List.of())));
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);

        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");
        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-2");

        var document = fixture.service.listDocuments().getFirst();
        assertThat(fixture.service.listVersions(document.id())).hasSize(1);
    }

    @Test
    void fullSyncArchivesMissingDocuments() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/a.md", null, "# A\nhello", "same", Map.of(), List.of())));
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);

        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");
        fixture.files = List.of();
        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.FULL, "trace-2");

        assertThat(fixture.service.listDocuments().getFirst().status()).isEqualTo(KnowledgeEnums.DocumentStatus.ARCHIVED);
    }

    @Test
    void recordsIndexFailureAndRetryCount() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/a.md", null, "# A\nhello", "same", Map.of(), List.of())));
        fixture.failEmbedding = true;
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);

        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");

        var document = fixture.service.listDocuments().getFirst();
        assertThat(document.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.FAILED);
        assertThat(document.indexFailureReason()).contains("embedding down");
        assertThat(document.indexRetryCount()).isEqualTo(1);
    }

    @Test
    void rebuildArchivesPreviousChunksBeforeCreatingNewIndex() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/a.md", null, "# A\nhello", "same", Map.of(), List.of())));
        fixture.failEmbedding = true;
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);
        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");
        var failed = fixture.service.listDocuments().getFirst();
        assertThat(fixture.service.listChunks(failed.id()))
                .allSatisfy(chunk -> assertThat(chunk.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.FAILED));

        fixture.failEmbedding = false;
        fixture.service.rebuildDocument(failed.id());

        var rebuilt = fixture.service.getDocument(failed.id());
        assertThat(rebuilt.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.INDEXED);
        assertThat(fixture.service.listChunks(rebuilt.id()))
                .allSatisfy(chunk -> assertThat(chunk.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.INDEXED));
    }

    @Test
    void emptyMarkdownDoesNotCallVectorStoreWithEmptyPayload() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/empty.md", null, "---\ntitle: Empty\n---\n", "empty", Map.of(), List.of())));
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);

        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");

        var document = fixture.service.listDocuments().getFirst();
        assertThat(document.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.INDEXED);
        assertThat(fixture.service.listChunks(document.id())).isEmpty();
        assertThat(fixture.embeddingCalls).isZero();
        assertThat(fixture.vectorCalls).isZero();
    }

    @Test
    void publishesSyncedDocumentAsPublicArticle() {
        Fixture fixture = new Fixture(List.of(new KnowledgeAdapters.RemoteFile(
                "docs/public-note.md", null, "# Public Note\nhello", "same", Map.of(), List.of())));
        var source = fixture.service.createDataSource("obsidian", "https://gitee.com/x/y", "master", null, "", "private", true);
        fixture.service.triggerSync(source.id(), KnowledgeEnums.SyncMode.INCREMENTAL, "trace-1");

        var published = fixture.service.publishDocument(fixture.service.listDocuments().getFirst().id());

        assertThat(published.visibility()).isEqualTo("private");
        assertThat(published.publicArticleId()).isEqualTo(fixture.publishedArticleId.toString().replace("-", ""));
    }

    @Test
    void ingestsPublishedBlogArticleWithoutChangingGiteeSource() {
        Fixture fixture = new Fixture(List.of());
        var articleId = java.util.UUID.randomUUID();

        var document = fixture.service.ingestBlogArticle(
                articleId, "Online Article", "online-article", "# Online Article\nhello", "public", true);

        assertThat(document.sourceType()).isEqualTo(KnowledgeEnums.SourceType.BLOG);
        assertThat(document.sourceId()).isEqualTo(articleId.toString());
        assertThat(document.sourcePath()).isEqualTo("blog:online-article");
        assertThat(document.publicArticleId()).isEqualTo(articleId.toString().replace("-", ""));
        assertThat(fixture.service.listVersions(document.id())).hasSize(1);
        assertThat(document.indexStatus()).isEqualTo(KnowledgeEnums.IndexStatus.INDEXED);
    }

    private static final class Fixture {
        private final InMemoryKnowledgeRepositories.DataSources sources = new InMemoryKnowledgeRepositories.DataSources();
        private final KnowledgeRepositories.KnowledgeRepository repository =
                new ForeignKeyGuardedKnowledgeRepository(new InMemoryKnowledgeRepositories.Knowledge());
        private final List<String> assets = new ArrayList<>();
        private List<KnowledgeAdapters.RemoteFile> files;
        private boolean failEmbedding;
        private int embeddingCalls;
        private int vectorCalls;
        private final UUID publishedArticleId = UUID.randomUUID();
        private final KnowledgeService service;

        private Fixture(List<KnowledgeAdapters.RemoteFile> files) {
            this.files = files;
            service = new KnowledgeService(
                    sources,
                    repository,
                    source -> this.files,
                    new KnowledgeAdapters.EmbeddingProviderPort() {
                        @Override
                        public String provider() {
                            return "test";
                        }

                        @Override
                        public String model() {
                            return "test-embedding";
                        }

                        @Override
                        public List<List<Double>> embed(List<String> contents) {
                            embeddingCalls++;
                            if (failEmbedding) {
                                throw new IllegalStateException("embedding down");
                            }
                            return contents.stream().map(content -> List.of(0.1, 0.2)).toList();
                        }
                    },
                    new KnowledgeAdapters.VectorStorePort() {
                        @Override
                        public void upsert(UUID documentId, List<KnowledgeChunk> chunks, List<List<Double>> embeddings) {
                            vectorCalls++;
                        }

                        @Override
                        public void deleteByVersion(UUID documentVersionId) {
                        }
                    },
                    (sourcePath, reference, documentId) -> assets.add(sourcePath + ":" + reference + ":" + documentId),
                    (documentId, title, sourcePath, content, indexStatus) ->
                            new KnowledgeAdapters.PublishedArticle(publishedArticleId, "public-note"),
                    Runnable::run);
        }
    }

    private static final class ForeignKeyGuardedKnowledgeRepository implements KnowledgeRepositories.KnowledgeRepository {
        private final KnowledgeRepositories.KnowledgeRepository delegate;

        private ForeignKeyGuardedKnowledgeRepository(KnowledgeRepositories.KnowledgeRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public KnowledgeDocument saveDocument(KnowledgeDocument document) {
            return delegate.saveDocument(document);
        }

        @Override
        public Optional<KnowledgeDocument> findDocument(UUID id) {
            return delegate.findDocument(id);
        }

        @Override
        public Optional<KnowledgeDocument> findDocumentBySource(String sourceType, String sourceId, String sourcePath) {
            return delegate.findDocumentBySource(sourceType, sourceId, sourcePath);
        }

        @Override
        public List<KnowledgeDocument> listDocuments() {
            return delegate.listDocuments();
        }

        @Override
        public KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version) {
            if (delegate.findDocument(version.documentId()).isEmpty()) {
                throw new IllegalStateException("knowledge_document parent must exist before saving version");
            }
            return delegate.saveVersion(version);
        }

        @Override
        public Optional<KnowledgeDocumentVersion> findVersion(UUID id) {
            return delegate.findVersion(id);
        }

        @Override
        public List<KnowledgeDocumentVersion> listVersions(UUID documentId) {
            return delegate.listVersions(documentId);
        }

        @Override
        public KnowledgeChunk saveChunk(KnowledgeChunk chunk) {
            return delegate.saveChunk(chunk);
        }

        @Override
        public List<KnowledgeChunk> listChunks(UUID versionId) {
            return delegate.listChunks(versionId);
        }

        @Override
        public void archiveChunks(UUID versionId) {
            delegate.archiveChunks(versionId);
        }

        @Override
        public SyncTask saveTask(SyncTask task) {
            return delegate.saveTask(task);
        }

        @Override
        public Optional<SyncTask> findTask(long id) {
            return delegate.findTask(id);
        }

        @Override
        public List<SyncTask> listTasks() {
            return delegate.listTasks();
        }

        @Override
        public long nextTaskId() {
            return delegate.nextTaskId();
        }

        @Override
        public ChunkingConfig getChunkingConfig() {
            return delegate.getChunkingConfig();
        }

        @Override
        public void saveChunkingConfig(ChunkingConfig config) {
            delegate.saveChunkingConfig(config);
        }
    }
}
