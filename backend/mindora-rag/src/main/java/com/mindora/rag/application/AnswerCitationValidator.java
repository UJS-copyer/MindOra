package com.mindora.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.rag.application.ContextAssembler.AssembledContext;
import com.mindora.rag.application.ContextAssembler.Segment;
import com.mindora.rag.domain.RagDomain.Citation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 答案引用校验器：拒答的第三层（服务端确定性校验）。
 *
 * <p><b>为什么必须做服务端校验</b>：AbstentionBench（arXiv:2506.09038）显示仅靠提示词无法可靠
 * 拒答，GopherCite（arXiv:2203.11147）证明「带已验证引用作答 + 不确定即弃答」才能把高质量率
 * 从 80% 提到 90%。因此模型输出只当候选，最终是否可信由本类判定。
 *
 * <p><b>校验粒度（实机联调修正）</b>：最初实现是「任一引用不合格即整体拒答」，实测会误杀正确回答
 * ——7B 模型常给出 2 条引用、其中 1 条 quote 未能逐字对上（例如把相邻行拼成一段），
 * 而另一条完全可核验。现在改为**逐条独立校验**：
 * <ol>
 *   <li>编号越界的引用直接丢弃（模型引用了不存在的片段，属确定性接地违规）；</li>
 *   <li>quote 逐字核验通过的引用原样保留；未通过的引用在严格模式下丢弃；</li>
 *   <li>只有「没有任何一条引用可核验」时才判定不可作答，由上层按「知识库没有记录」兜底。</li>
 * </ol>
 *
 * <p>本类同时负责解析模型输出（剥掉可能的 markdown 围栏后按 JSON 解析），
 * 使「解析 + 校验」这一对语义完整内聚。
 */
