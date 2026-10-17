package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.port.RagAdapters.ChatConversationRepository;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RetrievalMeta;
import com.mindora.rag.infrastructure.persistence.memory.InMemoryChatConversations;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 会话服务单元测试（memory 仓储）：建会话+标题截断、归属校验、落库终态、
 * 匿名/非法会话的兼容语义（方案 §3.3 / §4.3）。
 */
class ChatConversationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
    private static final String USER = "user-1";

    private final InMemoryChatConversations repository = new InMemoryChatConversations();
    private final ChatConversationService service = new ChatConversationService(
            repository,
            new ConversationMemoryAssembler(4, 3000),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private RagAnswerOutcome outcome(String answer) {
        return new RagAnswerOutcome(
                answer,
                List.of(new Citation(1, "chunk-1", "doc-1", "文档", "章节", "路径", "slug-1", 2, "引文", true)),
                new RetrievalMeta(100, 30, 30, 8, false, 2000),
                false, null, null, false, null, "test-model", NOW);
    }

    @Test
    void recordTurnCreatesConversationWithTitleFromFirstQuestion() {
        String longQuestion = "这是一个超过六十个字符的问题吗我们来看看标题截断是否生效再补一些文字进来确保它确实超过了六十个字符的界限位置补足长度用的尾巴";
        String id = service.recordTurn(null, USER, longQuestion, outcome("答案"));

        assertThat(id).isNotBlank();
        List<ChatConversationRepository.ConversationSummary> conversations = service.listConversations(USER);
        assertThat(conversations).hasSize(1);
        assertThat(conversations.getFirst().title()).hasSize(60);
        assertThat(conversations.getFirst().title()).isEqualTo(longQuestion.substring(0, 60));
        assertThat(conversations.getFirst().messageCount()).isEqualTo(2);
    }

    @Test
    void resolveHistoryReturnsWindowedTurns() {
        String id = service.recordTurn(null, USER, "第一问", outcome("第一答 [1]"));
        service.recordTurn(id, USER, "第二问", outcome("第二答 [2]"));

        List<ChatMessage> history = service.resolveHistory(id, USER);

        // 记忆窗口：时间正序、assistant 剥离 [n]
        assertThat(history).extracting(ChatMessage::content)
                .containsExactly("第一问", "第一答 ", "第二问", "第二答 ");
    }

    @Test
    void resolveHistoryIsEmptyForForeignOrMissingConversation() {
        String foreignId = service.recordTurn(null, "user-2", "别人的问题", outcome("别人的答案"));

        // 他人会话与不存在会话一律空列表（不抛错，按无会话处理——防枚举）
        assertThat(service.resolveHistory(foreignId, USER)).isEmpty();
        assertThat(service.resolveHistory("nonexistent", USER)).isEmpty();
        assertThat(service.resolveHistory(null, USER)).isEmpty();
        assertThat(service.resolveHistory("any", null)).isEmpty();
    }

    @Test
    void recordTurnRejectsForeignConversation() {
        String foreignId = service.recordTurn(null, "user-2", "别人的问题", outcome("别人的答案"));

        assertThatThrownBy(() -> service.recordTurn(foreignId, USER, "我的追问", outcome("答案")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo("chat_conversation_not_found");
    }

    @Test
    void refusedTurnIsStoredWithRefusalCopy() {
        RagAnswerOutcome refused = new RagAnswerOutcome(
                "知识库中没有找到能回答这个问题的内容。", List.of(),
                new RetrievalMeta(50, 0, 0, 0, false, 0),
                true, com.mindora.rag.domain.RagDomain.RefusalReason.NO_CONTEXT, "no_candidates",
                false, null, null, NOW);

        String id = service.recordTurn(null, USER, "没有依据的问题", refused);
        List<StoredMessage> messages = repository.messages(id, USER);

        // 拒答轮成对落库：「刚才没答上来」是对话状态，应进历史
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).isEqualTo("知识库中没有找到能回答这个问题的内容。");
    }

    @Test
    void detailReturnsMessagesInOrderAndDeleteIsSoft() {
        String id = service.recordTurn(null, USER, "问", outcome("答"));

        ChatConversationService.ConversationDetail detail = service.detail(id, USER);
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().getFirst().content()).isEqualTo("问");

        service.deleteConversation(id, USER);
        assertThat(service.resolveHistory(id, USER)).isEmpty();
        assertThatThrownBy(() -> service.detail(id, USER)).isInstanceOf(BusinessException.class);
    }

    @Test
    void listConversationsIsEmptyForAnonymous() {
        assertThat(service.listConversations(null)).isEmpty();
    }
}
