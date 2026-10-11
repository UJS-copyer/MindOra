package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.rag.application.AnswerCitationValidator.ParsedAnswer;
import com.mindora.rag.application.AnswerCitationValidator.RawCitation;
import com.mindora.rag.application.ContextAssembler.AssembledContext;
import com.mindora.rag.application.ContextAssembler.Segment;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 引用校验器单测：解析容错（围栏/脏 JSON/显式拒答）与三条校验规则
 * （编号越界、quote 非逐字、quote 过短），以及空白归一化与宽松模式。
 */
class AnswerCitationValidatorTest {

    private final AnswerCitationValidator validator = new AnswerCitationValidator();

    private static AssembledContext contextWith(String... texts) {
        List<Segment> segments = new java.util.ArrayList<>();
        for (int index = 0; index < texts.length; index++) {
            segments.add(new Segment(
                    index + 1,
                    "chunk" + (index + 1),
                    "doc1",
                    "文档",
                    "章节",
                    "工作/x.md",
                    null,
                    index + 1,
                    false,
                    texts[index]));
        }
        return new AssembledContext(segments, false, 0, Map.of());
    }

    // ---------- 解析 ----------

    @Test
    void 解析标准JSON() {
        ParsedAnswer parsed = validator.parse(
                "{\"refused\":false,\"answer\":\"答案[1]\",\"citations\":[{\"index\":1,\"quote\":\"片段原文\"}]}");
        assertThat(parsed.refused()).isFalse();
        assertThat(parsed.answer()).isEqualTo("答案[1]");
        assertThat(parsed.citations()).hasSize(1);
        assertThat(parsed.citations().get(0).index()).isEqualTo(1);
        assertThat(parsed.citations().get(0).quote()).isEqualTo("片段原文");
    }

    @Test
    void 解析带markdown围栏的JSON() {
        ParsedAnswer parsed = validator.parse("""
                ```json
                {"refused":false,"answer":"答案[1]","citations":[{"index":1,"quote":"片段原文"}]}
                ```
                """);
        assertThat(parsed.refused()).isFalse();
        assertThat(parsed.answer()).isEqualTo("答案[1]");
    }

    @Test
    void 解析前后有解释文字时截取最外层花括号() {
        ParsedAnswer parsed = validator.parse(
                "好的，结果如下：{\"refused\":false,\"answer\":\"A[1]\",\"citations\":[{\"index\":1,\"quote\":\"片段原文\"}]} 以上。");
        assertThat(parsed.refused()).isFalse();
        assertThat(parsed.answer()).isEqualTo("A[1]");
    }

    @Test
    void 模型显式拒答时视为拒答() {
        ParsedAnswer parsed = validator.parse("{\"refused\":true,\"answer\":\"\",\"citations\":[]}");
        assertThat(parsed.refused()).isTrue();
    }

    @Test
    void 答案为空视为拒答() {
        ParsedAnswer parsed = validator.parse("{\"refused\":false,\"answer\":\"   \",\"citations\":[]}");
        assertThat(parsed.refused()).isTrue();
    }

    @Test
    void 脏JSON或空输入一律按拒答处理() {
        assertThat(validator.parse("not json at all").refused()).isTrue();
        assertThat(validator.parse("").refused()).isTrue();
        assertThat(validator.parse(null).refused()).isTrue();
        assertThat(validator.parse("{\"refused\":false,\"answer\":").refused()).isTrue();
    }

    // ---------- 校验 ----------

    @Test
    void 引用编号与quote都成立时通过并产出引用() {
        AssembledContext context = contextWith("Redis 缓存穿透的解决方案是布隆过滤器。");
        ParsedAnswer parsed = new ParsedAnswer(
                false, "布隆过滤器[1]", List.of(new RawCitation(1, "Redis 缓存穿透的解决方案是布隆过滤器。")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isTrue();
        assertThat(result.answer()).isEqualTo("布隆过滤器[1]");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).index()).isEqualTo(1);
        assertThat(result.citations().get(0).chunkId()).isEqualTo("chunk1");
        assertThat(result.citations().get(0).quote()).isEqualTo("Redis 缓存穿透的解决方案是布隆过滤器。");
        assertThat(result.citations().get(0).quoteVerified()).isTrue();
        assertThat(result.verifiedCount()).isEqualTo(1);
        assertThat(result.snippetCount()).isZero();
        assertThat(result.droppedCount()).isZero();
    }

