package com.mindora.knowledge.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * 验证外部模型适配器的超时确实生效：当 provider 响应时间超过配置超时时，
 * 调用必须快速失败（抛出 {@link ResourceAccessException}），而不是无限等待。
 *
 * <p>使用本机环回地址的慢响应服务，不依赖任何外部网络。
 */
class ModelAdapterTimeoutTest {

    private static final int TIMEOUT_MILLIS = 400;
    private static final long SERVER_DELAY_MILLIS = 1500;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String startSlowServer(String path) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(SERVER_DELAY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void rerankFailsFastWhenProviderExceedsTimeout() throws Exception {
        String baseUrl = startSlowServer("/rerank");
        SiliconFlowRerankAdapter adapter =
                new SiliconFlowRerankAdapter("siliconflow", baseUrl, "test-key", "test-model", TIMEOUT_MILLIS);

        assertThatThrownBy(() -> adapter.rerank("query", List.of("document"), 1))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void embeddingFailsFastWhenProviderExceedsTimeout() throws Exception {
        String baseUrl = startSlowServer("/embeddings");
        OpenAiCompatibleEmbeddingAdapter adapter =
                new OpenAiCompatibleEmbeddingAdapter("bailian", baseUrl, "test-key", "test-model", 8, TIMEOUT_MILLIS);

        assertThatThrownBy(() -> adapter.embed(List.of("hello")))
                .isInstanceOf(ResourceAccessException.class);
    }
}
