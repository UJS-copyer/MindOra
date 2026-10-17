package com.mindora.rag.application;

import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.StoredMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮记忆组装器：把会话历史裁剪成可注入模型的消息序列（Stage 3.5 T6）。
 *
 * <p>策略：滑动窗口 + Token 截断双约束（design-04：{@code chat_context_turns=4}、
 * {@code max_history_tokens=3000}）。来源与取舍见 docs/stage-3.5-implementation-plan-cn.md §4：
 * <ul>
 *   <li><b>成对取舍</b>：一轮 = user + assistant 两条消息，要么都进要么都不进
 *       （只留 assistant 不留 user 会让模型看到没有问题的回答，来源：nageoffer 会话记忆设计）；</li>
 *   <li><b>超长轮整轮弃</b>：单条超过 hardCapTokens 的轮次直接跳过，异常长文轮不值得占预算；</li>
 *   <li><b>引用编号剥离</b>：assistant 历史里的 {@code [1]} {@code [12]} 尾标必须去掉——
 *       引用核验（AnswerCitationValidator）只作用于本轮回复，历史里的编号会诱导模型复用
 *       本轮不存在的编号，且它们不在本轮核验域内。</li>
 * </ul>
 *
 * <p>Token 估算口径：{@code 中文 × 1.5 + 其他非空白字符 ÷ 4}（jtokkit cl100k 对中文的近似；
 * 这里只做预算上限控制，不做计费，误差可接受）。不引入直接 tokenizer 依赖——单人项目不过度设计。
 */
public final class ConversationMemoryAssembler {
    /** 单条消息硬上限：超过则该轮整轮丢弃（异常长文轮，如降级摘录列表）。 */
    static final int SINGLE_MESSAGE_HARD_CAP_TOKENS = 1500;

    private final int maxTurns;
    private final int maxHistoryTokens;

    public ConversationMemoryAssembler(int maxTurns, int maxHistoryTokens) {
        if (maxTurns < 1 || maxTurns > 8) {
            throw new IllegalArgumentException("chat context turns must be within 1..8");
        }
        if (maxHistoryTokens < 200) {
            throw new IllegalArgumentException("max history tokens must be at least 200");
        }
        this.maxTurns = maxTurns;
        this.maxHistoryTokens = maxHistoryTokens;
    }

    /**
     * @param stored 会话最近消息（时间正序；仓储已按归属过滤，越界/匿名不会传进来）
     * @return 可直接插入 system 与本轮 user 之间的历史消息（时间正序）；输入不足一轮返回空列表
     */
    public List<ChatMessage> assemble(List<StoredMessage> stored) {
        List<Turn> turns = pairTurns(stored);
        List<ChatMessage> picked = new ArrayList<>();
        int budget = maxHistoryTokens;
        // 从最近一轮向前取，超预算即停（时间上更早的丢弃）
        for (int i = turns.size() - 1; i >= 0 && picked.size() / 2 < maxTurns; i--) {
            Turn turn = turns.get(i);
            int tokens = estimateTokens(turn.user().content()) + estimateTokens(turn.assistant().content());
            if (tokens > budget) {
                break;
            }
            picked.add(0, ChatMessage.assistant(stripCitationMarkers(turn.assistant().content())));
            picked.add(0, ChatMessage.user(turn.user().content()));
            budget -= tokens;
        }
        return List.copyOf(picked);
    }

    /** 按 user→assistant 成对切轮：孤立开头消息（如只有 user 没有回答）丢弃，保证成对语义。 */
    private List<Turn> pairTurns(List<StoredMessage> stored) {
        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i + 1 < stored.size(); i++) {
            StoredMessage first = stored.get(i);
            StoredMessage second = stored.get(i + 1);
            if ("user".equals(first.role()) && "assistant".equals(second.role())) {
                if (estimateTokens(first.content()) > SINGLE_MESSAGE_HARD_CAP_TOKENS
                        || estimateTokens(second.content()) > SINGLE_MESSAGE_HARD_CAP_TOKENS) {
                    continue;
                }
                turns.add(new Turn(first, second));
                i++;
            }
        }
        return List.copyOf(turns);
    }

    /** 剥离答案里的引用尾标 {@code [1]} / {@code [12]}（含全角括号），保留自然语言正文。 */
    static String stripCitationMarkers(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.replaceAll("[\\[\\[]\\d{1,3}[\\]\\]]", "");
    }

    /** Token 估算：中文 1 字 ≈ 1.5 token，其他非空白 ≈ 4 字符 1 token（近似口径，见类注释）。 */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                    || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF)) {
                cjk++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        return (int) Math.ceil(cjk * 1.5 + other / 4.0);
    }

    private record Turn(StoredMessage user, StoredMessage assistant) {
    }
}
