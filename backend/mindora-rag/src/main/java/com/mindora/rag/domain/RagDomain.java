package com.mindora.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RAG 领域模型：上下文策略、引用、请求与产出。
 *
 * <p>均为不可变 record；{@link ContextPolicy} 与 {@link RagAnswerRequest} 为后续扩展
 * （SSE / 多轮对话 / 上下文开关）预留了字段位，见计划文档第三节。
 */
public final class RagDomain {
    private RagDomain() {
    }

    /**
     * 上下文组装策略。
     *
     * <p>预算与扩展参数来自离线实验（docs/rag-retrieval-improvement-survey-cn.md §3.1）：
     * 邻居 ±1 + 同章节 ≤4、每次查询新增 ≤1500 字符是实测拐点（SpanHit@8 88.1% → 91.6%）。
     */
    public record ContextPolicy(
            int budgetChars,
            boolean expansionEnabled,
            int expansionAddedChars,
            int sectionCap) {
        public ContextPolicy {
            budgetChars = budgetChars <= 0 ? 4000 : budgetChars;
            expansionAddedChars = expansionAddedChars <= 0 ? 1500 : expansionAddedChars;
            sectionCap = sectionCap <= 0 ? 4 : sectionCap;
        }

        public static ContextPolicy defaults() {
            return new ContextPolicy(4000, true, 1500, 4);
        }
    }

    /**
     * 一条引用：定位信息（chunkId + sequence + headingPath + sourcePath + articleSlug）
     * 让前端能跳回原文；{@code quote} 由服务端从切片原文逐字复制，用于页内高亮。
     *
     * <p>ID 统一为 32 位无连字符形式（与切片查询接口、公共 ID 约定一致），
     * 便于前端直接用它调 {@code GET /documents/{documentId}/chunks}。
     */
    public record Citation(
            int index,
            String chunkId,
            String documentId,
            String documentTitle,
            String headingPath,
            String sourcePath,
            String articleSlug,
            int sequence,
            String quote,
            /**
             * quote 是否由模型逐字给出并通过服务端核验。
             * {@code false} 表示 quote 是服务端从该片段原文截取的摘录（仍来自原文，但不是模型指认的那段）。
             */
            boolean quoteVerified) {
    }

    /**
     * 请求：单次问答。
     *
     * @param conversationId 多轮记忆预留（今天忽略）
     * @param secretGuard    是否开启密钥兜底（公开端点 true；管理端调试 false——管理员看的是自己的库）
     */
    public record RagAnswerRequest(
            String query,
            String scope,
            Integer topK,
            Boolean rerankEnabled,
            Boolean keywordEnabled,
            Boolean expansion,
            UUID conversationId,
            boolean secretGuard) {
    }

    /** 拒答原因。 */
    public enum RefusalReason {
        /** 检索没有任何候选（或全部被权限过滤）：不调用 LLM 直接拒答。 */
        NO_CONTEXT,
        /** 模型自己判断资料不足而拒答（提示词的第一层拒答生效）。 */
        MODEL_REFUSED,
        /**
         * 模型给了答案，但引用无法在本次候选中核验（编号越界、quote 非逐字、没有引用）。
         * 第二层的服务端确定性校验把它拦下来了。
         */
        INVALID_CITATION,
        /**
         * 命中密钥兜底：上下文或答案（含引用摘录）里出现疑似凭据。
         *
         * <p>这是**公开问答的兜底**，不是主要管控手段——内容是否公开由文章级 visibility 决定。
         * 宁可误拒，不可泄露。
         */
        PROTECTED_CONTENT
    }

    /** 检索与组装的伴随元数据。 */
    public record RetrievalMeta(
            long latencyMs,
            int vectorCandidates,
            int keywordCandidates,
            int matchedTopK,
            boolean expansionApplied,
            int contextChars) {
    }

    /** 单次问答产出。refused 与 degraded 互斥且可同时为 false。 */
    public record RagAnswerOutcome(
            String answer,
            List<Citation> citations,
            RetrievalMeta retrieval,
            boolean refused,
            RefusalReason refusalReason,
            /** 拒答的可诊断细节（校验器给出的具体原因），便于定位是「没召回」还是「引用不达标」。 */
            String refusalDetail,
            boolean degraded,
            String degradedReason,
            String chatModel,
            Instant answeredAt) {
    }
}
