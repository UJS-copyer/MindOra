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
        return new RagAnswerRequest(query, "all", 8, true, true, null, null);
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

        var withExpansion = service.answer(new RagAnswerRequest("问题", "all", 8, true, true, true, null));
        var withoutExpansion = service.answer(new RagAnswerRequest("问题", "all", 8, true, true, false, null));

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

        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("  ", "all", 8, true, true, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_query_required"));
        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("问题", null, 8, true, true, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_scope_required"));
        assertThatThrownBy(() -> service.answer(new RagAnswerRequest("问题", "world", 8, true, true, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("rag_scope_invalid"));
    }
}
