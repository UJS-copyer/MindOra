package com.mindora.rag.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.rag.api.web.RagAnswerViews.RagAnswerView;
import com.mindora.rag.application.ConversationTurns;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答调试端点（管理端）。
 *
 * <p>管理端可见全部知识库内容（scope 默认 {@code all}，含私有），并开放调参旋钮。
 * 公开端点见 {@link PublicRagController}：请求体收窄为 query、scope 由服务端硬编码为 public、
 * 叠加限流与密钥兜底，但**响应形状与本端点一致**（共用 {@link RagAnswerViews}）。
 *
 * <p>3.6b b2：管理端问答接入会话域——带合法 {@code conversationId} 时注入记忆窗口，
 * 终态落库并把会话 id 随响应回传；{@code persist=false} 时只读历史、不写库（复测场景）。
 */
@RestController
@RequestMapping("/api/v1/admin/rag")
@SecurityRequirement(name = "bearerAuth")
public class RagAdminController {
    /** 管理端允许的检索范围：all=全库（私有域工具），public=按公开域复测（design-11 §15.5）。 */
    private static final Set<String> ALLOWED_SCOPES = Set.of("all", "public");

    private final RagAnswerService service;
    private final ConversationTurns conversationTurns;
    private final com.mindora.rag.application.port.RagAdapters.CallerIdentityPort callerIdentity;

    public RagAdminController(
            RagAnswerService service,
            ConversationTurns conversationTurns,
            com.mindora.rag.application.port.RagAdapters.CallerIdentityPort callerIdentity) {
        this.service = service;
        this.conversationTurns = conversationTurns;
        this.callerIdentity = callerIdentity;
    }

    @PostMapping("/answer")
    public ApiResponse<RagAnswerView> answer(
            @RequestBody(required = false) RagAnswerRequestView request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RagAnswerRequestView safe = request == null
                ? new RagAnswerRequestView(null, null, null, null, null, null, null, null)
                : request;
        String scope = safe.scope() == null || safe.scope().isBlank() ? "all" : safe.scope().trim();
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new BusinessException("rag_scope_invalid", "scope must be all or public");
        }
        ConversationTurns.Resolved conversation = conversationTurns.resolve(safe.conversationId());
        long startedAt = System.currentTimeMillis();
        RagAnswerOutcome outcome = service.answer(new RagAnswerRequest(
                safe.query(),
                scope,
                safe.topK(),
                safe.rerankEnabled(),
                safe.keywordEnabled(),
                safe.expansion(),
                parseConversationId(safe.conversationId()),
                // 管理端调试不启用密钥兜底：管理员看的是自己的知识库，拒答反而妨碍排查
                false,
                conversation.history()));
        boolean persist = safe.persist() == null || safe.persist();
        // persist=false 只读历史、不写库；合法续聊仍回显同一个 id，非法/未带 id 回 null
        String conversationId = persist
                ? conversationTurns.recordQuietly(conversation, safe.query(), outcome)
                : conversationTurns.effectiveId(conversation);
        RagAccessLog.log(
                "admin",
                scope,
                callerIdentity.currentUserId().orElse(null),
                httpRequest.getRemoteAddr(),
                safe.query(),
                outcome.refused() ? "refused" : outcome.degraded() ? "degraded" : "ok",
                outcome.refusalReason() == null ? null : outcome.refusalReason().name().toLowerCase(),
                outcome.chatModel(),
                System.currentTimeMillis() - startedAt,
                -1L,
                outcome.citations().size());
        return success(RagAnswerViews.of(outcome, conversationId), traceId);
    }

    private UUID parseConversationId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PublicIds.toUuid(raw);
        } catch (IllegalArgumentException exception) {
            // 非法 ID 不报错，按无会话处理（与合法 id 的归属校验一致，防枚举）
            return null;
        }
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(data, traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    /**
     * 请求体。{@code persist} 缺省 true：管理端常规调试需要留下会话；
     * 复测/一次性验证可显式传 false，只读历史不落库。
     */
    public record RagAnswerRequestView(
            String query,
            String scope,
            Integer topK,
            Boolean rerankEnabled,
            Boolean keywordEnabled,
            Boolean expansion,
            String conversationId,
            Boolean persist) {
    }
}
