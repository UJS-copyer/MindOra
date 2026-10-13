package com.mindora.rag.application.port;

import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalOutcome;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalQuery;
import com.mindora.knowledge.domain.KnowledgeChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 模块对外部能力的端口集合。
 *
 * <p>设计动机（见 docs/stage-3.3-rag-generation-layer-plan-cn.md 第三节）：把「调用 mindora-knowledge」
 * 收敛为一个窄端口，RAG 的应用层不直接依赖 knowledge 的服务类型，将来替换检索实现、加缓存或加查询改写时，
 * {@link RagAnswerService} 不需要改动。
 */
public final class RagAdapters {
    private RagAdapters() {
    }

    /** 聊天消息（system / user / assistant）。 */
    public record ChatMessage(String role, String content) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /**
     * 聊天调用选项。
     *
     * <p>{@code streamConsumer} 是为 SSE 流式输出预留的回调槽（计划提交 1 的扩展点）：
     * 今天的最小实现传 {@code null} 走同步调用；将来接 SSE 只需要装配处传入回调，
     * {@link ChatClientPort} 的签名不变。
     */
    public record ChatOptions(
            String model,
            double temperature,
            int maxTokens,
            java.util.function.Consumer<String> streamConsumer) {

        public ChatOptions {
            temperature = temperature <= 0 ? 0.2d : Math.min(temperature, 2.0d);
            maxTokens = maxTokens <= 0 ? 2048 : maxTokens;
        }

        public static ChatOptions defaults() {
            return new ChatOptions(null, 0.2d, 2048, null);
        }
    }

    /** 聊天回复。 */
    public record ChatReply(String content, String model, String finishReason) {
    }

    /**
     * 对话式大模型端口。
     *
     * <p>语义与 knowledge 的 EmbeddingProviderPort / RerankProviderPort 一致：能力缺失时抛
     * {@code BusinessException("rag_chat_unavailable", ...)}，由上层决定降级还是报错。
     */
    public interface ChatClientPort {
        String provider();

        String model();

        /**
         * 同步对话补全。
         *
         * @param messages 按角色排列的消息（system 在前）
         * @param options  生成选项；模型名缺省时使用端口自身配置
         */
        ChatReply chat(List<ChatMessage> messages, ChatOptions options);
    }

    /**
     * 检索门面端口：对 mindora-knowledge 检索能力的最小封装。
     *
     * <p>RAG 侧只需要两件事：执行一次检索、把命中补全为全文切片。
     * 预览字段（contentPreview 仅 300 字）不足以支撑生成，因此必须有 {@link #chunksOf}。
     *
     * <p><b>ID 方言</b>：检索接口返回带连字符的 36 位 UUID，切片接口返回无连字符的 32 位十六进制。
     * 因此 {@link #chunksOf} 的返回键统一为 {@link #normalizeId} 归一化后的形式，
     * 调用方也用它查表——这个不一致曾导致评测脚本的度量缺陷，这里必须显式统一。
     */
    public interface RetrievalFacadePort {
        RetrievalOutcome search(RetrievalQuery query);

        /**
         * 按文档批量取切片全文。
         *
         * @param documentIds 检索命中里的 documentId（两种连字符格式都接受）
         * @return {@code normalizeId(chunkId) -> 切片}
         */
        Map<String, KnowledgeChunk> chunksOf(List<String> documentIds);

        /** 归一化 ID：去连字符并转小写，用于跨接口比对。 */
        static String normalizeId(String id) {
            return id == null ? null : id.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 限流决策：{@code retryAfterSeconds} 为窗口剩余时间，供前端提示。 */
    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
        public static RateLimitDecision allowed(long windowSeconds) {
            return new RateLimitDecision(true, Math.max(1, windowSeconds));
        }

        public static RateLimitDecision rejected(long windowSeconds) {
            return new RateLimitDecision(false, Math.max(1, windowSeconds));
        }
    }

    /**
     * 公开问答限流端口（固定窗口）。
     *
     * <p><b>语义约定</b>：限流是**成本护栏**，不是权限边界（design-04 原则：质量可降级、权限不可降级）。
     * 因此后端不可用时的正确行为是 fail-open + warn，而不是把公开问答整体打挂；真正的泄露防护由
     * SecretGuard 独立承担。
     *
     * @param key   限流键（调用方拼好前缀与身份，如 {@code mindora:rag:rl:ip:1.2.3.4}）
     * @param limit 该身份在本窗口内的最大请求数
     */
    public interface RateLimitPort {
        RateLimitDecision tryAcquire(String key, int limit);
    }

    /**
     * 当前调用者身份端口。
     *
     * <p>业务模块不依赖 Spring Security（本项目的安全装配集中在 mindora-app），因此把「当前用户是谁」
     * 收敛成一个端口，由 app 层用 {@code SecurityContextHolder} 实现；测试可直接给固定身份。
     */
    public interface CallerIdentityPort {
        /** 已登录返回 userId（JWT 的 sub），匿名返回 empty。 */
        java.util.Optional<String> currentUserId();
    }
}
