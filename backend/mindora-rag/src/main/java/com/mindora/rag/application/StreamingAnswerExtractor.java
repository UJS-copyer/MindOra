package com.mindora.rag.application;

/**
 * 流式 answer 字段抽取器：从模型输出的 JSON 信封里**增量**提取 {@code answer} 字段的正文。
 *
 * <p><b>为什么需要它</b>：模型契约是完整 JSON 信封（{@code {"refused":..,"answer":"..","citations":[..]}}），
 * 直接转发给用户会看到 JSON 语法。抽取器让正文可以先流出去，而 {@code citations} 仍在流结束后由
 * 既有的 {@code AnswerCitationValidator} 解析完整文本得到——<b>模型契约一行都不用改</b>。
 *
 * <p><b>状态机</b>：
 * <ol>
 *   <li>{@code SEARCHING}：在字符流里找 {@code "answer"} 键 → 跳过空白 → {@code :} → 跳过空白 →
 *       {@code "}，找到后转入取值；未找齐时只保留尾部若干字符（够容纳半个键名，见 {@link #SEARCH_KEEP}）。</li>
 *   <li>{@code IN_VALUE}：逐字符解码——JSON 转义序列（引号、反斜杠、斜杠、n、t、r、b、f）
 *       与 Unicode 转义（反斜杠 + u + 四位十六进制；收满 4 位才输出，因此被 delta 切断也能正确还原）；
 *       遇到**未转义的闭合引号**即结束，其后的 {@code ,"citations":..} 一律忽略。</li>
 *   <li>{@code DONE}：忽略一切后续输入。</li>
 * </ol>
 *
 * <p>兼容性：{@code answer} 出现在 {@code refused} 之前或之后都能工作（只认键名）；
 * 输出带 markdown 围栏也不影响。模型没给 answer 字段（脏输出）时全程返回空串，
 * 由调用方按既有「无引用即拒答」语义处理。
 */
public class StreamingAnswerExtractor {
    private static final String ANSWER_KEY = "\"answer\"";
    /** 搜索态保留的尾部字符数：需覆盖 {@code "answer"} + 冒号 + 引号 + 空白。 */
    private static final int SEARCH_KEEP = 16;

    private enum State {
        SEARCHING,
        IN_VALUE,
        DONE
    }

    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder unicodeHex = new StringBuilder();
    private State state = State.SEARCHING;
    private boolean escaped;
    private boolean inUnicode;

    /**
     * 处理一段原始模型输出，返回本次新解码出的 answer 正文字符（无可转发内容时返回空串）。
     */
    public String accept(String rawDelta) {
        if (rawDelta != null && !rawDelta.isEmpty()) {
            buffer.append(rawDelta);
        }
        StringBuilder out = new StringBuilder();
        if (state == State.SEARCHING) {
            if (!locateValueStart()) {
                return "";
            }
        }
        if (state == State.IN_VALUE) {
            decodeValue(out);
        }
        return out.toString();
    }

    /** 找到 {@code "answer":"} 的结尾位置并转入取值态；字符不够则只保留尾部并返回 false。 */
    private boolean locateValueStart() {
        int valueStart = findValueStart(buffer);
        if (valueStart < 0) {
            if (buffer.length() > SEARCH_KEEP) {
                buffer.delete(0, buffer.length() - SEARCH_KEEP);
            }
            return false;
        }
        buffer.delete(0, valueStart);
        state = State.IN_VALUE;
        return true;
    }

    private void decodeValue(StringBuilder out) {
        int consumed = 0;
        while (consumed < buffer.length()) {
            char current = buffer.charAt(consumed++);
            if (inUnicode) {
                unicodeHex.append(current);
                if (unicodeHex.length() == 4) {
                    out.append(decodeHex(unicodeHex.toString()));
                    unicodeHex.setLength(0);
                    inUnicode = false;
                }
                continue;
            }
            if (escaped) {
                escaped = false;
                switch (current) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> inUnicode = true;
                    default -> out.append(current);
                }
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                // 未转义的闭合引号：answer 值结束，后续字段（citations 等）不参与流式转发
                state = State.DONE;
                break;
            }
            out.append(current);
        }
        buffer.delete(0, consumed);
    }

    private static char decodeHex(String hex) {
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            return ' ';
        }
    }

    /** 返回 answer 值第一个字符的下标；找不到或不完整返回 -1。 */
    private static int findValueStart(CharSequence text) {
        int searchFrom = 0;
        while (true) {
            int keyIndex = indexOf(text, ANSWER_KEY, searchFrom);
            if (keyIndex < 0) {
                return -1;
            }
            int cursor = keyIndex + ANSWER_KEY.length();
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= text.length()) {
                return -1;
            }
            if (text.charAt(cursor) != ':') {
                searchFrom = keyIndex + 1;
                continue;
            }
            cursor++;
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= text.length()) {
                return -1;
            }
            if (text.charAt(cursor) != '"') {
                searchFrom = keyIndex + 1;
                continue;
            }
            return cursor + 1;
        }
    }

    private static int indexOf(CharSequence text, String needle, int fromIndex) {
        int limit = text.length() - needle.length();
        for (int index = Math.max(0, fromIndex); index <= limit; index++) {
            boolean matched = true;
            for (int offset = 0; offset < needle.length(); offset++) {
                if (text.charAt(index + offset) != needle.charAt(offset)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return index;
            }
        }
        return -1;
    }
}
