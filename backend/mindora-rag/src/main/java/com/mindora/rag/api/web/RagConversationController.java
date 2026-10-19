package com.mindora.rag.api.web;

import com.mindora.common.api.ApiResponse;
import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.ChatConversationService;
import com.mindora.rag.application.port.RagAdapters.CallerIdentityPort;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话历史端点（3.6b b3）：会话属于账号，统一挂在用户域 {@code /api/v1/rag/conversations*}。
 *
 * <p>安全语义：
 * <ul>
 *   <li>整个 {@code /api/v1/rag/**} 需要登录（见 SecurityConfiguration），匿名请求由安全链返回 401；</li>
 *   <li>所有读写都带当前 {@code userId} 行级过滤，他人资源与不存在统一 404（防枚举）；</li>
 *   <li>删除是软删，消息保留但不可见、不可继续。</li>
 * </ul>
 *
 * <p>路径迁移自 Stage 3.5 的 {@code /api/v1/public/rag/conversations*}：旧路径已删除，
 * 公开问答端点只保留问答与流式，不再承载会话 CRUD。
 */
@RestController
@RequestMapping("/api/v1/rag")
@SecurityRequirement(name = "bearerAuth")
public class RagConversationController {
    private final ChatConversationService conversations;
    private final CallerIdentityPort callerIdentity;

    public RagConversationController(
            ChatConversationService conversations, CallerIdentityPort callerIdentity) {
        this.conversations = conversations;
        this.callerIdentity = callerIdentity;
    }

    /** 当前用户的会话列表（未软删，按最近消息倒序，最多 50 条）。 */
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> conversations() {
        String userId = requireUser();
        List<ConversationView> views = conversations.listConversations(userId).stream()
                .map(item -> new ConversationView(
                        item.id(),
                        item.title(),
                        item.messageCount(),
                        item.lastMessageAt() == null ? null : item.lastMessageAt().toEpochMilli(),
                        item.createdAt() == null ? null : item.createdAt().toEpochMilli()))
                .toList();
        return ApiResponse.success(views, UUID.randomUUID().toString());
    }

    /** 会话完整消息（恢复对话）；他人或不存在统一 404。 */
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<ConversationDetailView> conversationMessages(@PathVariable("id") String id) {
        ChatConversationService.ConversationDetail detail =
                conversations.detail(id, requireUser());
        return ApiResponse.success(new ConversationDetailView(
                detail.id(),
                detail.title(),
                detail.messages().stream()
                        .map(message -> new StoredMessageView(
                                message.role(), message.content(), message.createdAtMillis()))
                        .toList()),
                UUID.randomUUID().toString());
    }

    /** 软删会话（消息保留、不可见）；他人或不存在统一 404。 */
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable("id") String id) {
        conversations.deleteConversation(id, requireUser());
        return ApiResponse.success(null, UUID.randomUUID().toString());
    }

    /**
     * 取当前用户；正常匿名请求在安全链就被拦成 401，这里只是防御性兜底
     * （避免将来安全规则调整时把「匿名」误当成某个 userId）。
     */
    private String requireUser() {
        return callerIdentity
                .currentUserId()
                .filter(userId -> !userId.isBlank())
                .orElseThrow(() -> new BusinessException(
                        "chat_user_required", "Conversation requires a logged-in user"));
    }

    public record ConversationView(
            String id, String title, int messageCount, Long lastMessageAt, Long createdAt) {
    }

    public record StoredMessageView(String role, String content, long createdAt) {
    }

    public record ConversationDetailView(String id, String title, List<StoredMessageView> messages) {
    }
}
