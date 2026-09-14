package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class SiliconFlowRerankAdapter implements KnowledgeAdapters.RerankProviderPort {
    private final RestClient restClient;
    private final String provider;
    private final String model;
    private final String apiKey;

    public SiliconFlowRerankAdapter(String provider, String baseUrl, String apiKey, String model) {
        this.provider = provider == null || provider.isBlank() ? "siliconflow" : provider;
        this.model = model == null || model.isBlank() ? "BAAI/bge-reranker-v2-m3" : model;
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "https://api.siliconflow.cn/v1" : baseUrl)
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
    public List<KnowledgeAdapters.RerankResult> rerank(String query, List<String> documents, int topN) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("rerank_api_key_missing", "Rerank API key is not configured");
        }
        Map<String, Object> response = restClient.post()
                .uri("/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of(
                        "model", model,
                        "query", query,
                        "documents", documents,
                        "top_n", Math.max(1, Math.min(topN, documents.size()))))
                .retrieve()
                .body(Map.class);
        Object results = response == null ? null : response.get("results");
        if (!(results instanceof List<?> items)) {
            throw new BusinessException("rerank_response_invalid", "Rerank response is invalid");
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(item -> result((Map<String, Object>) item, documents))
                .toList();
    }

    private KnowledgeAdapters.RerankResult result(Map<String, Object> item, List<String> documents) {
        int index = number(item.get("index")).intValue();
        double score = item.containsKey("relevance_score")
                ? number(item.get("relevance_score")).doubleValue()
                : number(item.get("score")).doubleValue();
        return new KnowledgeAdapters.RerankResult(index, score, documentText(item.get("document"), documents, index));
    }

    private String documentText(Object value, List<String> documents, int index) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map && map.get("text") != null) {
            return String.valueOf(map.get("text"));
        }
        return index >= 0 && index < documents.size() ? documents.get(index) : "";
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
