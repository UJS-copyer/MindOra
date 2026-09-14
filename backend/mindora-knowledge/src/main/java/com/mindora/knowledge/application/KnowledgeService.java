package com.mindora.knowledge.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.application.port.KnowledgeAdapters.AssetReferencePort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.DocumentPublicationPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.EmbeddingProviderPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.GiteeRepositoryPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.RemoteFile;
import com.mindora.knowledge.application.port.KnowledgeAdapters.SyncTaskExecutorPort;
import com.mindora.knowledge.application.port.KnowledgeAdapters.VectorStorePort;
import com.mindora.knowledge.application.port.KnowledgeRepositories.DataSourceRepository;
import com.mindora.knowledge.application.port.KnowledgeRepositories.KnowledgeRepository;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeDocumentVersion;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.domain.SyncTask;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

public class KnowledgeService {
    private final DataSourceRepository dataSources;
    private final KnowledgeRepository repository;
    private final GiteeRepositoryPort gitee;
    private final EmbeddingProviderPort embedding;
    private final KnowledgeAdapters.RerankProviderPort rerank;
    private final VectorStorePort vectors;
    private final AssetReferencePort assets;
    private final DocumentPublicationPort publication;
    private final SyncTaskExecutorPort taskExecutor;
    private final MarkdownDocumentParser parser;
    private final MarkdownChunker chunker;
    private final Clock clock;

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            VectorStorePort vectors,
            AssetReferencePort assets,
            Executor executor) {
        this(dataSources, repository, gitee, embedding, KnowledgeService.unavailableRerank(), vectors, assets,
                (documentId, title, sourcePath, content, indexStatus) -> {
                    throw new BusinessException(
                            "knowledge_publication_unavailable",
                            "Knowledge document publication is not configured in this runtime");
                },
                executor);
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            VectorStorePort vectors,
            AssetReferencePort assets,
            DocumentPublicationPort publication,
            Executor executor) {
        this.dataSources = dataSources;
        this.repository = repository;
        this.gitee = gitee;
        this.embedding = embedding;
        this.rerank = KnowledgeService.unavailableRerank();
        this.vectors = vectors;
        this.assets = assets;
        this.publication = publication;
        this.taskExecutor = taskId -> executor.execute(() -> executeTask(taskId));
        this.parser = new MarkdownDocumentParser();
        this.chunker = new MarkdownChunker();
        this.clock = Clock.systemUTC();
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            KnowledgeAdapters.RerankProviderPort rerank,
            VectorStorePort vectors,
            AssetReferencePort assets,
            DocumentPublicationPort publication,
            Executor executor) {
        this.dataSources = dataSources;
        this.repository = repository;
        this.gitee = gitee;
        this.embedding = embedding;
        this.rerank = rerank;
        this.vectors = vectors;
        this.assets = assets;
        this.publication = publication;
        this.taskExecutor = taskId -> executor.execute(() -> executeTask(taskId));
        this.parser = new MarkdownDocumentParser();
        this.chunker = new MarkdownChunker();
        this.clock = Clock.systemUTC();
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            VectorStorePort vectors,
            AssetReferencePort assets,
            SyncTaskExecutorPort taskExecutor) {
        this(dataSources, repository, gitee, embedding, KnowledgeService.unavailableRerank(), vectors, assets,
                (documentId, title, sourcePath, content, indexStatus) -> {
                    throw new BusinessException(
                            "knowledge_publication_unavailable",
                            "Knowledge document publication is not configured in this runtime");
                },
                taskExecutor,
                new MarkdownDocumentParser(), new MarkdownChunker(), Clock.systemUTC());
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            VectorStorePort vectors,
            AssetReferencePort assets,
            DocumentPublicationPort publication,
            SyncTaskExecutorPort taskExecutor) {
        this(dataSources, repository, gitee, embedding, KnowledgeService.unavailableRerank(), vectors, assets, publication, taskExecutor,
                new MarkdownDocumentParser(), new MarkdownChunker(), Clock.systemUTC());
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            KnowledgeAdapters.RerankProviderPort rerank,
            VectorStorePort vectors,
            AssetReferencePort assets,
            DocumentPublicationPort publication,
            SyncTaskExecutorPort taskExecutor) {
        this(dataSources, repository, gitee, embedding, rerank, vectors, assets, publication, taskExecutor,
                new MarkdownDocumentParser(), new MarkdownChunker(), Clock.systemUTC());
    }

    public KnowledgeService(
            DataSourceRepository dataSources,
            KnowledgeRepository repository,
            GiteeRepositoryPort gitee,
            EmbeddingProviderPort embedding,
            KnowledgeAdapters.RerankProviderPort rerank,
            VectorStorePort vectors,
            AssetReferencePort assets,
            DocumentPublicationPort publication,
            SyncTaskExecutorPort taskExecutor,
            MarkdownDocumentParser parser,
            MarkdownChunker chunker,
            Clock clock) {
        this.dataSources = dataSources;
        this.repository = repository;
        this.gitee = gitee;
        this.embedding = embedding;
        this.rerank = rerank;
        this.vectors = vectors;
        this.assets = assets;
        this.publication = publication;
        this.taskExecutor = taskExecutor;
        this.parser = parser;
        this.chunker = chunker;
        this.clock = clock;
    }

    public DataSource createDataSource(
            String name,
            String repositoryUrl,
            String branch,
            String accessToken,
            String rootPath,
            String visibility,
            boolean enabled) {
        if (name == null || name.isBlank() || repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new BusinessException("knowledge_source_invalid", "Gitee source name and URL are required");
        }
        Instant now = clock.instant();
        return dataSources.save(new DataSource(
                UUID.randomUUID(), name.trim(), KnowledgeEnums.SourceType.GITEE, repositoryUrl.trim(),
                branch, accessToken, rootPath, visibility, enabled, now, now));
    }

    public DataSource updateDataSource(
            UUID id,
            String name,
            String repositoryUrl,
            String branch,
            String accessToken,
            String rootPath,
            String visibility,
            boolean enabled) {
        DataSource source = findDataSource(id);
        return dataSources.save(source.update(name, repositoryUrl, branch, accessToken, rootPath,
                visibility, enabled, clock.instant()));
    }

    public List<DataSource> listDataSources() {
        return dataSources.list();
    }

    public SyncTask triggerSync(UUID sourceId, KnowledgeEnums.SyncMode mode, String traceId) {
        DataSource source = findDataSource(sourceId);
        if (!source.enabled()) {
            throw new BusinessException("knowledge_source_disabled", "Data source is disabled");
        }
        SyncTask task = new SyncTask(
                repository.nextTaskId(), source.id(), mode == null ? KnowledgeEnums.SyncMode.INCREMENTAL : mode,
                KnowledgeEnums.TaskStatus.PENDING, 0, 0, 0, 0, 0, 0, null,
                traceId, clock.instant(), null, null);
        repository.saveTask(task);
        taskExecutor.execute(task.id());
        return repository.findTask(task.id()).orElse(task);
    }

    public SyncTask retryTask(long taskId) {
        SyncTask old = repository.findTask(taskId)
                .orElseThrow(() -> new BusinessException("knowledge_task_not_found", "Sync task not found"));
        return triggerSync(old.sourceId(), old.mode(), old.traceId());
    }

    public List<SyncTask> listTasks() {
        return repository.listTasks().stream()
                .sorted(java.util.Comparator.comparing(SyncTask::createdAt).reversed())
                .toList();
    }

    public SyncTask getTask(long id) {
        return repository.findTask(id)
                .orElseThrow(() -> new BusinessException("knowledge_task_not_found", "Sync task not found"));
    }

    public List<KnowledgeDocument> listDocuments() {
        return repository.listDocuments().stream()
                .sorted(java.util.Comparator.comparing(KnowledgeDocument::updatedAt).reversed())
                .toList();
    }

    public KnowledgeDocument updateDocumentSettings(UUID id, boolean knowledgeEnabled, String visibility) {
        KnowledgeDocument document = getDocument(id);
        return repository.saveDocument(new KnowledgeDocument(
                document.id(), document.sourceType(), document.sourceId(), document.sourcePath(), document.title(),
                document.currentVersionId(), document.sourceHash(),
                visibility == null || visibility.isBlank() ? document.visibility() : visibility,
                knowledgeEnabled, document.publicArticleId(), document.status(), document.parseStatus(), document.indexStatus(),
                document.indexFailureReason(), document.indexRetryCount(), document.createdAt(), clock.instant()));
    }

    public KnowledgeDocument publishDocument(UUID documentId) {
        KnowledgeDocument document = getDocument(documentId);
        if (document.currentVersionId() == null) {
            throw new BusinessException("knowledge_version_not_found", "Document has no current version");
        }
        KnowledgeDocumentVersion version = repository.findVersion(UUID.fromString(document.currentVersionId()))
                .orElseThrow(() -> new BusinessException("knowledge_version_not_found", "Document version not found"));
        var article = publication.publishMarkdownDocument(
                document.id(),
                document.title(),
                document.sourcePath(),
                version.contentSnapshot(),
                document.indexStatus().name().toLowerCase());
        return repository.saveDocument(document.linkedArticle(PublicIds.toPublicId(article.articleId()), clock.instant()));
    }

    public KnowledgeDocument ingestBlogArticle(
            UUID articleId,
            String title,
            String slug,
            String body,
            String visibility,
            boolean enabled) {
        if (articleId == null) {
            throw new BusinessException("article_id_required", "Article id is required");
        }
        KnowledgeDocument existing = findBlogDocument(articleId);
        if (!enabled) {
            return disableBlogArticle(articleId);
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new BusinessException("article_content_required", "Article title and body are required");
        }
        Instant now = clock.instant();
        String sourcePath = "blog:" + (slug == null || slug.isBlank() ? articleId : slug.trim());
        String normalizedVisibility = visibility == null || visibility.isBlank() ? "public" : visibility.trim().toLowerCase();
        String hash = KnowledgeHash.sha256(body);
        KnowledgeDocument document = existing == null
                ? new KnowledgeDocument(
                        UUID.randomUUID(), KnowledgeEnums.SourceType.BLOG, articleId.toString(), sourcePath,
                        title.trim(), null, null, normalizedVisibility, true, PublicIds.toPublicId(articleId),
                        KnowledgeEnums.DocumentStatus.ACTIVE, KnowledgeEnums.ParseStatus.PENDING,
                        KnowledgeEnums.IndexStatus.PENDING, null, 0, now, now)
                : new KnowledgeDocument(
                        existing.id(), existing.sourceType(), existing.sourceId(), sourcePath, title.trim(),
                        existing.currentVersionId(), existing.sourceHash(), normalizedVisibility, true,
                        PublicIds.toPublicId(articleId), KnowledgeEnums.DocumentStatus.ACTIVE,
                        existing.parseStatus(), existing.indexStatus(), existing.indexFailureReason(),
                        existing.indexRetryCount(), existing.createdAt(), now);
        if (existing != null && hash.equals(existing.sourceHash())) {
            return repository.saveDocument(document);
        }
        MarkdownDocumentParser.ParsedDocument parsed = parser.parse(sourcePath, body);
        UUID versionId = UUID.randomUUID();
        repository.saveDocument(document);
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(
                versionId, document.id(), KnowledgeEnums.SourceType.BLOG, articleId.toString(), sourcePath,
                parsed.body(), metadata(parsed), hash, now);
        repository.saveVersion(version);
        if (document.currentVersionId() != null) {
            repository.archiveChunks(UUID.fromString(document.currentVersionId()));
        }
        KnowledgeDocument versioned = document.versioned(versionId.toString(), hash, title.trim(), now);
        repository.saveDocument(versioned);
        for (String image : parsed.imageReferences()) {
            assets.attachMarkdownReference(sourcePath, image, document.id().toString());
        }
        indexVersion(versioned, versionId);
        return getDocument(document.id());
    }

    public KnowledgeDocument disableBlogArticle(UUID articleId) {
        if (articleId == null) {
            throw new BusinessException("article_id_required", "Article id is required");
        }
        KnowledgeDocument existing = findBlogDocument(articleId);
        if (existing == null) {
            return new KnowledgeDocument(
                    null, KnowledgeEnums.SourceType.BLOG, articleId.toString(), "blog:" + articleId,
                    "Blog Article", null, null, "private", false, PublicIds.toPublicId(articleId),
                    KnowledgeEnums.DocumentStatus.ACTIVE, KnowledgeEnums.ParseStatus.PENDING,
                    KnowledgeEnums.IndexStatus.PENDING, null, 0, clock.instant(), clock.instant());
        }
        return repository.saveDocument(new KnowledgeDocument(
                existing.id(), existing.sourceType(), existing.sourceId(), existing.sourcePath(), existing.title(),
                existing.currentVersionId(), existing.sourceHash(), existing.visibility(), false,
                existing.publicArticleId(), existing.status(), existing.parseStatus(), existing.indexStatus(),
                existing.indexFailureReason(), existing.indexRetryCount(), existing.createdAt(), clock.instant()));
    }

    public KnowledgeDocument getDocument(UUID id) {
        return repository.findDocument(id)
                .orElseThrow(() -> new BusinessException("knowledge_document_not_found", "Knowledge document not found"));
    }

    public List<KnowledgeDocumentVersion> listVersions(UUID documentId) {
        getDocument(documentId);
        return repository.listVersions(documentId);
    }

    public List<KnowledgeChunk> listChunks(UUID documentId) {
        KnowledgeDocument document = getDocument(documentId);
        if (document.currentVersionId() == null) {
            return List.of();
        }
        return repository.listChunks(UUID.fromString(document.currentVersionId()));
    }

    public void rebuildDocument(UUID documentId) {
        KnowledgeDocument document = getDocument(documentId);
        if (document.currentVersionId() == null) {
            throw new BusinessException("knowledge_version_not_found", "Document has no current version");
        }
        UUID versionId = UUID.fromString(document.currentVersionId());
        repository.archiveChunks(versionId);
        indexVersion(document, versionId);
    }

    public int rebuildDocuments(List<UUID> documentIds) {
        List<UUID> ids = documentIds == null ? List.of() : documentIds;
        ids.forEach(this::rebuildDocument);
        return ids.size();
    }

    public ChunkingConfig getChunkingConfig() {
        return repository.getChunkingConfig();
    }

    public ChunkingConfig updateChunkingConfig(ChunkingConfig config) {
        repository.saveChunkingConfig(config);
        return repository.getChunkingConfig();
    }

    public List<KnowledgeAdapters.RerankResult> rerankPreview(String query, List<String> documents, int topN) {
        if (query == null || query.isBlank()) {
            throw new BusinessException("rerank_query_required", "Rerank query is required");
        }
        List<String> candidates = documents == null ? List.of() : documents.stream()
                .filter(document -> document != null && !document.isBlank())
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException("rerank_documents_required", "Rerank documents are required");
        }
        return rerank.rerank(query.trim(), candidates, topN <= 0 ? candidates.size() : topN);
    }

    public void executeTask(long taskId) {
        SyncTask task = getTask(taskId);
        DataSource source = findDataSource(task.sourceId());
        repository.saveTask(task.start(clock.instant()));
        try {
            List<RemoteFile> files = gitee.listMarkdownFiles(source);
            Set<String> seen = new HashSet<>();
            int created = 0;
            int updated = 0;
            for (RemoteFile file : files) {
                if (file.path() == null || !file.path().toLowerCase().endsWith(".md")) {
                    continue;
                }
                seen.add(file.path());
                KnowledgeDocument existing = repository.findDocumentBySource(
                        KnowledgeEnums.SourceType.GITEE.name(), source.id().toString(), file.path()).orElse(null);
                String hash = file.contentHash() == null || file.contentHash().isBlank()
                        ? KnowledgeHash.sha256(file.content()) : file.contentHash();
                if (existing != null && hash.equals(existing.sourceHash())) {
                    if (existing.knowledgeEnabled()
                            && existing.currentVersionId() != null
                            && existing.indexStatus() != KnowledgeEnums.IndexStatus.INDEXED) {
                        rebuildDocument(existing.id());
                    }
                    continue;
                }
                if (existing == null) {
                    existing = new KnowledgeDocument(
                            UUID.randomUUID(), KnowledgeEnums.SourceType.GITEE, source.id().toString(),
                            file.path(), file.title(), null, null, source.defaultVisibility(), true,
                            null, KnowledgeEnums.DocumentStatus.ACTIVE, KnowledgeEnums.ParseStatus.PENDING,
                            KnowledgeEnums.IndexStatus.PENDING, null, 0, clock.instant(), clock.instant());
                    created++;
                } else {
                    updated++;
                }
                ingestFile(source, existing, file, hash);
            }
            int archived = archiveMissing(source, seen, task.mode());
            repository.saveTask(getTask(taskId).success(
                    files.size(), files.size(), created, updated, archived, clock.instant()));
        } catch (RuntimeException exception) {
            repository.saveTask(getTask(taskId).failed(
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    clock.instant()));
        }
    }

    private void ingestFile(DataSource source, KnowledgeDocument document, RemoteFile file, String hash) {
        MarkdownDocumentParser.ParsedDocument parsed = parser.parse(file.path(), file.content());
        UUID versionId = UUID.randomUUID();
        repository.saveDocument(document);
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(
                versionId, document.id(), KnowledgeEnums.SourceType.GITEE, source.id().toString(),
                file.path(), parsed.body(), metadata(parsed), hash, clock.instant());
        repository.saveVersion(version);
        if (document.currentVersionId() != null) {
            repository.archiveChunks(UUID.fromString(document.currentVersionId()));
        }
        KnowledgeDocument versioned = document.versioned(versionId.toString(), hash, parsed.title(), clock.instant());
        repository.saveDocument(versioned);
        for (String image : parsed.imageReferences()) {
            assets.attachMarkdownReference(file.path(), image, document.id().toString());
        }
        indexVersion(versioned, versionId);
    }

    private void indexVersion(KnowledgeDocument document, UUID versionId) {
        KnowledgeDocumentVersion version = repository.findVersion(versionId)
                .orElseThrow(() -> new BusinessException("knowledge_version_not_found", "Document version not found"));
        List<String> contents = chunker.chunk(version.contentSnapshot(), repository.getChunkingConfig());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            String content = contents.get(index);
            chunks.add(new KnowledgeChunk(
                    UUID.randomUUID(), versionId, index, content, content.length(),
                    KnowledgeEnums.IndexStatus.VECTORIZING, null, null, null, 0, clock.instant()));
        }
        chunks.forEach(repository::saveChunk);
        if (contents.isEmpty()) {
            repository.saveDocument(document.index(KnowledgeEnums.IndexStatus.INDEXED, null, clock.instant()));
            return;
        }
        try {
            List<List<Double>> embeddings = embedding.embed(contents);
            if (embeddings.size() != contents.size()) {
                throw new IllegalStateException("Embedding provider returned an unexpected vector count");
            }
            List<KnowledgeChunk> indexed = IntStream.range(0, chunks.size())
                    .mapToObj(index -> chunks.get(index).indexed(embedding.model(), document.id() + ":" + index))
                    .toList();
            vectors.upsert(document.id(), indexed, embeddings);
            indexed.forEach(repository::saveChunk);
            repository.saveDocument(document.index(KnowledgeEnums.IndexStatus.INDEXED, null, clock.instant()));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            chunks.stream().map(chunk -> chunk.failed(reason)).forEach(repository::saveChunk);
            repository.saveDocument(document.index(KnowledgeEnums.IndexStatus.FAILED, reason, clock.instant()));
        }
    }

    private int archiveMissing(DataSource source, Set<String> seen, KnowledgeEnums.SyncMode mode) {
        if (mode != KnowledgeEnums.SyncMode.FULL) {
            return 0;
        }
        int archived = 0;
        for (KnowledgeDocument document : repository.listDocuments()) {
            if (document.sourceType() == KnowledgeEnums.SourceType.GITEE
                    && source.id().toString().equals(document.sourceId())
                    && !seen.contains(document.sourcePath())
                    && document.status() == KnowledgeEnums.DocumentStatus.ACTIVE) {
                repository.saveDocument(document.archived(clock.instant()));
                archived++;
            }
        }
        return archived;
    }

    private KnowledgeDocument findBlogDocument(UUID articleId) {
        return repository.listDocuments().stream()
                .filter(document -> document.sourceType() == KnowledgeEnums.SourceType.BLOG)
                .filter(document -> articleId.toString().equals(document.sourceId()))
                .findFirst()
                .orElse(null);
    }

    private String metadata(MarkdownDocumentParser.ParsedDocument parsed) {
        return parsed.frontmatter().entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private DataSource findDataSource(UUID id) {
        return dataSources.find(id)
                .orElseThrow(() -> new BusinessException("knowledge_source_not_found", "Data source not found"));
    }

    private static KnowledgeAdapters.RerankProviderPort unavailableRerank() {
        return new KnowledgeAdapters.RerankProviderPort() {
            @Override
            public String provider() {
                return "siliconflow";
            }

            @Override
            public String model() {
                return "BAAI/bge-reranker-v2-m3";
            }

            @Override
            public List<KnowledgeAdapters.RerankResult> rerank(String query, List<String> documents, int topN) {
                throw new BusinessException(
                        "rerank_adapter_unavailable",
                        "Rerank provider is not configured in this runtime");
            }
        };
    }
}
