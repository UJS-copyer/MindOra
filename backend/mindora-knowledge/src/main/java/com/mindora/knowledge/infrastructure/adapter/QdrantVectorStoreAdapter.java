package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.mindora.knowledge.domain.KnowledgeChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class QdrantVectorStoreAdapter implements KnowledgeAdapters.VectorStorePort {
    private final RestClient restClient;
    private final String apiKey;
    private final String collection;
    private final int vectorSize;
    private final String configuredVectorName;
    private final AtomicBoolean collectionChecked = new AtomicBoolean(false);
    private volatile String activeVectorName;

    public QdrantVectorStoreAdapter(String url, String apiKey, String collection) {
        this(url, apiKey, collection, 1024, "dense");
    }

    public QdrantVectorStoreAdapter(String url, String apiKey, String collection, int vectorSize) {
        this(url, apiKey, collection, vectorSize, "dense");
    }

    public QdrantVectorStoreAdapter(String url, String apiKey, String collection, int vectorSize, String vectorName) {
        this.apiKey = apiKey;
        this.collection = collection == null || collection.isBlank() ? "mindora_chunk" : collection;
        this.vectorSize = vectorSize <= 0 ? 1024 : vectorSize;
        this.configuredVectorName = vectorName == null || vectorName.isBlank() ? "dense" : vectorName;
        this.activeVectorName = this.configuredVectorName;
        this.restClient = RestClient.builder()
                .baseUrl(url == null || url.isBlank() ? "http://localhost:6333" : url)
                .build();
    }

    @Override
    public void upsert(UUID documentId, List<KnowledgeChunk> chunks, List<List<Double>> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new BusinessException("vector_payload_invalid", "Vector payload size does not match chunks");
        }
        ensureCollection();
        List<Map<String, Object>> points = IntStream.range(0, chunks.size())
                .mapToObj(index -> Map.<String, Object>of(
                        "id", chunks.get(index).id().toString(),
                        "vector", vectorPayload(embeddings.get(index)),
                        "payload", Map.of(
                                "documentId", documentId.toString(),
                                "versionId", chunks.get(index).documentVersionId().toString(),
                                "chunkId", chunks.get(index).id().toString(),
                                "sequence", chunks.get(index).sequence())))
                .toList();
        RestClient.RequestBodySpec request = restClient.put()
                .uri("/collections/{collection}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("api-key", apiKey);
        }
        request.body(Map.of("points", points)).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteByVersion(UUID documentVersionId) {
        ensureCollection();
        RestClient.RequestBodySpec request = restClient.post()
                .uri("/collections/{collection}/points/delete?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("api-key", apiKey);
        }
        request.body(Map.of("filter", Map.of(
                "must", List.of(Map.of("key", "versionId", "match", Map.of("value", documentVersionId.toString()))))))
                .retrieve()
                .toBodilessEntity();
    }

    private void ensureCollection() {
        if (collectionChecked.get()) {
            return;
        }
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/collections/{collection}", collection);
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("api-key", apiKey);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = request.retrieve().body(Map.class);
            detectVectorName(response);
            collectionChecked.set(true);
        } catch (HttpClientErrorException.NotFound notFound) {
            RestClient.RequestBodySpec create = restClient.put()
                    .uri("/collections/{collection}", collection)
                    .contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                create.header("api-key", apiKey);
            }
            create.body(Map.of("vectors", Map.of(
                            configuredVectorName,
                            Map.of("size", vectorSize, "distance", "Cosine"))))
                    .retrieve()
                    .toBodilessEntity();
            activeVectorName = configuredVectorName;
            collectionChecked.set(true);
        }
    }

    private Object vectorPayload(List<Double> embedding) {
        String vectorName = activeVectorName;
        return vectorName == null || vectorName.isBlank()
                ? embedding
                : Map.of(vectorName, embedding);
    }

    @SuppressWarnings("unchecked")
    private void detectVectorName(Map<String, Object> response) {
        Object result = response == null ? null : response.get("result");
        Object config = result instanceof Map<?, ?> map ? map.get("config") : null;
        Object params = config instanceof Map<?, ?> map ? map.get("params") : null;
        Object vectors = params instanceof Map<?, ?> map ? map.get("vectors") : null;
        if (!(vectors instanceof Map<?, ?> map)) {
            activeVectorName = configuredVectorName;
            return;
        }
        if (map.containsKey("size")) {
            activeVectorName = null;
            return;
        }
        if (map.containsKey(configuredVectorName)) {
            activeVectorName = configuredVectorName;
            return;
        }
        activeVectorName = map.keySet().stream()
                .findFirst()
                .map(String::valueOf)
                .orElse(configuredVectorName);
    }
}
