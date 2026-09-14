package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OpenAiCompatibleEmbeddingAdapter implements KnowledgeAdapters.EmbeddingProviderPort {
    private final RestClient restClient;
    private final String provider;
    private final String model;
    private final String apiKey;
    private final int dimensions;

    public OpenAiCompatibleEmbeddingAdapter(String provider, String baseUrl, String apiKey, String model) {
        this(provider, baseUrl, apiKey, model, 1024);
    }

    public OpenAiCompatibleEmbeddingAdapter(String provider, String baseUrl, String apiKey, String model, int dimensions) {
        this.provider = provider == null || provider.isBlank() ? "bailian" : provider;
        this.model = model == null || model.isBlank() ? "text-embedding-v4" : model;
        this.apiKey = apiKey;
        this.dimensions = dimensions;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : baseUrl)
                .build();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Double>> embed(List<String> contents) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("embedding_api_key_missing", "Embedding API key is not configured");
        }
        List<List<Double>> embeddings = new java.util.ArrayList<>();
        for (int start = 0; start < contents.size(); start += 10) {
            embeddings.addAll(embedBatch(contents.subList(start, Math.min(start + 10, contents.size()))));
        }
        return embeddings;
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> embedBatch(List<String> contents) {
        Map<String, Object> requestBody = dimensions > 0
                ? Map.of("model", model, "input", contents, "dimensions", dimensions)
                : Map.of("model", model, "input", contents);
        Map<String, Object> response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> items)) {
            throw new BusinessException("embedding_response_invalid", "Embedding response is invalid");
        }
        return items.stream()
                .map(item -> (Map<String, Object>) item)
                .map(item -> (List<Double>) item.get("embedding"))
                .toList();
    }
}
