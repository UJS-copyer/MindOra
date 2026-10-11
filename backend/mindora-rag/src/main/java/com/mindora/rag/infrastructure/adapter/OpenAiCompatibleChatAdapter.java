package com.mindora.rag.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.ChatReply;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 兼容对话补全适配器（POST /chat/completions）。
 *
 * <p>与 knowledge 的 OpenAiCompatibleEmbeddingAdapter 同风格：RestClient + BusinessException；
 * 密钥缺失或上游不可用时抛 {@code rag_chat_unavailable}，由 RagAnswerService 决定降级。
 */
public class OpenAiCompatibleChatAdapter implements ChatClientPort {
    private final RestClient restClient;
    private final String provider;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleChatAdapter(String provider, String baseUrl, String apiKey, String model) {
        this(provider, baseUrl, apiKey, model, 15000);
    }

    public OpenAiCompatibleChatAdapter(
            String provider, String baseUrl, String apiKey, String model, int timeoutMillis) {
        this.provider = provider == null || provider.isBlank() ? "siliconflow" : provider;
        this.model = model == null || model.isBlank() ? "Qwen/Qwen3-30B-A3B-Instruct-2507" : model;
        this.apiKey = apiKey;
        this.restClient = newRestClientBuilder(
                RagHttpFactories.requireBaseUrl(baseUrl, "https://api.siliconflow.cn/v1"),
                timeoutMillis).build();
    }

    /**
     * 构造 RestClient 的构建器（含超时配置）。独立成方法是为了让单测可以覆盖它并绑定
     * MockRestServiceServer（否则无法在不访问真实上游的情况下校验请求结构）。
     */
    RestClient.Builder newRestClientBuilder(String effectiveBaseUrl, int timeoutMillis) {
        return RestClient.builder()
                .baseUrl(effectiveBaseUrl)
                .requestFactory(RagHttpFactories.withTimeout(timeoutMillis));
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
    public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("rag_chat_api_key_missing", "RAG chat API key is not configured");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException("rag_chat_messages_required", "Chat messages are required");
        }
        String effectiveModel = options != null && options.model() != null && !options.model().isBlank()
                ? options.model()
                : model;
        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            payloadMessages.add(Map.of(
                    "role", message.role() == null ? "user" : message.role(),
                    "content", message.content() == null ? "" : message.content()));
        }
        Map<String, Object> requestBody = Map.of(
                "model", effectiveModel,
                "messages", payloadMessages,
                "temperature", options == null ? 0.2d : options.temperature(),
                "max_tokens", options == null ? 2048 : options.maxTokens(),
                "stream", false);
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            return toReply(response, effectiveModel);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "rag_chat_unavailable",
                    "RAG chat service is temporarily unavailable: " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ChatReply toReply(Map<String, Object> response, String effectiveModel) {
        Object choices = response == null ? null : response.get("choices");
        if (!(choices instanceof List<?> items) || items.isEmpty()) {
            throw new BusinessException("rag_chat_response_invalid", "RAG chat response is invalid");
        }
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null || String.valueOf(content).isBlank()) {
            throw new BusinessException("rag_chat_response_invalid", "RAG chat response content is empty");
        }
        Object finishReason = first.get("finish_reason");
        return new ChatReply(String.valueOf(content), effectiveModel, finishReason == null ? null : String.valueOf(finishReason));
    }
}
