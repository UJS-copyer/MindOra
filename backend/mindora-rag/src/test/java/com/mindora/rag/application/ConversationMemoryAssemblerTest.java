package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 记忆组装器单元测试：轮数上限、成对截断、超长轮丢弃、[n] 剥离、时间正序还原。
 * 预算语义（方案 §4.1）与 design-04 参数（4 轮 / 3000 token）。
 */
class ConversationMemoryAssemblerTest {
    private final ConversationMemoryAssembler assembler = new ConversationMemoryAssembler(4, 3000);

    @Test
    void keepsMostRecentTurnsInChronologicalOrder() {
        List<StoredMessage> stored = List.of(
                new StoredMessage("user", "第一问", 1),
                new StoredMessage("assistant", "第一答", 2),
                new StoredMessage("user", "第二问", 3),
                new StoredMessage("assistant", "第二答", 4));

        List<ChatMessage> messages = assembler.assemble(stored);

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).content()).isEqualTo("第一问");
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(1).content()).isEqualTo("第一答");
        assertThat(messages.get(3).content()).isEqualTo("第二答");
    }

    @Test
    void respectsTurnLimit() {
        List<StoredMessage> stored = new java.util.ArrayList<>();
        for (int turn = 1; turn <= 6; turn++) {
            stored.add(new StoredMessage("user", "问" + turn, turn * 10L));
            stored.add(new StoredMessage("assistant", "答" + turn, turn * 10L + 1));
        }

        List<ChatMessage> messages = assembler.assemble(stored);

        // 4 轮上限：最早的 2 轮被窗口丢弃
        assertThat(messages).hasSize(8);
        assertThat(messages.getFirst().content()).isEqualTo("问3");
        assertThat(messages.getLast().content()).isEqualTo("答6");
    }

    @Test
    void dropsWholeTurnWhenTokenBudgetExceeded() {
        // 预算 3000：第 5 轮 assistant 很长（>剩余预算），从该轮起整轮不进
        String longAnswer = "长".repeat(2500); // ≈3750 tokens（>3000 预算）
        List<StoredMessage> stored = List.of(
                new StoredMessage("user", "早期问题", 1),
                new StoredMessage("assistant", longAnswer, 2),
                new StoredMessage("user", "最近问", 3),
                new StoredMessage("assistant", "最近答", 4));

        List<ChatMessage> messages = assembler.assemble(stored);

        // 成对截断：长轮整轮丢弃，只保留最近一轮
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).isEqualTo("最近问");
        assertThat(messages.getLast().content()).isEqualTo("最近答");
    }

    @Test
    void skipsOversizedSingleMessageTurnEntirely() {
        String oversized = "长".repeat(2000); // ≈3000 tokens > 单条 1500 硬上限
        List<StoredMessage> stored = List.of(
                new StoredMessage("user", oversized, 1),
                new StoredMessage("assistant", "答", 2),
                new StoredMessage("user", "正常问", 3),
                new StoredMessage("assistant", "正常答", 4));

        List<ChatMessage> messages = assembler.assemble(stored);

        // 超长轮整轮弃，即使总预算放得下
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).isEqualTo("正常问");
    }

    @Test
    void stripsCitationMarkersFromAssistantHistory() {
        List<StoredMessage> stored = List.of(
                new StoredMessage("user", "DDPM 是什么", 1),
                new StoredMessage("assistant", "它是一篇论文 [1]，后来又有工作 [12]。", 2));

        List<ChatMessage> messages = assembler.assemble(stored);

        // [n] 剥离：历史编号不得进入本轮核验域，也不诱导模型复用编号
        assertThat(messages.get(1).content()).isEqualTo("它是一篇论文 ，后来又有工作 。");
    }

    @Test
    void ignoresLeadingOrphanUserMessage() {
        List<StoredMessage> stored = List.of(
                new StoredMessage("user", "被中断的问句（无回答）", 1),
                new StoredMessage("user", "第二问", 2),
                new StoredMessage("assistant", "第二答", 3));

        List<ChatMessage> messages = assembler.assemble(stored);

        // 孤立 user 开头不成轮（成对语义）
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).isEqualTo("第二问");
    }

    @Test
    void returnsEmptyForEmptyHistory() {
        assertThat(assembler.assemble(List.of())).isEmpty();
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThatThrownBy(() -> new ConversationMemoryAssembler(0, 3000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationMemoryAssembler(9, 3000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationMemoryAssembler(4, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
