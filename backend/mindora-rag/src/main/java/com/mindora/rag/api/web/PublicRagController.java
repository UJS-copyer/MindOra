package com.mindora.rag.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.exception.BusinessException;
import com.mindora.rag.api.web.RagAnswerViews.CitationView;
import com.mindora.rag.api.web.RagAnswerViews.RagAnswerView;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.application.port.RagAdapters.CallerIdentityPort;
import com.mindora.rag.application.port.RagAdapters.RateLimitDecision;
import com.mindora.rag.application.port.RagAdapters.RateLimitPort;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 公开问答端点（匿名可访问）：同步 JSON 与 SSE 流式各一个。
 *
 * <p><b>三条硬性约束</b>（见 docs/design-11 与 docs/design-04 的公开问答权限模型）：
 * <ol>
 *   <li>{@code scope} 由服务端硬编码为 {@code public}，且请求体里<b>根本没有</b> scope 字段；</li>
 *   <li>公开内容判定沿用知识库既有过滤（visibility/knowledgeEnabled/status + MySQL 权威复核）；</li>
 *   <li>叠加限流（固定窗口）与密钥兜底（SecretGuard）。</li>
 * </ol>
 *
 * <p>请求体刻意收窄为只有 {@code query}：{@code topK / rerankEnabled / keywordEnabled / expansion}
 * 是管理端调试旋钮，不暴露给匿名调用者。
 */
@RestController
@RequestMapping("/api/v1/public/rag")
public class PublicRagController {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 500;
    /** 限流键前缀，便于在 Redis 里识别与清理。 */
    private static final String RATE_LIMIT_KEY_PREFIX = "mindora:rag:rl:";
    /** emitter 超时在模型超时之上留的余量（检索 + 组装 + 收尾事件）。 */
    private static final long STREAM_TIMEOUT_BUFFER_MILLIS = 30_000L;

    private final RagAnswerService service;
    private final RateLimitPort rateLimiter;
    private final CallerIdentityPort callerIdentity;
    private final RagStreamTaskExecutor streamExecutor;
    private final boolean enabled;
    private final int anonymousPerMinute;
    private final int loggedInPerMinute;
    private final long chatTimeoutMillis;

