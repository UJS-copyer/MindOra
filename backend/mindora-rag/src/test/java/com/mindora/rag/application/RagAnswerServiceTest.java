package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalHit;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalOutcome;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalQuery;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.ChatReply;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import com.mindora.rag.domain.RagDomain.RefusalReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 编排服务单测：三条降级/拒答语义（无候选直接拒答且不调模型、模型不可用降级、
 * 引用校验失败拒答）与参数校验。
 */
class RagAnswerServiceTest {

    private static final UUID VERSION = UUID.randomUUID();
    private static final String DOC_ID = PublicIds.newId();

    private static KnowledgeChunk chunk(int sequence, String heading, String content) {
        return new KnowledgeChunk(
                UUID.randomUUID(), VERSION, sequence, content, content.length(),
                heading, 2, KnowledgeEnums.BlockType.PARAGRAPH, null, "hash",
                KnowledgeEnums.IndexStatus.INDEXED, "model", "point", null, 0, Instant.now());
    }

    private static RetrievalHit hit(KnowledgeChunk chunk, int rank) {
        return new RetrievalHit(
                chunk.id().toString(), DOC_ID, "Redis 笔记", "gitee", "工作/redis.md",
                chunk.headingPath(), "paragraph", null, 0.9d, 0.8d, 0.03d, rank, rank, "both",
                chunk.content().substring(0, Math.min(30, chunk.content().length())), null, null);
    }

    /** {@code hitCount} 控制前多少个切片作为检索命中，其余只能通过扩展进入上下文。 */
    private static RetrievalFacadePort facadeOf(List<KnowledgeChunk> chunks, int hitCount) {
        return new RetrievalFacadePort() {
            @Override
            public RetrievalOutcome search(RetrievalQuery query) {
                List<RetrievalHit> hits = new ArrayList<>();
                for (int index = 0; index < Math.min(hitCount, chunks.size()); index++) {
                    hits.add(hit(chunks.get(index), index + 1));
                }
                return new RetrievalOutcome(
                        "all", 30, 30, 20, 8, true, true, 30, 30, false, null, false, null, 11L, hits);
            }

            @Override
            public Map<String, KnowledgeChunk> chunksOf(List<String> documentIds) {
                Map<String, KnowledgeChunk> all = new LinkedHashMap<>();
                for (KnowledgeChunk chunk : chunks) {
                    all.put(RetrievalFacadePort.normalizeId(PublicIds.toPublicId(chunk.id())), chunk);
                }
                return all;
            }
        };
    }

