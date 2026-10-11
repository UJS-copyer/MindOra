package com.mindora.rag.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.id.PublicIds;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import com.mindora.rag.domain.RagDomain.RetrievalMeta;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答调试端点（管理端）。
 *
 * <p>今天是 admin 调试版：scope 默认 {@code all}（可见全部知识库内容，含私有）。
 * 后续公开问答端点必须固定 {@code public} 并叠加限流配额——协议已预留
 * {@code conversationId} 与 {@code expansion} 参数，公开版只需换一个 Controller。
 */
@RestController
@RequestMapping("/api/v1/admin/rag")
@SecurityRequirement(name = "bearerAuth")
public class RagAdminController {
    private final RagAnswerService service;

    public RagAdminController(RagAnswerService service) {
        this.service = service;
    }

    @PostMapping("/answer")
    public ApiResponse<RagAnswerView> answer(
            @RequestBody(required = false) RagAnswerRequestView request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RagAnswerRequestView safe = request == null ? new RagAnswerRequestView(null, null, null, null, null, null, null) : request;
        RagAnswerOutcome outcome = service.answer(new RagAnswerRequest(
                safe.query(),
                safe.scope() == null || safe.scope().isBlank() ? "all" : safe.scope(),
                safe.topK(),
                safe.rerankEnabled(),
                safe.keywordEnabled(),
                safe.expansion(),
                parseConversationId(safe.conversationId())));
        return success(toView(outcome), traceId);
    }

    private UUID parseConversationId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PublicIds.toUuid(raw);
        } catch (IllegalArgumentException exception) {
            // 多轮记忆尚未实现：非法 ID 不报错，按无会话处理
            return null;
        }
    }

    private RagAnswerView toView(RagAnswerOutcome outcome) {
        return new RagAnswerView(
                outcome.answer(),
                outcome.citations().stream().map(this::toCitationView).toList(),
                toRetrievalView(outcome.retrieval()),
                outcome.refused(),
                outcome.refusalReason() == null ? null : outcome.refusalReason().name().toLowerCase(),
                outcome.refusalDetail(),
                outcome.degraded(),
                outcome.degradedReason(),
                outcome.chatModel());
    }

    private CitationView toCitationView(Citation citation) {
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

    private RetrievalView toRetrievalView(RetrievalMeta meta) {
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

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(data, traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    /** 请求体。{@code conversationId} 与 {@code expansion} 为后续扩展预留。 */
    public record RagAnswerRequestView(
            String query,
            String scope,
            Integer topK,
            Boolean rerankEnabled,
            Boolean keywordEnabled,
            Boolean expansion,
            String conversationId) {
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
            /** 拒答细节（如 citation_quote_not_verbatim:1 / model_refused），用于联调定位。 */
            String refusalDetail,
            boolean degraded,
            String degradedReason,
            String chatModel) {
    }
}
