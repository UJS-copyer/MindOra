package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.util.LinkedHashMap;
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
    public void upsert(List<KnowledgeAdapters.VectorRecord> points, List<List<Double>> embeddings) {
        if (points.size() != embeddings.size()) {
            throw new BusinessException("vector_payload_invalid", "Vector payload size does not match chunks");
        }
        if (points.isEmpty()) {
            return;
        }
        ensureCollection();
        List<Map<String, Object>> payloads = IntStream.range(0, points.size())
                .mapToObj(index -> Map.<String, Object>of(
                        "id", points.get(index).chunkId().toString(),
                        "vector", vectorPayload(embeddings.get(index)),
                        "payload", payload(points.get(index))))
                .toList();
        RestClient.RequestBodySpec request = restClient.put()
                .uri("/collections/{collection}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("api-key", apiKey);
        }
        request.body(Map.of("points", payloads)).retrieve().toBodilessEntity();
    }

    /**
     * 组装 Qdrant payload。
     *
     * <p>键名保持 camelCase 以兼容既有数据；空值字段不写入，避免向量库中出现 null。
     * 点 ID 直接使用 {@code chunkId}，使向量库点与 {@code knowledge_chunk.id} 一一对应。
     */
    Map<String, Object> payload(KnowledgeAdapters.VectorRecord point) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", point.chunkId().toString());
        payload.put("documentId", point.documentId().toString());
        payload.put("versionId", point.versionId().toString());
        payload.put("sequence", point.sequence());
        payload.put("sourceType", point.sourceType());
        payload.put("sourceId", point.sourceId());
        payload.put("visibility", point.visibility());
        payload.put("status", point.status());
        payload.put("knowledgeEnabled", point.knowledgeEnabled());
        putIfPresent(payload, "sourcePath", point.sourcePath());
        putIfPresent(payload, "headingPath", point.headingPath());
        putIfPresent(payload, "blockType", point.blockType());
        putIfPresent(payload, "codeLanguage", point.codeLanguage());
        putIfPresent(payload, "articleId", point.articleId());
        putIfPresent(payload, "articleSlug", point.articleSlug());
        putIfPresent(payload, "contentHash", point.contentHash());
        return payload;
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
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

    @Override
    public void updatePayload(UUID documentVersionId, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        ensureCollection();
        RestClient.RequestBodySpec request = restClient.post()
                .uri("/collections/{collection}/points/payload?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("api-key", apiKey);
        }
        request.body(Map.of(
                        "payload", payload,
                        "filter", qdrantFilter(Map.of("versionId", documentVersionId.toString()))))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<KnowledgeAdapters.VectorHit> search(
            List<Double> queryVector, int topN, Map<String, Object> exactMatchFilter) {
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", searchVector(queryVector));
        body.put("limit", Math.max(1, topN));
        body.put("with_payload", true);
        Map<String, Object> filter = qdrantFilter(exactMatchFilter);
        if (!filter.isEmpty()) {
            body.put("filter", filter);
        }
        RestClient.RequestBodySpec request = restClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("api-key", apiKey);
        }
        Map<String, Object> response = request.body(body).retrieve().body(Map.class);
        Object result = response == null ? null : response.get("result");
        if (!(result instanceof List<?> items)) {
            throw new BusinessException("vector_response_invalid", "Vector search response is invalid");
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(item -> toHit((Map<String, Object>) item))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 把应用层的「字段精确匹配」翻译为 Qdrant filter 语法。
     *
     * <p>向量库专属语法只存在于 adapter 内，应用层不需要感知。
     */
    static Map<String, Object> qdrantFilter(Map<String, Object> exactMatchFilter) {
        if (exactMatchFilter == null || exactMatchFilter.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> must = exactMatchFilter.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> Map.<String, Object>of(
                        "key", entry.getKey(),
                        "match", Map.of("value", entry.getValue())))
                .toList();
        if (must.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", must);
        return filter;
    }

    @SuppressWarnings("unchecked")
    private KnowledgeAdapters.VectorHit toHit(Map<String, Object> item) {
        Object payload = item.get("payload");
        Map<String, Object> values = payload instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        UUID chunkId = uuid(values.get("chunkId"));
        UUID documentId = uuid(values.get("documentId"));
        UUID versionId = uuid(values.get("versionId"));
        if (chunkId == null || documentId == null || versionId == null) {
            return null;
        }
        double score = item.get("score") instanceof Number number ? number.doubleValue() : 0d;
        return new KnowledgeAdapters.VectorHit(chunkId, documentId, versionId, score, values);
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

    /**
     * 检索接口的向量格式与写入不同：{@code points/search} 使用 Qdrant 的 NamedVectorStruct，
     * 命名向量必须写成 {@code {"name": "...", "vector": [...]}}，而不是写入时的
     * {@code {"<name>": [...]}} 映射形式；写成映射会被 Qdrant 以 JSON 格式错误拒绝。
     */
    private Object searchVector(List<Double> queryVector) {
        String vectorName = activeVectorName;
        return vectorName == null || vectorName.isBlank()
                ? queryVector
                : Map.of("name", vectorName, "vector", queryVector);
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
