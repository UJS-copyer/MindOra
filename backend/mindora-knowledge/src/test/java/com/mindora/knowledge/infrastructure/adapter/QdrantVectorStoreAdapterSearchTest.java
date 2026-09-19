package com.mindora.knowledge.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.application.port.KnowledgeAdapters;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 校验向量检索的过滤条件翻译与响应解析，使用本机环回桩服务，不依赖真实 Qdrant。
 */
class QdrantVectorStoreAdapterSearchTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void translatesExactMatchFilterToQdrantSyntax() {
        assertThat(QdrantVectorStoreAdapter.qdrantFilter(null)).isEmpty();
        assertThat(QdrantVectorStoreAdapter.qdrantFilter(Map.of())).isEmpty();

        Map<String, Object> filter = QdrantVectorStoreAdapter.qdrantFilter(Map.of("visibility", "public"));

        assertThat(filter).containsOnlyKeys("must");
        assertThat((List<?>) filter.get("must")).hasSize(1);
    }

    @Test
    void skipsNullFilterValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("visibility", "public");
        source.put("articleId", null);

        Map<String, Object> filter = QdrantVectorStoreAdapter.qdrantFilter(source);

        assertThat((List<?>) filter.get("must")).hasSize(1);
    }

    @Test
    void searchesWithFilterAndParsesHits() throws Exception {
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AtomicReference<String> searchBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, """
                        {"result":{"config":{"params":{"vectors":{"dense":{"size":1024,"distance":"Cosine"}}}}}}""");
                return;
            }
            searchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"result\":[{\"id\":\"" + chunkId + "\",\"score\":0.87,\"payload\":{"
                    + "\"chunkId\":\"" + chunkId + "\","
                    + "\"documentId\":\"" + documentId + "\","
                    + "\"versionId\":\"" + versionId + "\","
                    + "\"visibility\":\"public\",\"headingPath\":\"A > B\"}}]}");
        });
        server.start();

        QdrantVectorStoreAdapter adapter = new QdrantVectorStoreAdapter(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", "mindora_chunk");

        List<KnowledgeAdapters.VectorHit> hits =
                adapter.search(List.of(0.1, 0.2), 5, Map.of("visibility", "public", "knowledgeEnabled", true));

        assertThat(hits).hasSize(1);
        KnowledgeAdapters.VectorHit hit = hits.getFirst();
        assertThat(hit.chunkId()).isEqualTo(chunkId);
        assertThat(hit.documentId()).isEqualTo(documentId);
        assertThat(hit.versionId()).isEqualTo(versionId);
        assertThat(hit.score()).isEqualTo(0.87);
        assertThat(hit.payload()).containsEntry("headingPath", "A > B");

        String request = searchBody.get();
        assertThat(request).contains("\"limit\":5");
        assertThat(request).contains("\"with_payload\":true");
        assertThat(request).contains("\"key\":\"visibility\"");
        assertThat(request).contains("\"match\":{\"value\":\"public\"}");
        assertThat(request).contains("\"key\":\"knowledgeEnabled\"");
        assertThat(request).contains("\"match\":{\"value\":true}");
        // 命名向量在检索接口需用 NamedVectorStruct 的 {"name":..., "vector":[...]} 形式
        assertThat(request).contains("\"name\":\"dense\"");
        assertThat(request).contains("\"vector\":[0.1,0.2]");
    }

    @Test
    void skipsHitsWithIncompletePayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, """
                        {"result":{"config":{"params":{"vectors":{"dense":{"size":1024,"distance":"Cosine"}}}}}}""");
                return;
            }
            respond(exchange, "{\"result\":[{\"id\":\"x\",\"score\":0.5,\"payload\":{\"chunkId\":\"not-a-uuid\"}}]}");
        });
        server.start();

        QdrantVectorStoreAdapter adapter = new QdrantVectorStoreAdapter(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", "mindora_chunk");

        assertThat(adapter.search(List.of(0.1), 3, Map.of())).isEmpty();
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
