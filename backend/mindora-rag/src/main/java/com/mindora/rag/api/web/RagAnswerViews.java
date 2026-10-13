package com.mindora.rag.api.web;

import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RetrievalMeta;
import java.util.List;

/**
 * 问答响应视图：管理端与公开端点**共用同一形状**（design-11 契约）。
 *
 * <p>抽出来的原因：公开端点必须与管理端返回一致的 citations 结构，避免两处各写一份而漂移。
 * 公开端点的差别只在<b>请求体</b>（收窄到 query）与权限（scope 硬编码 public），不在响应形状。
 */
public final class RagAnswerViews {
    private RagAnswerViews() {
    }

    public static RagAnswerView of(RagAnswerOutcome outcome) {
        return new RagAnswerView(
                outcome.answer(),
                outcome.citations().stream().map(RagAnswerViews::ofCitation).toList(),
                ofRetrieval(outcome.retrieval()),
                outcome.refused(),
                outcome.refusalReason() == null ? null : outcome.refusalReason().name().toLowerCase(),
                outcome.refusalDetail(),
                outcome.degraded(),
                outcome.degradedReason(),
                outcome.chatModel());
    }

    public static CitationView ofCitation(Citation citation) {
        return new CitationView(
                citation.index(),
                citation.chunkId(),
                citation.documentId(),
                citation.documentTitle(),
                citation.headingPath(),
                citation.sourcePath(),
                citation.articleSlug(),
                citation.sequence(),
                citation.quote(),
                citation.quoteVerified());
    }

    private static RetrievalView ofRetrieval(RetrievalMeta meta) {
        if (meta == null) {
            return null;
        }
        return new RetrievalView(
                meta.latencyMs(),
                meta.vectorCandidates(),
                meta.keywordCandidates(),
                meta.matchedTopK(),
                meta.expansionApplied(),
                meta.contextChars());
    }

    public record CitationView(
            int index,
            String chunkId,
            String documentId,
            String documentTitle,
            String headingPath,
            String sourcePath,
            String articleSlug,
            int sequence,
            String quote,
            /** quote 是否由模型逐字给出并核验通过；false 表示是服务端从原文截取的摘录。 */
            boolean quoteVerified) {
    }

    public record RetrievalView(
            long latencyMs,
            int vectorCandidates,
            int keywordCandidates,
            int matchedTopK,
            boolean expansionApplied,
            int contextChars) {
    }

    public record RagAnswerView(
            String answer,
            List<CitationView> citations,
            RetrievalView retrieval,
            boolean refused,
            String refusalReason,
            /** 拒答细节（如 citations_not_verifiable:1 / model_refused / secret_guard:answer:xxx）。 */
            String refusalDetail,
            boolean degraded,
            String degradedReason,
            String chatModel) {
    }
}