    @Test
    void 一条引用改写另一条可核验时保留可核验的那条而不是整体拒答() {
        // 实机联调发现的回归：7B 模型给 2 条引用、其中 1 条 quote 未逐字对上，
        // 旧实现会连另一条正确的引用一起丢掉，导致把答对的回答误判为「没有记录」。
        AssembledContext context = contextWith(
                "Redis 缓存穿透的解决方案是布隆过滤器。", "缓存雪崩可以给过期时间加随机值。");
        ParsedAnswer parsed = new ParsedAnswer(
                false,
                "用布隆过滤器[1]，雪崩加随机过期时间[2]",
                List.of(
                        new RawCitation(1, "模型自己概括的一段话"),
                        new RawCitation(2, "缓存雪崩可以给过期时间加随机值。")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).index()).isEqualTo(2);
        assertThat(result.citations().get(0).quoteVerified()).isTrue();
    }

    @Test
    void 所有引用都不可核验时才判为不可作答() {
        AssembledContext context = contextWith("Redis 缓存穿透的解决方案是布隆过滤器。");
        ParsedAnswer parsed = new ParsedAnswer(
                false, "答案[1]", List.of(new RawCitation(1, "模型自己概括的一段话")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).startsWith("citations_not_verifiable");
    }

    @Test
    void 编号越界的引用被丢弃而可核验的保留() {
        AssembledContext context = contextWith("只有这一段原文内容。");
        ParsedAnswer parsed = new ParsedAnswer(
                false,
                "答案[1][9]",
                List.of(new RawCitation(9, "越界引用"), new RawCitation(1, "只有这一段原文内容。")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).index()).isEqualTo(1);
        assertThat(result.droppedCount()).isEqualTo(1);
    }

    @Test
    void 所有引用编号都越界时判为不可作答() {
        AssembledContext context = contextWith("只有这一段原文内容。");
        ParsedAnswer parsed = new ParsedAnswer(false, "答案[9]", List.of(new RawCitation(9, "越界引用")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).startsWith("citations_out_of_range");
    }

    @Test
    void quote空白归一化后匹配即可通过() {
        AssembledContext context = contextWith("第一行\n    第二行内容");
        ParsedAnswer parsed = new ParsedAnswer(
                false, "答案[1]", List.of(new RawCitation(1, "第一行 第二行内容")));

        assertThat(validator.validate(parsed, context).valid()).isTrue();
    }

    @Test
    void quote过短时视为不可核验() {
        AssembledContext context = contextWith("Redis 缓存穿透的解决方案是布隆过滤器。");
        ParsedAnswer parsed = new ParsedAnswer(false, "答案[1]", List.of(new RawCitation(1, "。")));

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).startsWith("citations_not_verifiable");
    }

    @Test
    void 没有任何引用时判为不可信() {
        AssembledContext context = contextWith("内容");
        ParsedAnswer parsed = new ParsedAnswer(false, "答案", List.of());

        var result = validator.validate(parsed, context);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("no_citation");
    }

    @Test
    void 模型已拒答时直接不可信() {
        AssembledContext context = contextWith("内容");
        var result = validator.validate(ParsedAnswer.refusedAnswer(), context);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("model_refused");
    }

    @Test
    void 无上下文时判为不可信() {
        var result = validator.validate(
                new ParsedAnswer(false, "答案[1]", List.of(new RawCitation(1, "内容"))),
                new AssembledContext(List.of(), false, 0, Map.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("no_context");
    }

    @Test
    void 宽松模式下不强制quote逐字并回填服务端摘录() {
        AnswerCitationValidator lenient = new AnswerCitationValidator(false);
        AssembledContext context = contextWith("Redis 缓存穿透的解决方案是布隆过滤器，可以拦截不存在的 key。");
        ParsedAnswer parsed = new ParsedAnswer(
                false, "答案[1]", List.of(new RawCitation(1, "模型自己概括的一句话")));

        var result = lenient.validate(parsed, context);

        assertThat(result.valid()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).quote()).startsWith("Redis 缓存穿透的解决方案是布隆过滤器");
        assertThat(result.citations().get(0).quoteVerified()).isFalse();
        assertThat(result.snippetCount()).isEqualTo(1);
        assertThat(result.verifiedCount()).isZero();
    }
}
