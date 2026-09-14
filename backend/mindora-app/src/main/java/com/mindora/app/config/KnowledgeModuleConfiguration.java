package com.mindora.app.config;

import com.mindora.knowledge.application.port.KnowledgeRepositories;
import com.mindora.app.knowledge.RocketMqKnowledgeSyncTaskDispatcher;
import com.mindora.blog.application.ArticleKnowledgePort;
import com.mindora.blog.application.ArticleService;
import com.mindora.blog.domain.BlogArticle;
import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.KnowledgeDocument;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.knowledge.infrastructure.adapter.GiteeApiRepositoryAdapter;
import com.mindora.knowledge.infrastructure.adapter.OpenAiCompatibleEmbeddingAdapter;
import com.mindora.knowledge.infrastructure.adapter.QdrantVectorStoreAdapter;
import com.mindora.knowledge.infrastructure.adapter.SiliconFlowRerankAdapter;
import com.mindora.knowledge.infrastructure.adapter.UnavailableKnowledgeAdapters;
import com.mindora.knowledge.infrastructure.persistence.jdbc.JdbcKnowledgeDataSourceRepository;
import com.mindora.knowledge.infrastructure.persistence.jdbc.JdbcKnowledgeRepository;
import com.mindora.knowledge.infrastructure.persistence.memory.InMemoryKnowledgeRepositories;
import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Configuration
public class KnowledgeModuleConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.knowledge", havingValue = "memory")
    KnowledgeRepositories.DataSourceRepository memoryKnowledgeDataSourceRepository() {
        return new InMemoryKnowledgeRepositories.DataSources();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.knowledge", havingValue = "memory")
    KnowledgeRepositories.KnowledgeRepository memoryKnowledgeRepository() {
        return new InMemoryKnowledgeRepositories.Knowledge();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.knowledge", havingValue = "jdbc", matchIfMissing = true)
    KnowledgeRepositories.DataSourceRepository jdbcKnowledgeDataSourceRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcKnowledgeDataSourceRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.knowledge", havingValue = "jdbc", matchIfMissing = true)
    KnowledgeRepositories.KnowledgeRepository jdbcKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcKnowledgeRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.gitee.enabled", havingValue = "true", matchIfMissing = true)
    KnowledgeAdapters.GiteeRepositoryPort giteeRepositoryPort(
            @Value("${mindora.knowledge.gitee.base-url:https://gitee.com/api/v5}") String baseUrl,
            @Value("${mindora.knowledge.gitee.default-token:${GITEE_TOKEN:}}") String defaultAccessToken) {
        return new GiteeApiRepositoryAdapter(baseUrl, defaultAccessToken);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.GiteeRepositoryPort unavailableGiteeRepositoryPort() {
        return new UnavailableKnowledgeAdapters.Gitee();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.embedding.enabled", havingValue = "true", matchIfMissing = true)
    KnowledgeAdapters.EmbeddingProviderPort embeddingProviderPort(
            @Value("${mindora.knowledge.embedding.provider:bailian}") String provider,
            @Value("${mindora.knowledge.embedding.base-url:${EMBEDDING_BASE_URL:}}") String baseUrl,
            @Value("${mindora.knowledge.embedding.api-key:${EMBEDDING_API_KEY:}}") String apiKey,
            @Value("${mindora.knowledge.embedding.model:${EMBEDDING_MODEL:text-embedding-v4}}") String model,
            @Value("${mindora.knowledge.embedding.dimensions:${EMBEDDING_DIMENSIONS:1024}}") int dimensions) {
        return new OpenAiCompatibleEmbeddingAdapter(provider, baseUrl, apiKey, model, dimensions);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.EmbeddingProviderPort unavailableEmbeddingProviderPort() {
        return new UnavailableKnowledgeAdapters.Embedding();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.rerank.enabled", havingValue = "true", matchIfMissing = true)
    KnowledgeAdapters.RerankProviderPort rerankProviderPort(
            @Value("${mindora.knowledge.rerank.provider:siliconflow}") String provider,
            @Value("${mindora.knowledge.rerank.base-url:${RERANK_BASE_URL:https://api.siliconflow.cn/v1}}") String baseUrl,
            @Value("${mindora.knowledge.rerank.api-key:${RERANK_MODEL_API_KEY:}}") String apiKey,
            @Value("${mindora.knowledge.rerank.model:${RERANK_MODEL:BAAI/bge-reranker-v2-m3}}") String model) {
        return new SiliconFlowRerankAdapter(provider, baseUrl, apiKey, model);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.RerankProviderPort unavailableRerankProviderPort() {
        return new UnavailableKnowledgeAdapters.Rerank();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.vector.enabled", havingValue = "true", matchIfMissing = true)
    KnowledgeAdapters.VectorStorePort vectorStorePort(
            @Value("${mindora.qdrant.url:${QDRANT_URL:http://localhost:6333}}") String url,
            @Value("${mindora.qdrant.api-key:${QDRANT_API_KEY:}}") String apiKey,
            @Value("${mindora.qdrant.collection:${QDRANT_COLLECTION:mindora_chunk}}") String collection,
            @Value("${mindora.knowledge.embedding.dimensions:${EMBEDDING_DIMENSIONS:1024}}") int dimensions,
            @Value("${mindora.qdrant.vector-name:${QDRANT_VECTOR_NAME:dense}}") String vectorName) {
        return new QdrantVectorStoreAdapter(url, apiKey, collection, dimensions, vectorName);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.VectorStorePort unavailableVectorStorePort() {
        return new UnavailableKnowledgeAdapters.VectorStore();
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.AssetReferencePort assetReferencePort() {
        return new UnavailableKnowledgeAdapters.AssetReference();
    }

    @Bean
    @ConditionalOnMissingBean
    ArticleKnowledgePort articleKnowledgePort(ObjectProvider<KnowledgeService> knowledgeService) {
        return new ArticleKnowledgePort() {
            @Override
            public ArticleKnowledgeState upsert(BlogArticle article) {
                KnowledgeService service = knowledgeService.getIfAvailable();
                if (service == null) {
                    return new ArticleKnowledgeState(article.knowledgeDocumentId(), article.knowledgeIndexStatus());
                }
                return upsertArticleKnowledge(service, article);
            }

            @Override
            public ArticleKnowledgeState disable(BlogArticle article) {
                KnowledgeService service = knowledgeService.getIfAvailable();
                if (service == null) {
                    return new ArticleKnowledgeState(article.knowledgeDocumentId(), article.knowledgeIndexStatus());
                }
                if (article.knowledgeDocumentId() != null) {
                    var document = service.updateDocumentSettings(
                            article.knowledgeDocumentId(), false, article.visibility());
                    return new ArticleKnowledgeState(document.id(), document.indexStatus().name().toLowerCase());
                }
                var document = service.disableBlogArticle(article.id());
                return new ArticleKnowledgeState(document.id(), document.indexStatus().name().toLowerCase());
            }
        };
    }

    private ArticleKnowledgePort.ArticleKnowledgeState upsertArticleKnowledge(
            KnowledgeService service,
            BlogArticle article) {
        KnowledgeDocument linkedDocument = findLinkedDocument(service, article);
        if (linkedDocument != null && linkedDocument.sourceType() == KnowledgeEnums.SourceType.GITEE) {
            var document = service.updateDocumentSettings(
                    linkedDocument.id(), article.knowledgeEnabled(), article.visibility());
            return new ArticleKnowledgePort.ArticleKnowledgeState(
                    document.id(), document.indexStatus().name().toLowerCase());
        }
        var document = service.ingestBlogArticle(
                article.id(), article.title(), article.slug(), article.body(),
                article.visibility(), article.knowledgeEnabled());
        return new ArticleKnowledgePort.ArticleKnowledgeState(
                document.id(), document.indexStatus().name().toLowerCase());
    }

    private KnowledgeDocument findLinkedDocument(KnowledgeService service, BlogArticle article) {
        if (article.knowledgeDocumentId() == null) {
            return null;
        }
        try {
            return service.getDocument(article.knowledgeDocumentId());
        } catch (BusinessException exception) {
            if ("knowledge_document_not_found".equals(exception.code())) {
                return null;
            }
            throw exception;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeAdapters.DocumentPublicationPort documentPublicationPort(ArticleService articleService) {
        return (documentId, title, sourcePath, content, indexStatus) -> {
            var article = articleService.publishKnowledgeDocument(documentId, title, sourcePath, content, indexStatus);
            return new KnowledgeAdapters.PublishedArticle(article.id(), article.slug());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.async", havingValue = "false")
    Executor syncKnowledgeExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.async", havingValue = "true", matchIfMissing = true)
    Executor asyncKnowledgeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("knowledge-sync-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.dispatcher", havingValue = "rocketmq")
    KnowledgeAdapters.SyncTaskExecutorPort rocketMqKnowledgeSyncTaskDispatcher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${mindora.knowledge.sync-topic:mindora-knowledge-sync}") String topic) {
        return new RocketMqKnowledgeSyncTaskDispatcher(rocketMQTemplate, topic);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.dispatcher", havingValue = "rocketmq")
    KnowledgeService knowledgeService(
            KnowledgeRepositories.DataSourceRepository dataSources,
            KnowledgeRepositories.KnowledgeRepository repository,
            KnowledgeAdapters.GiteeRepositoryPort gitee,
            KnowledgeAdapters.EmbeddingProviderPort embedding,
            KnowledgeAdapters.RerankProviderPort rerank,
            KnowledgeAdapters.VectorStorePort vectors,
            KnowledgeAdapters.AssetReferencePort assets,
            KnowledgeAdapters.DocumentPublicationPort publication,
            KnowledgeAdapters.SyncTaskExecutorPort taskExecutor) {
        return new KnowledgeService(dataSources, repository, gitee, embedding, rerank, vectors, assets, publication, taskExecutor);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.knowledge.dispatcher", havingValue = "executor", matchIfMissing = true)
    KnowledgeService executorKnowledgeService(
            KnowledgeRepositories.DataSourceRepository dataSources,
            KnowledgeRepositories.KnowledgeRepository repository,
            KnowledgeAdapters.GiteeRepositoryPort gitee,
            KnowledgeAdapters.EmbeddingProviderPort embedding,
            KnowledgeAdapters.RerankProviderPort rerank,
            KnowledgeAdapters.VectorStorePort vectors,
            KnowledgeAdapters.AssetReferencePort assets,
            KnowledgeAdapters.DocumentPublicationPort publication,
            Executor executor) {
        return new KnowledgeService(dataSources, repository, gitee, embedding, rerank, vectors, assets, publication, executor);
    }
}
