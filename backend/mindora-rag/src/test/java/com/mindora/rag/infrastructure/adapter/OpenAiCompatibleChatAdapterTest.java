package com.mindora.rag.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 用 MockRestServiceServer 校验 OpenAI 兼容对话适配器的请求体结构与响应解析。
 * 不访问真实上游，测试完全离线。
 */
class OpenAiCompatibleChatAdapterTest {

    private static final String BASE_URL = "https://chat.example.com/v1";

    private MockRestServiceServer server;

    private OpenAiCompatibleChatAdapter adapterWith(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiCompatibleChatAdapter("test-provider", BASE_URL, apiKey, "test-model") {
            @Override
            RestClient.Builder newRestClientBuilder(String effectiveBaseUrl, int timeoutMillis) {
                return builder;
            }
        };
    }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesReply() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
            assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getURI().getPath()).isEqualTo("/v1/chat/completions");
            assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
        }).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"答案[1]\"},\"finish_reason\":\"stop\"}]}"));

        var reply = adapter.chat(
                List.of(ChatMessage.system("s"), ChatMessage.user("q")),
                ChatOptions.defaults());

        assertThat(reply.content()).isEqualTo("答案[1]");
        assertThat(reply.model()).isEqualTo("test-model");
        assertThat(reply.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void rejectsBlankApiKeyBeforeSending() {
        OpenAiCompatibleChatAdapter noKey = adapterWith(" ");
        assertThatThrownBy(() -> noKey.chat(List.of(ChatMessage.user("q")), ChatOptions.defaults()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_api_key_missing"));
    }

    @Test
    void rejectsEmptyMessages() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        assertThatThrownBy(() -> adapter.chat(List.of(), ChatOptions.defaults()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_messages_required"));
    }

    @Test
    void wrapsUpstreamFailureAsUnavailable() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withServerError());
        assertThatThrownBy(() -> adapter.chat(List.of(ChatMessage.user("q")), ChatOptions.defaults()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_unavailable"));
    }

    @Test
    void rejectsResponseWithoutChoices() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withSuccess("{\"object\":\"list\"}"));
        assertThatThrownBy(() -> adapter.chat(List.of(ChatMessage.user("q")), ChatOptions.defaults()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_response_invalid"));
    }

    @Test
    void rejectsResponseWithBlankContent() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  \"},\"finish_reason\":\"stop\"}]}"));
        assertThatThrownBy(() -> adapter.chat(List.of(ChatMessage.user("q")), ChatOptions.defaults()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_response_invalid"));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator withSuccess(String body) {
        return org.springframework.test.web.client.response.MockRestResponseCreators
                .withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator withServerError() {
        return org.springframework.test.web.client.response.MockRestResponseCreators.withServerError();
    }
}