    private static ChatClientPort chatReturning(String content) {
        return new ChatClientPort() {
            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String model() {
                return "test-model";
            }

            @Override
            public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
                return new ChatReply(content, "test-model", "stop");
            }
        };
    }

    private static ChatClientPort chatFailing(String code, String message) {
        return new ChatClientPort() {
            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String model() {
                return "test-model";
            }

            @Override
            public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
                throw new BusinessException(code, message);
            }
        };
    }

    private static RagAnswerService service(
            RetrievalFacadePort facade, ChatClientPort chatClient, ContextPolicy policy) {
        return new RagAnswerService(
                facade,
                new ContextAssembler(facade),
                chatClient,
                new AnswerCitationValidator(),
                policy,
                ChatOptions.defaults());
    }

    private static RagAnswerRequest request(String query) {
        return new RagAnswerRequest(query, "all", 8, true, true, null, null, false);
    }

    @Test
    void 无候选时直接拒答且不调用模型() {
        boolean[] chatCalled = {false};
        ChatClientPort spy = new ChatClientPort() {
            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String model() {
                return "test-model";
            }

            @Override
            public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
                chatCalled[0] = true;
                return new ChatReply("x", "test-model", "stop");
            }
        };
        RagAnswerService service = service(facadeOf(List.of(), 0), spy, ContextPolicy.defaults());

        var outcome = service.answer(request("知识库里没有的问题"));

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.NO_CONTEXT);
        assertThat(outcome.citations()).isEmpty();
        assertThat(chatCalled[0]).isFalse();
    }

    @Test
    void 正常路径返回答案与可定位引用() {
        KnowledgeChunk target = chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。");
        ChatClientPort chat = chatReturning("""
                {"refused":false,"answer":"用布隆过滤器[1]","citations":[{"index":1,"quote":"Redis 缓存穿透的解决方案是布隆过滤器。"}]}
                """);
        RagAnswerService service = service(
                facadeOf(List.of(target), 1), chat, new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存穿透怎么解决？"));

        assertThat(outcome.refused()).isFalse();
        assertThat(outcome.degraded()).isFalse();
        assertThat(outcome.answer()).isEqualTo("用布隆过滤器[1]");
        assertThat(outcome.citations()).hasSize(1);
        var citation = outcome.citations().get(0);
        assertThat(citation.chunkId()).isEqualTo(PublicIds.toPublicId(target.id()));
        assertThat(citation.documentId()).isEqualTo(DOC_ID);
        assertThat(citation.documentTitle()).isEqualTo("Redis 笔记");
        assertThat(citation.sequence()).isEqualTo(1);
        assertThat(outcome.retrieval().matchedTopK()).isEqualTo(1);
    }

    @Test
    void 引用不可核验时按没有记录拒答() {
        KnowledgeChunk target = chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。");
        ChatClientPort chat = chatReturning("""
                {"refused":false,"answer":"用布隆过滤器[1]","citations":[{"index":1,"quote":"模型自己编的一段话"}]}
                """);
        RagAnswerService service = service(
                facadeOf(List.of(target), 1), chat, new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存穿透怎么解决？"));

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.INVALID_CITATION);
        assertThat(outcome.refusalDetail()).startsWith("citations_not_verifiable");
        assertThat(outcome.citations()).isEmpty();
    }

    @Test
    void 部分引用不可核验时保留可核验的引用而不是整体拒答() {
        List<KnowledgeChunk> chunks = List.of(
                chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。"),
                chunk(2, "缓存雪崩", "缓存雪崩可以给过期时间加随机值。"));
        ChatClientPort chat = chatReturning("""
                {"refused":false,"answer":"布隆过滤器[1]，雪崩加随机过期时间[2]","citations":[{"index":1,"quote":"模型自己编的一段话"},{"index":2,"quote":"缓存雪崩可以给过期时间加随机值。"}]}
                """);
        RagAnswerService service = service(
                facadeOf(chunks, 2), chat, new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存问题怎么解决？"));

        assertThat(outcome.refused()).isFalse();
        assertThat(outcome.citations()).hasSize(1);
        assertThat(outcome.citations().get(0).sequence()).isEqualTo(2);
        assertThat(outcome.citations().get(0).quoteVerified()).isTrue();
    }

    @Test
    void 模型不可用时降级为只给检索片段而不是抛错() {
        KnowledgeChunk target = chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。");
        RagAnswerService service = service(
                facadeOf(List.of(target), 1),
                chatFailing("rag_chat_unavailable", "upstream down"),
                new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存穿透怎么解决？"));

        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.degradedReason()).isEqualTo("rag_chat_unavailable");
        assertThat(outcome.refused()).isFalse();
        assertThat(outcome.answer()).contains("生成模型暂不可用");
        assertThat(outcome.citations()).hasSize(1);
    }

    @Test
    void 密钥未配置也走降级路径() {
        KnowledgeChunk target = chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。");
        RagAnswerService service = service(
                facadeOf(List.of(target), 1),
                chatFailing("rag_chat_api_key_missing", "no key"),
                new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存穿透怎么解决？"));

        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.degradedReason()).isEqualTo("rag_chat_api_key_missing");
    }

    @Test
    void 模型显式拒答时按模型拒答归类而不是引用不达标() {
        KnowledgeChunk target = chunk(1, "缓存穿透", "Redis 缓存穿透的解决方案是布隆过滤器。");
        RagAnswerService service = service(
                facadeOf(List.of(target), 1),
                chatReturning("{\"refused\":true,\"answer\":\"\",\"citations\":[]}"),
                new ContextPolicy(4000, false, 1500, 4));

        var outcome = service.answer(request("Redis 缓存穿透怎么解决？"));

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.MODEL_REFUSED);
        assertThat(outcome.refusalDetail()).isEqualTo("model_refused");
    }

    @Test
    void 扩展开关可被请求覆盖并体现在元数据() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 3; sequence++) {
            chunks.add(chunk(sequence, "章节", "第" + sequence + "段内容足够长用于占位。"));
        }
        ChatClientPort chat = chatReturning(
                "{\"refused\":true,\"answer\":\"\",\"citations\":[]}");
        RagAnswerService service = service(
                facadeOf(chunks, 1), chat, new ContextPolicy(4000, true, 1500, 0));

        var withExpansion = service.answer(new RagAnswerRequest("问题", "all", 8, true, true, true, null, false));
        var withoutExpansion = service.answer(new RagAnswerRequest("问题", "all", 8, true, true, false, null, false));

        assertThat(withExpansion.retrieval().expansionApplied()).isTrue();
        assertThat(withoutExpansion.retrieval().expansionApplied()).isFalse();
        assertThat(withExpansion.retrieval().matchedTopK())
                .isGreaterThan(withoutExpansion.retrieval().matchedTopK());
    }

    @Test
    void 空问题与非法scope被拒绝() {
        RagAnswerService service = service(
                facadeOf(List.of(chunk(1, "章节", "内容")), 1),
                chatReturning("{}"),
                ContextPolicy.defaults());

        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("  ", "all", 8, true, true, null, null, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_query_required"));
        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("问题", null, 8, true, true, null, null, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_scope_required"));
        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("问题", "world", 8, true, true, null, null, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_scope_invalid"));
    }

    @Test
    void 上下文含密钥时拒绝作答且不调用模型() {
        boolean[] chatCalled = {false};
        ChatClientPort spy = new ChatClientPort() {
            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String model() {
                return "test-model";
            }

            @Override
            public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
                chatCalled[0] = true;
                return new ChatReply("{\"refused\":false,\"answer\":\"ok\",\"citations\":[]}", "test-model", "stop");
            }
        };
        List<KnowledgeChunk> chunks = List.of(
                chunk(1, "章节", "部署配置：sk-abcdefghijklmnopqrstuvwxyz0123456789"));
        RagAnswerService service = serviceWithGuard(facadeOf(chunks, 1), spy, ContextPolicy.defaults());

        RagAnswerOutcome outcome = service.answer(
                new RagAnswerRequest("部署配置是什么", "public", 8, true, true, null, null, true));

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.PROTECTED_CONTENT);
        assertThat(outcome.refusalDetail()).startsWith("secret_guard:context:");
        assertThat(chatCalled[0]).as("上下文命中密钥时不应把密钥送进 prompt").isFalse();
    }

    @Test
    void 降级路径的引用摘录含密钥时同样拒答() {
        // 用赋值型（上下文层不拦），验证答案层对「服务端摘录」的覆盖
        List<KnowledgeChunk> chunks = List.of(
                chunk(1, "章节", "配置说明：api_key = abcdefghij0123456789xyz 用于内部调用。"));
        RagAnswerService service = serviceWithGuard(
                facadeOf(chunks, 1), chatFailing("rag_chat_unavailable", "down"), ContextPolicy.defaults());

        RagAnswerOutcome outcome = service.answer(
                new RagAnswerRequest("配置说明", "public", 8, true, true, null, null, true));

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.PROTECTED_CONTENT);
        assertThat(outcome.refusalDetail()).startsWith("secret_guard:answer:");
    }

    @Test
    void 管理端不启用兜底时同一内容可正常返回降级片段() {
        List<KnowledgeChunk> chunks = List.of(
                chunk(1, "章节", "配置说明：api_key = abcdefghij0123456789xyz 用于内部调用。"));
        RagAnswerService service = serviceWithGuard(
                facadeOf(chunks, 1), chatFailing("rag_chat_unavailable", "down"), ContextPolicy.defaults());

        RagAnswerOutcome outcome = service.answer(
                new RagAnswerRequest("配置说明", "all", 8, true, true, null, null, false));

        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.refused()).isFalse();
    }

    private static RagAnswerService serviceWithGuard(
            RetrievalFacadePort facade, ChatClientPort chatClient, ContextPolicy policy) {
        return new RagAnswerService(
                facade,
                new ContextAssembler(facade),
                chatClient,
                new AnswerCitationValidator(),
                policy,
                ChatOptions.defaults(),
                true);
    }

    // ---------- 流式（answerStream） ----------

    /** 假模型：把 JSON 信封按给定分段推给 streamConsumer，并返回完整信封。 */
    private static ChatClientPort chatStreaming(String... pieces) {
        return new ChatClientPort() {
            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String model() {
                return "test-model";
            }

            @Override
            public ChatReply chat(List<ChatMessage> messages, ChatOptions options) {
                // 同步路径（streamConsumer 为空）只返回完整信封，不推送增量
                if (options.streamConsumer() != null) {
                    for (String piece : pieces) {
                        options.streamConsumer().accept(piece);
                    }
                }
                return new ChatReply(String.join("", pieces), "test-model", "stop");
            }
        };
    }

    @Test
    void 流式转发answer正文且产出与同步路径一致() {
        List<KnowledgeChunk> chunks = List.of(chunk(1, "章节", "向量检索与关键词检索通过 RRF 融合。"));
        RagAnswerService service = service(
                facadeOf(chunks, 1),
                chatStreaming(
                        "{\"refused\":false,\"answer\":\"向量检索",
                        "与关键词检索通过 RRF 融合。\",\"citations\":[{\"index\":1,\"quote\":\"向量检索与关键词检索通过 RRF 融合。\"}]}"),
                ContextPolicy.defaults());

        List<String> deltas = new ArrayList<>();
        RagAnswerOutcome streamed = service.answerStream(request("检索是怎么做的"), deltas::add);

        assertThat(String.join("", deltas)).isEqualTo("向量检索与关键词检索通过 RRF 融合。");
        assertThat(streamed.refused()).isFalse();
        assertThat(streamed.answer()).isEqualTo("向量检索与关键词检索通过 RRF 融合。");
        assertThat(streamed.citations()).hasSize(1);
        // 与同步路径结果一致（同一条编排代码）
        RagAnswerOutcome sync = service.answer(request("检索是怎么做的"));
        assertThat(sync.answer()).isEqualTo(streamed.answer());
    }

    @Test
    void 流式答案含密钥则改判拒答且密钥不转发() {
        List<KnowledgeChunk> chunks = List.of(chunk(1, "章节", "普通内容，足够长用于组装上下文。"));
        RagAnswerService service = serviceWithGuard(
                facadeOf(chunks, 1),
                chatStreaming("{\"refused\":false,\"answer\":\"配置是 sk-abcdefghijklmnopqrstuvwxyz0123456789\",\"citations\":[]}"),
                ContextPolicy.defaults());

        List<String> deltas = new ArrayList<>();
        RagAnswerOutcome outcome = service.answerStream(
                new RagAnswerRequest("配置", "public", 8, true, true, null, null, true), deltas::add);

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalReason()).isEqualTo(RefusalReason.PROTECTED_CONTENT);
        assertThat(outcome.refusalDetail()).startsWith("secret_guard:answer:");
        assertThat(String.join("", deltas)).as("密钥绝不能出现在已转发的文本里").doesNotContain("sk-");
    }

    @Test
    void 流式密钥被delta切断同样命中() {
        List<KnowledgeChunk> chunks = List.of(chunk(1, "章节", "普通内容，足够长用于组装上下文。"));
        RagAnswerService service = serviceWithGuard(
                facadeOf(chunks, 1),
                chatStreaming(
                        "{\"refused\":false,\"answer\":\"配置是 sk-abcdefghij",
                        "klmnopqrstuvwxyz0123456789\",\"citations\":[]}"),
                ContextPolicy.defaults());

        List<String> deltas = new ArrayList<>();
        RagAnswerOutcome outcome = service.answerStream(
                new RagAnswerRequest("配置", "public", 8, true, true, null, null, true), deltas::add);

        assertThat(outcome.refused()).isTrue();
        assertThat(outcome.refusalDetail()).startsWith("secret_guard:answer:");
        assertThat(String.join("", deltas)).doesNotContain("sk-");
    }

    @Test
    void 流式消费端异常原样上抛() {
        List<KnowledgeChunk> chunks = List.of(chunk(1, "章节", "内容足够长用于组装上下文。"));
        RagAnswerService service = service(
                facadeOf(chunks, 1),
                chatStreaming("{\"refused\":false,\"answer\":\"正文\",\"citations\":[]}"),
                ContextPolicy.defaults());
        IllegalStateException clientGone = new IllegalStateException("client disconnected");

        // 客户端断开必须原样上抛，让适配器据此中断上游读取
        assertThatThrownBy(() -> service.answerStream(request("问题"), text -> {
            throw clientGone;
        })).isSameAs(clientGone);
    }
}