    public PublicRagController(
            RagAnswerService service,
            RateLimitPort rateLimiter,
            CallerIdentityPort callerIdentity,
            RagStreamTaskExecutor streamExecutor,
            @Value("${mindora.rag.public.enabled:true}") boolean enabled,
            @Value("${mindora.rag.public.rate-limit.anonymous-per-minute:6}") int anonymousPerMinute,
            @Value("${mindora.rag.public.rate-limit.logged-in-per-minute:20}") int loggedInPerMinute,
            @Value("${mindora.rag.chat.timeout-ms:15000}") long chatTimeoutMillis) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.callerIdentity = callerIdentity;
        this.streamExecutor = streamExecutor;
        this.enabled = enabled;
        this.anonymousPerMinute = anonymousPerMinute;
        this.loggedInPerMinute = loggedInPerMinute;
        this.chatTimeoutMillis = chatTimeoutMillis;
    }

    /** 同步问答：一次性返回答案与引用。 */
    @PostMapping("/answer")
    public ApiResponse<RagAnswerView> answer(
            @RequestBody(required = false) PublicRagRequestView request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        String query = validatedQuery(request);
        applyRateLimit(httpRequest);
        long startedAt = System.currentTimeMillis();
        RagAnswerOutcome outcome = service.answer(answerRequest(query));
        RagAccessLog.log(
                "public",
                "public",
                callerIdentity.currentUserId().orElse(null),
                httpRequest.getRemoteAddr(),
                query,
                statusOf(outcome),
                reasonOf(outcome),
                outcome.chatModel(),
                System.currentTimeMillis() - startedAt,
                -1L,
                outcome.citations().size());
        return ApiResponse.success(
                RagAnswerViews.of(outcome),
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    /**
     * 流式问答（SSE）。
     *
     * <p>预检（总开关 → 参数 → 限流）全部在返回 emitter 之前完成，失败时是**普通 JSON 错误**
     * （此时还没写 {@code text/event-stream} 头）；一旦开始流，错误只能以事件表达
     * （成熟做法见 Dify 流式文档：流内失败 HTTP 仍为 200）。
     *
     * <p>事件契约：{@code delta}* → {@code citation}* → {@code done}（终态）；
     * 意外失败为 {@code error} 事件（终态）。<b>done 或 error 恰一个为终态。</b>
     */
    @PostMapping("/answer/stream")
    public SseEmitter answerStream(
            @RequestBody(required = false) PublicRagRequestView request,
            HttpServletRequest httpRequest) {
        String query = validatedQuery(request);
        applyRateLimit(httpRequest);
        SseEmitter emitter = new SseEmitter(chatTimeoutMillis + STREAM_TIMEOUT_BUFFER_MILLIS);
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAt = {0L};
        try {
            streamExecutor.execute(() -> runStream(emitter, query, httpRequest, startedAt, firstDeltaAt));
        } catch (TaskRejectedException exception) {
            // 线程池已满：明确告知而不是静默断流
            send(emitter, new ErrorEvent("error", "server_busy", "服务繁忙，请稍后再试"));
            emitter.complete();
        }
        return emitter;
    }

    private void runStream(
            SseEmitter emitter,
            String query,
            HttpServletRequest httpRequest,
            long startedAt,
            long[] firstDeltaAt) {
        String status = "ok";
        String refusalReason = null;
        String chatModel = null;
        int citationCount = 0;
        try {
            Consumer<String> userDelta = text -> {
                if (firstDeltaAt[0] == 0L) {
                    firstDeltaAt[0] = System.currentTimeMillis();
                }
                send(emitter, new DeltaEvent("delta", text));
            };
            RagAnswerOutcome outcome = service.answerStream(answerRequest(query), userDelta);
            citationCount = outcome.citations().size();
            chatModel = outcome.chatModel();
            refusalReason = reasonOf(outcome);
            status = statusOf(outcome);
            for (Citation citation : outcome.citations()) {
                send(emitter, new CitationEvent("citation", RagAnswerViews.ofCitation(citation)));
            }
            send(emitter, new DoneEvent(
                    "done",
                    outcome.refused(),
                    refusalReason,
                    outcome.refusalDetail(),
                    outcome.degraded(),
                    outcome.degradedReason(),
                    outcome.chatModel(),
                    System.currentTimeMillis() - startedAt,
                    deltaMillis(startedAt, firstDeltaAt)));
            emitter.complete();
        } catch (Exception exception) {
            boolean clientGone = isClientGone(exception);
            status = clientGone ? "cancelled" : "error";
            if (!clientGone) {
                try {
                    send(emitter, new ErrorEvent("error", "rag_stream_error", "生成中断，请重试"));
                } catch (RuntimeException ignored) {
                    // 连接已不可用，无需再报
                }
                emitter.complete();
            }
        } finally {
            RagAccessLog.log(
                    "public-stream",
                    "public",
                    callerIdentity.currentUserId().orElse(null),
                    httpRequest.getRemoteAddr(),
                    query,
                    status,
                    refusalReason,
                    chatModel,
                    System.currentTimeMillis() - startedAt,
                    deltaMillis(startedAt, firstDeltaAt),
                    citationCount);
        }
    }

    private RagAnswerRequest answerRequest(String query) {
        // scope 服务端硬编码；secretGuard=true 表示公开端点启用密钥兜底
        return new RagAnswerRequest(query, "public", null, null, null, null, null, true);
    }

    private static String statusOf(RagAnswerOutcome outcome) {
        if (outcome.refused()) {
            return "refused";
        }
        return outcome.degraded() ? "degraded" : "ok";
    }

    private static String reasonOf(RagAnswerOutcome outcome) {
        return outcome.refusalReason() == null ? null : outcome.refusalReason().name().toLowerCase();
    }

    private static long deltaMillis(long startedAt, long[] firstDeltaAt) {
        return firstDeltaAt[0] == 0L ? -1L : firstDeltaAt[0] - startedAt;
    }

    /** 写事件；失败即视为连接不可用（抛出后由 runStream 分类为 cancelled/error）。 */
    private void send(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }

    /** 客户端断开（send 抛 IOException/IllegalStateException）与真实故障的区分。 */
    private boolean isClientGone(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof IOException || current instanceof IllegalStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 固定窗口限流：登录用户按 userId、匿名按来源 IP 分桶，额度不同（design-04：访客限制更严）。
     *
     * <p>身份经 {@link CallerIdentityPort} 获取（业务模块不依赖 Spring Security）。
     *
     * <p>注意：{@code getRemoteAddr()} 是直连地址。若将来部署在反向代理后，需在此处按可信代理配置
     * 解析 {@code X-Forwarded-For}，否则所有访客会共用代理 IP 的额度。
     */
    private void applyRateLimit(HttpServletRequest httpRequest) {
        String userId = callerIdentity.currentUserId().orElse(null);
        boolean loggedIn = userId != null && !userId.isBlank();
        String key = loggedIn
                ? RATE_LIMIT_KEY_PREFIX + "u:" + userId
                : RATE_LIMIT_KEY_PREFIX + "ip:" + httpRequest.getRemoteAddr();
        int limit = loggedIn ? loggedInPerMinute : anonymousPerMinute;
        RateLimitDecision decision = rateLimiter.tryAcquire(key, limit);
        if (!decision.allowed()) {
            throw new BusinessException("rag_rate_limited", "请求过于频繁，请稍后再试");
        }
    }

    /** 预检顺序固定：总开关 → query 校验。任一失败都在写响应体之前抛出（普通 JSON 错误）。 */
    private String validatedQuery(PublicRagRequestView request) {
        if (!enabled) {
            throw new BusinessException("rag_public_disabled", "公开问答暂未开放");
        }
        String raw = request == null ? null : request.query();
        String query = raw == null ? "" : raw.trim();
        if (query.length() < MIN_QUERY_LENGTH || query.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(
                    "validation_error",
                    "问题长度需在 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + " 字之间");
        }
        return query;
    }

    /** 公开问答请求体：只有 query，没有 scope/topK 等调试旋钮（未知字段被忽略）。 */
    public record PublicRagRequestView(String query) {
    }

    /** 增量文本事件（0..n 次）。 */
    public record DeltaEvent(String type, String text) {
    }

    /** 校验通过的引用（逐条，0..n 次）。 */
    public record CitationEvent(String type, CitationView citation) {
    }

    /**
     * 终态事件（恰一次）：拒答/降级/模型信息与延迟都在这里。
     *
     * <p>注意：严格模式下引用逐字核验需要完整文本，因此「先流出文字、后判定拒答」是**设计取舍**——
     * 前端收到 {@code refused=true} 时应以标准拒答文案替换已显示的文字。
     */
    public record DoneEvent(
            String type,
            boolean refused,
            String refusalReason,
            String refusalDetail,
            boolean degraded,
            String degradedReason,
            String chatModel,
            long latencyMs,
            long firstDeltaMs) {
    }

    /** 意外失败事件（终态）。 */
    public record ErrorEvent(String type, String code, String message) {
    }
}
