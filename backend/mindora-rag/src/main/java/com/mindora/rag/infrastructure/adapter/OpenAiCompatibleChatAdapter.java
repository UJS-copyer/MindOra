package com.mindora.rag.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.ChatReply;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 兼容对话补全适配器（POST /chat/completions）。
 *
 * <p>与 knowledge 的 OpenAiCompatibleEmbeddingAdapter 同风格：RestClient + BusinessException；
 * 密钥缺失或上游不可用时抛 {@code rag_chat_unavailable}，由 RagAnswerService 决定降级。
 *
 * <p><b>流式</b>：{@link ChatOptions#streamConsumer()} 非空时请求带 {@code stream: true}，
 * 逐帧把 {@code choices[0].delta.content} 推给回调，同时在本地累积完整文本并以
 * {@link ChatReply#content()} 返回——<b>端口签名不变</b>（兑现 ChatOptions 里预留的槽位设计）。
 * 完整累积文本随后交给既有 {@code AnswerCitationValidator}，模型输出协议无需改动。
 */
public class OpenAiCompatibleChatAdapter implements ChatClientPort {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        Consumer<String> streamConsumer = options == null ? null : options.streamConsumer();
        boolean streaming = streamConsumer != null;
        Map<String, Object> requestBody = Map.of(
                "model", effectiveModel,
                "messages", payloadMessages,
                "temperature", options == null ? 0.2d : options.temperature(),
                "max_tokens", options == null ? 2048 : options.maxTokens(),
                "stream", streaming);
        if (streaming) {
            return chatStreaming(requestBody, effectiveModel, streamConsumer);
        }
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

    /**
     * 流式补全：逐行读 SSE 帧（{@code data: {...}}），累积并转发 {@code delta.content}。
     *
     * <p><b>消费端异常 = 唯一的中断机制</b>：回调抛出的异常（客户端断开导致的写失败、密钥兜底熔断）
     * 会被包装成 {@link StreamConsumerAbort} 冒泡出读取循环，try-with-resources 随即关闭响应流——
     * 即「停止读取上游、断开连接」。这条路径必须原样上抛，不能当成上游故障。
     */
    private ChatReply chatStreaming(
            Map<String, Object> requestBody, String effectiveModel, Consumer<String> streamConsumer) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(
                                    "rag_chat_unavailable",
                                    "RAG chat service returned " + response.getStatusCode());
                        }
                        StreamState state = new StreamState(effectiveModel);
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue; // 空行分隔符、注释与 event: 行都跳过
                                }
                                String payload = line.substring(5).trim();
                                if (payload.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(payload)) {
                                    break;
                                }
                                consumeChunk(payload, state, streamConsumer);
                            }
                        }
                        return new ChatReply(state.content.toString(), state.model, state.finishReason);
                    });
        } catch (StreamConsumerAbort abort) {
            throw abort.cause();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "rag_chat_unavailable",
                    "RAG chat service is temporarily unavailable: " + exception.getMessage());
        }
    }

    /** 解析单个 SSE 数据帧：跳过只有 role 的帧，转发并累积 content，记录 model/finish_reason。 */
    private void consumeChunk(String payload, StreamState state, Consumer<String> streamConsumer) {
        JsonNode node;
        try {
            node = MAPPER.readTree(payload);
        } catch (IOException exception) {
            throw new BusinessException("rag_chat_response_invalid", "RAG chat stream chunk is invalid");
        }
        JsonNode choices = node.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }
        JsonNode modelNode = node.path("model");
        if (!modelNode.isMissingNode() && !modelNode.isNull() && !modelNode.asText().isBlank()) {
            state.model = modelNode.asText();
        }
        JsonNode choice = choices.get(0);
        JsonNode finish = choice.path("finish_reason");
        if (!finish.isMissingNode() && !finish.isNull()) {
            state.finishReason = finish.asText();
        }
        JsonNode delta = choice.path("delta").path("content");
        if (delta.isMissingNode() || delta.isNull()) {
            return;
        }
        String piece = delta.asText();
        if (piece.isEmpty()) {
            return;
        }
        state.content.append(piece);
        try {
            streamConsumer.accept(piece);
        } catch (RuntimeException exception) {
            throw new StreamConsumerAbort(exception);
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

    /** 流式累积状态（lambda 内需要可变容器）。 */
    private static final class StreamState {
        private final StringBuilder content = new StringBuilder();
        private String model;
        private String finishReason;

        private StreamState(String model) {
            this.model = model;
        }
    }

    /** 把消费端异常包装成可穿透 exchange lambda 的信号，交由调用方原样上抛。 */
    private static final class StreamConsumerAbort extends RuntimeException {
        private final transient RuntimeException cause;

        private StreamConsumerAbort(RuntimeException cause) {
            super(cause);
            this.cause = cause;
        }

        private RuntimeException cause() {
            return cause;
        }
    }
}
