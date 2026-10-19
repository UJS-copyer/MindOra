package com.mindora.rag.application;

import com.mindora.rag.application.port.RagAdapters.CallerIdentityPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 共享会话组件（3.6b b1）：把「当前调用者的会话上下文」与「终态落库」从控制器里抽出来，
 * 公开问答与管理端问答共用同一套语义。
 *
 * <p><b>为什么需要它</b>：Stage 3.5 只在公开端点实现了记忆与落库，管理端即使带了
 * {@code conversationId} 也不会注入历史；两处若各写一份「解析 + 落库 + 兜底」，语义必然漂移。
 *
 * <p><b>契约</b>（design-11 §15.5）：
 * <ul>
 *   <li>匿名：上下文为空，落库直接跳过（不报错）；</li>
 *   <li>非法/非本人/已软删 id：按无会话处理，落库时**创建新会话**（不抛错，防枚举）；</li>
 *   <li>落库失败：WARN 后继续返回答案——会话历史是增强能力，不能反向阻断问答。</li>
 * </ul>
 */
public class ConversationTurns {
    private static final Logger log = LoggerFactory.getLogger(ConversationTurns.class);

    private final ChatConversationService conversations;
    private final CallerIdentityPort callerIdentity;

    public ConversationTurns(
            ChatConversationService conversations, CallerIdentityPort callerIdentity) {
        this.conversations = conversations;
        this.callerIdentity = callerIdentity;
    }

    /**
     * 解析本次请求的会话上下文。
     *
     * @param conversationId 客户端带来的会话 id（可空/非法）
     * @return 匿名或非法 id 时 {@code valid=false}、历史为空；合法会话保留 requestedId 与记忆窗口
     */
    public Resolved resolve(String conversationId) {
        String userId = currentUserId();
        if (isBlank(userId)) {
            return Resolved.anonymous();
        }
        if (isBlank(conversationId)) {
            return new Resolved(null, false, List.of());
        }
        try {
            return conversations
                    .resolveHistoryIfOwned(conversationId, userId)
                    .map(history -> new Resolved(conversationId, true, history))
                    .orElseGet(() -> new Resolved(conversationId, false, List.of()));
        } catch (RuntimeException exception) {
            // 会话解析失败不阻断问答：按无会话继续（与「非法值按无会话处理」同一豁免语义）
            log.warn("会话解析失败，按无会话处理：{}", exception.getMessage());
            return new Resolved(conversationId, false, List.of());
        }
    }

    /**
     * 落库一轮终态问答（user 与 assistant 成对写入）。
     *
     * @return 会话 id；匿名返回 null；非法 requestedId 会被当作新会话创建
     */
    public String record(Resolved resolved, String question, RagAnswerOutcome outcome) {
        String userId = currentUserId();
        if (isBlank(userId)) {
            return null;
        }
        String requestedId = resolved != null && resolved.valid() ? resolved.requestedId() : null;
        return conversations.recordTurn(requestedId, userId, question, outcome);
    }

    /** 落库一轮并吞掉异常（同步/流式响应都不因落库失败而失败）；失败返回 null。 */
    public String recordQuietly(Resolved resolved, String question, RagAnswerOutcome outcome) {
        try {
            return record(resolved, question, outcome);
        } catch (RuntimeException exception) {
            log.warn("会话落库失败（不影响问答响应）：{}", exception.getMessage());
            return null;
        }
    }

    /**
     * 响应里应回传的会话 id：仅当请求带来的 id 确实属于当前用户时回传，否则 null。
     *
     * <p>用于 {@code persist=false}：不写库，但合法续聊仍应回显同一个 id。
     */
    public String effectiveId(Resolved resolved) {
        return resolved != null && resolved.valid() ? resolved.requestedId() : null;
    }

    private String currentUserId() {
        return callerIdentity.currentUserId().orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 解析结果：requestedId 仅当 valid 时有意义；history 已按窗口裁剪。 */
    public record Resolved(String requestedId, boolean valid, List<ChatMessage> history) {
        public Resolved {
            history = history == null ? List.of() : List.copyOf(history);
        }

        static Resolved anonymous() {
            return new Resolved(null, false, List.of());
        }
    }
}
