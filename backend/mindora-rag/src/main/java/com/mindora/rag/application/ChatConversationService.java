package com.mindora.rag.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话历史应用服务（Stage 3.5 T6）：记忆解析、终态落库、会话 CRUD，三职责一类。
 *
 * <p>刻意不拆更细——单人项目，拆分带来的边界维护成本高于收益（方案 §0）。
 *
 * <p><b>落库语义</b>：只落终态（done / refused / degraded），流式中断不落 assistant 消息；
 * refused/degraded 轮以其对外文案落库并进历史——「刚才没答上来」本身是对话状态。
 *
 * <p><b>身份边界</b>：所有方法要求 userId 非空（匿名由控制器先行短路）；仓储层再做行级校验，
 * 归属不匹配按不存在处理（防枚举）。
 */
public class ChatConversationService {
    /** 会话列表返回上限（前端下拉足够；全量分页属过度设计）。 */
    static final int CONVERSATION_LIST_LIMIT = 50;
    /** 标题长度：首问截断（展示用，可后改——本阶段不改）。 */
    static final int TITLE_MAX_LENGTH = 60;

    private final ChatConversationRepository repository;
    private final ConversationMemoryAssembler memoryAssembler;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ChatConversationService(
            ChatConversationRepository repository,
            ConversationMemoryAssembler memoryAssembler,
            Clock clock) {
        this(repository, memoryAssembler, clock, new ObjectMapper());
    }

    public ChatConversationService(
            ChatConversationRepository repository,
            ConversationMemoryAssembler memoryAssembler,
            Clock clock,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.memoryAssembler = memoryAssembler;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析请求会话的记忆窗口。
     *
     * <p>与 {@link #resolveHistoryIfOwned} 的区别：这里把「不存在/非本人」压成空列表，
     * 供只关心历史的调用方使用；需要区分「非法 id」与「合法但暂无历史」时用 Optional 版本。
     *
     * @return 历史消息（可能为空）；会话不存在/非本人返回空（不抛错——调用方按无会话继续，
     *         沿用 design-11「非法 conversationId 按无会话处理」的契约）
     */
    public List<ChatMessage> resolveHistory(String conversationId, String userId) {
        return resolveHistoryIfOwned(conversationId, userId).orElse(List.of());
    }

    /**
     * 解析会话记忆窗口，并保留「是否归属当前用户」的信息。
     *
     * @return 合法会话返回历史（可能为空）；不存在/非本人/入参为空返回 {@code Optional.empty()}
     */
    public Optional<List<ChatMessage>> resolveHistoryIfOwned(
            String conversationId, String userId) {
        if (isBlank(conversationId) || isBlank(userId)) {
            return Optional.empty();
        }
        if (!repository.existsForUser(conversationId, userId)) {
            return Optional.empty();
        }
        return Optional.of(
                memoryAssembler.assemble(
                        repository.recentMessages(conversationId, userId, maxWindowMessages())));
    }

    /**
     * 落库一轮问答（终态调用；user 与 assistant 成对写入）。
     *
     * @return 会话 id（首次提问创建会话后返回新 id，供响应透出）
     */
    public String recordTurn(
            String conversationId, String userId, String question, RagAnswerOutcome outcome) {
        if (isBlank(userId)) {
            throw new BusinessException("chat_user_required", "Conversation requires a logged-in user");
        }
        Instant now = clock.instant();
        String effectiveConversationId = conversationId;
        if (isBlank(effectiveConversationId)) {
            effectiveConversationId = repository.create(userId, titleOf(question), now);
        } else if (!repository.existsForUser(effectiveConversationId, userId)) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
        repository.appendMessage(
                effectiveConversationId, userId, "user", question, null, "done", null, now);
        repository.appendMessage(
                effectiveConversationId, userId, "assistant", outcome.answer(),
                citationsJson(outcome.citations()), statusOf(outcome), metaJson(outcome), clock.instant());
        return effectiveConversationId;
    }

    public List<ChatConversationRepository.ConversationSummary> listConversations(String userId) {
        if (isBlank(userId)) {
            return List.of();
        }
        return repository.listConversations(userId, CONVERSATION_LIST_LIMIT);
    }

    /** 会话完整消息（前端恢复对话）。首条 user 消息即标题来源，一并返回免去第二次请求。 */
    public ConversationDetail detail(String conversationId, String userId) {
        if (isBlank(conversationId) || isBlank(userId)
                || !repository.existsForUser(conversationId, userId)) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
        List<ChatConversationRepository.ConversationSummary> mine =
                repository.listConversations(userId, CONVERSATION_LIST_LIMIT);
        String title = mine.stream()
                .filter(item -> item.id().equals(conversationId))
                .findFirst()
                .map(ChatConversationRepository.ConversationSummary::title)
                .orElse("");
        return new ConversationDetail(conversationId, title, repository.messages(conversationId, userId));
    }

    public void deleteConversation(String conversationId, String userId) {
        if (isBlank(conversationId) || isBlank(userId)) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
        if (!repository.softDelete(conversationId, userId)) {
            throw new BusinessException("chat_conversation_not_found", "Conversation not found");
        }
    }

    /** 历史窗口的上界消息数：maxTurns 轮 × 2 条 + 1 条容错（孤立 user 轮），仓储侧粗筛即可。 */
    private int maxWindowMessages() {
        return memoryAssemblerWindowTurns() * 2 + 1;
    }

    private int memoryAssemblerWindowTurns() {
        // 组装器在 1..8 之间校验；这里按同一上界粗取，窗口裁剪交给 assemble 的精确逻辑
        return 8;
    }

    private String statusOf(RagAnswerOutcome outcome) {
        if (outcome.refused()) {
            return "refused";
        }
        return outcome.degraded() ? "degraded" : "done";
    }

    /** 引用压缩序列化：只留回显所需六字段（headingPath/sourcePath 可由 chunkId 回查）。 */
    private String citationsJson(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> compact = citations.stream()
                    .map(citation -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("index", citation.index());
                        item.put("chunkId", citation.chunkId());
                        item.put("documentTitle", citation.documentTitle());
                        item.put("articleSlug", citation.articleSlug());
                        item.put("sequence", citation.sequence());
                        item.put("quote", citation.quote());
                        return item;
                    })
                    .toList();
            return objectMapper.writeValueAsString(compact);
        } catch (Exception exception) {
            // 序列化失败不阻断问答主流程：引用历史缺失只影响回显完整度
            return null;
        }
    }

    private String metaJson(RagAnswerOutcome outcome) {
        try {
            Map<String, Object> meta = new HashMap<>();
            if (outcome.chatModel() != null) {
                meta.put("chatModel", outcome.chatModel());
            }
            if (outcome.retrieval() != null) {
                meta.put("latencyMs", outcome.retrieval().latencyMs());
            }
            if (outcome.degraded()) {
                meta.put("degradedReason", outcome.degradedReason());
            }
            if (outcome.refused()) {
                meta.put("refusalReason", outcome.refusalReason() == null ? null : outcome.refusalReason().name());
            }
            return objectMapper.writeValueAsString(meta);
        } catch (Exception exception) {
            return null;
        }
    }

    private String titleOf(String question) {
        String trimmed = question.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 会话详情：消息按时间正序，供前端一次性恢复。 */
    public record ConversationDetail(String id, String title, List<StoredMessage> messages) {
    }
}
