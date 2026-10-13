package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 流式 answer 抽取器：重点是**任意切分方式下的等价性**。
 *
 * <p>真实上游的 delta 边界不可预测（可能正好切在键名、转义符或 Unicode 转义中间），因此除了定点用例，
 * 还有一个随机切分的性质测试：任何切分都应与「整段一次给入」的结果完全一致。
 */
class StreamingAnswerExtractorTest {

    private static final String NORMAL_ENVELOPE =
            "{\"refused\":false,\"answer\":\"向量检索与关键词检索通过 RRF 融合[1]。\","
                    + "\"citations\":[{\"index\":1,\"quote\":\"RRF 融合\"}]}";
    private static final String ESCAPED_ENVELOPE =
            "{\"refused\":false,\"answer\":\"第一行\\n第二行：带 \\\"引号\\\" 与反斜杠 \\\\ 以及中文 \\u4f60\\u597d\","
                    + "\"citations\":[]}";
    private static final String REFUSED_ENVELOPE =
            "{\"refused\":true,\"answer\":\"\",\"citations\":[]}";

    @Test
    void 整段给入时抽出正文() {
        assertThat(drain(List.of(NORMAL_ENVELOPE))).isEqualTo("向量检索与关键词检索通过 RRF 融合[1]。");
    }

    @Test
    void 逐字符给入结果一致() {
        List<String> pieces = new ArrayList<>();
        for (char character : NORMAL_ENVELOPE.toCharArray()) {
            pieces.add(String.valueOf(character));
        }

        assertThat(drain(pieces)).isEqualTo("向量检索与关键词检索通过 RRF 融合[1]。");
    }

    @Test
    void 键名在delta边界被切断() {
        assertThat(drain(List.of("{\"refused\":false,\"ans", "wer\":\"正文", "\",\"citations\":[]}")))
                .isEqualTo("正文");
    }

    @Test
    void 转义反斜杠与引号被拆成两段() {
        // 原始 JSON：{"answer":"他说\"好\"","citations":[]}
        // 两个 \" 转义都被 delta 边界拆成 \ 与 "，不能把紧跟其后的 " 误判为值结束
        assertThat(drain(List.of(
                "{\"answer\":\"他说\\",
                "\"好\\",
                "\"\",\"citations\":[]}")))
                .isEqualTo("他说\"好\"");
    }

    @Test
    void unicode转义被拆成两段() {
        assertThat(drain(List.of("{\"answer\":\"\\u4f", "60\\u597d\",\"citations\":[]}")))
                .isEqualTo("你好");
    }

    @Test
    void 换行与制表转义正确解码() {
        assertThat(drain(List.of("{\"answer\":\"a\\nb\\tc\",\"citations\":[]}")))
                .isEqualTo("a\nb\tc");
    }

    @Test
    void 闭合引号之后的内容不再输出() {
        assertThat(drain(List.of("{\"answer\":\"正文\",\"citations\":[{\"quote\":\"不应出现\"}]}")))
                .isEqualTo("正文");
    }

    @Test
    void 拒答时答案为空串且无输出() {
        assertThat(drain(List.of(REFUSED_ENVELOPE))).isEmpty();
    }

    @Test
    void answer键在refused键之前也能工作() {
        assertThat(drain(List.of("{\"answer\":\"先给的正文\",\"refused\":false,\"citations\":[]}")))
                .isEqualTo("先给的正文");
    }

    @Test
    void 带markdown围栏与前后空白也能抽出() {
        assertThat(drain(List.of("```json\n{\"refused\":false, \"answer\" : \"正文\" , \"citations\":[]}\n```")))
                .isEqualTo("正文");
    }

    @Test
    void 脏输出没有answer字段时无输出() {
        assertThat(drain(List.of("抱歉，我不确定。"))).isEmpty();
    }

    @Test
    void 任意随机切分结果都与整段一致() {
        List<String> envelopes = List.of(NORMAL_ENVELOPE, ESCAPED_ENVELOPE, REFUSED_ENVELOPE);
        for (String envelope : envelopes) {
            String expected = drain(List.of(envelope));
            Random random = new Random(20261011L + envelope.length());
            for (int round = 0; round < 100; round++) {
                assertThat(drain(randomSplit(envelope, random)))
                        .as("第 %s 轮切分应与整段一致：%s", round, envelope)
                        .isEqualTo(expected);
            }
        }
    }

    private static List<String> randomSplit(String text, Random random) {
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int size = 1 + random.nextInt(7);
            int end = Math.min(text.length(), cursor + size);
            pieces.add(text.substring(cursor, end));
            cursor = end;
        }
        return pieces;
    }

    private static String drain(List<String> pieces) {
        StreamingAnswerExtractor extractor = new StreamingAnswerExtractor();
        StringBuilder out = new StringBuilder();
        for (String piece : pieces) {
            out.append(extractor.accept(piece));
        }
        return out.toString();
    }
}
