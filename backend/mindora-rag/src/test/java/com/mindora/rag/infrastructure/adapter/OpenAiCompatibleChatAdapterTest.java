package com.mindora.rag.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import java.util.ArrayList;
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

    /** 流式响应用 text/event-stream。 */
    private static org.springframework.test.web.client.response.DefaultResponseCreator withSuccess(
            String body, MediaType contentType) {
        return org.springframework.test.web.client.response.MockRestResponseCreators
                .withSuccess(body, contentType);
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator withServerError() {
        return org.springframework.test.web.client.response.MockRestResponseCreators.withServerError();
    }

    // ---------- 流式（streamConsumer 非空） ----------

    @Test
    void 流式请求带stream标志并逐帧转发且累积完整文本() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
            String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
            assertThat(body).contains("\"stream\":true");
        }).andRespond(withSuccess("""
                data: {"model":"stream-model","choices":[{"delta":{"role":"assistant"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"答案"}}]}

                data: {"choices":[{"delta":{"content":"[1]"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """, MediaType.TEXT_EVENT_STREAM));

        List<String> pieces = new ArrayList<>();
        var reply = adapter.chat(
                List.of(ChatMessage.user("q")),
                new ChatOptions(null, 0.2d, 256, pieces::add));

        // 只有 role 的首帧被跳过；内容按顺序转发
        assertThat(pieces).containsExactly("答案", "[1]");
        assertThat(reply.content()).isEqualTo("答案[1]");
        assertThat(reply.model()).isEqualTo("stream-model");
        assertThat(reply.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void 流式请求忽略DONE之后的帧() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withSuccess("""
                data: {"choices":[{"delta":{"content":"a"}}]}

                data: [DONE]

                data: {"choices":[{"delta":{"content":"should-be-ignored"}}]}

                """, MediaType.TEXT_EVENT_STREAM));

        List<String> pieces = new ArrayList<>();
        var reply = adapter.chat(
                List.of(ChatMessage.user("q")),
                new ChatOptions(null, 0.2d, 256, pieces::add));

        assertThat(pieces).containsExactly("a");
        assertThat(reply.content()).isEqualTo("a");
    }

    @Test
    void 消费端抛异常时中断读取并原样上抛() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withSuccess("""
                data: {"choices":[{"delta":{"content":"第一段"}}]}

                data: {"choices":[{"delta":{"content":"第二段"}}]}

                data: [DONE]

                """, MediaType.TEXT_EVENT_STREAM));

        IllegalStateException clientGone = new IllegalStateException("client disconnected");
        List<String> pieces = new ArrayList<>();

        // 客户端断开 / 密钥熔断都走这条路径：必须是原始异常，不能被包装成上游不可用
        assertThatThrownBy(() -> adapter.chat(
                List.of(ChatMessage.user("q")),
                new ChatOptions(null, 0.2d, 256, piece -> {
                    pieces.add(piece);
                    throw clientGone;
                })))
                .isSameAs(clientGone);

        assertThat(pieces).as("首段抛出后不应继续读取后续帧").hasSize(1);
    }

    @Test
    void 流式上游报错时包装为不可用() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.chat(
                List.of(ChatMessage.user("q")),
                new ChatOptions(null, 0.2d, 256, piece -> {
                })))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_unavailable"));
    }

    @Test
    void 流式帧内容非法时按响应无效处理() {
        OpenAiCompatibleChatAdapter adapter = adapterWith("test-key");
        server.expect(request -> {
        }).andRespond(withSuccess("""
                data: {not-json}

                """, MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> adapter.chat(
                List.of(ChatMessage.user("q")),
                new ChatOptions(null, 0.2d, 256, piece -> {
                })))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_chat_response_invalid"));
    }
}