public class AnswerCitationValidator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 过短的 quote 没有校验意义（例如一个句号），视为无效。 */
    private static final int MIN_QUOTE_CHARS = 4;

    private final boolean requireQuoteVerbatim;

    public AnswerCitationValidator() {
        this(true);
    }

    public AnswerCitationValidator(boolean requireQuoteVerbatim) {
        this.requireQuoteVerbatim = requireQuoteVerbatim;
    }

    /** 模型原始输出（未校验）。 */
    public record ParsedAnswer(boolean refused, String answer, List<RawCitation> citations) {
        public static ParsedAnswer refusedAnswer() {
            return new ParsedAnswer(true, "", List.of());
        }
    }

    /** 模型给出的引用：{@code index} 为片段编号（1 起），{@code quote} 为期望逐字命中的原文片段。 */
    public record RawCitation(int index, String quote) {
    }

    /**
     * 校验结果。
     *
     * @param valid         是否可以作答（至少一条引用落在候选内且通过当前模式的核验）
     * @param citations     可对外暴露的引用（已剔除越界引用）
     * @param verifiedCount 逐字核验通过的引用数
     * @param snippetCount  保留了编号但改用服务端原文摘录的引用数
     * @param droppedCount  因编号越界被丢弃的引用数
     * @param reason        {@code valid=false} 时的原因，供日志与调试
     */
    public record ValidationResult(
            boolean valid,
            List<Citation> citations,
            int verifiedCount,
            int snippetCount,
            int droppedCount,
            String reason,
            String answer) {
        static ValidationResult failure(String reason) {
            return new ValidationResult(false, List.of(), 0, 0, 0, reason, "");
        }
    }

    /**
     * 解析模型输出。
     *
     * <p>容错：剥掉 ```json 围栏、截取最外层花括号；解析失败返回 {@link ParsedAnswer#refusedAnswer()}
     * （宁可拒答，也不把无法解析的输出当成答案）。
     */
    public ParsedAnswer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedAnswer.refusedAnswer();
        }
        String text = stripFences(raw.trim());
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return ParsedAnswer.refusedAnswer();
        }
        try {
            JsonNode root = MAPPER.readTree(text.substring(start, end + 1));
            boolean refused = root.path("refused").asBoolean(false);
            String answer = root.path("answer").asText("");
            List<RawCitation> citations = new ArrayList<>();
            JsonNode array = root.path("citations");
            if (array.isArray()) {
                for (JsonNode node : array) {
                    int index = node.path("index").asInt(0);
                    String quote = node.path("quote").asText("");
                    if (index > 0) {
                        citations.add(new RawCitation(index, quote));
                    }
                }
            }
            if (refused || answer.isBlank()) {
                return ParsedAnswer.refusedAnswer();
            }
            return new ParsedAnswer(false, answer, citations);
        } catch (Exception exception) {
            return ParsedAnswer.refusedAnswer();
        }
    }

    /** 逐条校验模型给出的引用；通过时返回可直接对外暴露的引用列表（ID 统一为公共格式）。 */
    public ValidationResult validate(ParsedAnswer parsed, AssembledContext context) {
        if (parsed == null || parsed.refused()) {
            return ValidationResult.failure("model_refused");
        }
        if (context == null || context.segments().isEmpty()) {
            return ValidationResult.failure("no_context");
        }
        if (parsed.citations().isEmpty()) {
            return ValidationResult.failure("no_citation");
        }

        Map<Integer, Segment> byIndex = new LinkedHashMap<>();
        for (Segment segment : context.segments()) {
            byIndex.put(segment.index(), segment);
        }

        List<Citation> citations = new ArrayList<>();
        int verifiedCount = 0;
        int snippetCount = 0;
        int droppedCount = 0;
        int strictDropped = 0;
        for (RawCitation raw : parsed.citations()) {
            Segment segment = byIndex.get(raw.index());
            if (segment == null) {
                // 引用了本次候选中不存在的片段：确定性接地违规，直接丢弃该条
                droppedCount++;
                continue;
            }
            String quote = raw.quote() == null ? "" : raw.quote().trim();
            boolean quoteVerified = quote.length() >= MIN_QUOTE_CHARS
                    && containsNormalized(segment.text(), quote);
            if (requireQuoteVerbatim && !quoteVerified) {
                // 严格模式：该条不可核验，丢弃它，但不牵连同一次回答里其它可核验的引用
                strictDropped++;
                continue;
            }
            if (quoteVerified) {
                verifiedCount++;
            } else {
                snippetCount++;
            }
            citations.add(new Citation(
                    segment.index(),
                    segment.chunkId(),
                    segment.documentId(),
                    segment.documentTitle(),
                    segment.headingPath(),
                    segment.sourcePath(),
                    segment.articleSlug(),
                    segment.sequence(),
                    // 只有通过逐字校验的 quote 才原样回传；否则一律回传服务端从原文截取的摘录，
                    // 保证对外展示的引用文本永远来自知识库原文
                    quoteVerified ? quote : snippetOf(segment.text()),
                    quoteVerified));
        }

        if (citations.isEmpty()) {
            String reason = droppedCount > 0
                    ? "citations_out_of_range:" + droppedCount
                    : "citations_not_verifiable:" + strictDropped;
            return ValidationResult.failure(reason);
        }
        return new ValidationResult(
                true, citations, verifiedCount, snippetCount, droppedCount, null, parsed.answer());
    }

    /** 空白归一化后判断 {@code haystack} 是否包含 {@code needle}（容忍换行/缩进差异，不容忍改写）。 */
    private boolean containsNormalized(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        return normalizeWhitespace(haystack).contains(normalizeWhitespace(needle));
    }

    private String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** 模型未给出可核验 quote 时，取片段开头一小段作为展示用摘录（仍是服务端逐字截取）。 */
    private String snippetOf(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = normalizeWhitespace(text);
        return collapsed.length() <= 120 ? collapsed : collapsed.substring(0, 120);
    }

    private String stripFences(String text) {
        String result = text;
        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');
            if (firstNewline > 0) {
                result = result.substring(firstNewline + 1);
            }
            int closing = result.lastIndexOf("```");
            if (closing >= 0) {
                result = result.substring(0, closing);
            }
        }
        return result.trim();
    }
}
