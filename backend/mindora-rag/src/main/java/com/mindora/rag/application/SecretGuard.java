package com.mindora.rag.application;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 密钥兜底：在**公开问答链路上**扫描「疑似凭据」，防止含密钥的笔记被检索到之后由模型复述出去。
 *
 * <p><b>定位</b>：这是兜底，不是主要管控手段——内容是否进入知识库由文章级 visibility 决定
 * （design-04 公开问答权限模型）。它要解决的是「不该公开的文档意外可检索」这一剩余风险。
 *
 * <p><b>两层规则</b>：
 * <ul>
 *   <li>{@link #scanContext}：检索后、调模型<b>之前</b>扫上下文，只用<b>前缀型</b>高置信规则。
 *       命中即拒答，密钥连 prompt 都不会进；</li>
 *   <li>{@link #scanAnswer}：生成后扫答案文本与引用摘录，用<b>全部</b>规则（含赋值型）。
 *       上下文里出现 {@code api_key = ...} 这类示例很常见（文档本身就在讲配置），用它扫上下文会
 *       大面积误伤；但答案里出现赋值型，说明模型在复述配置值，必须拦。</li>
 * </ul>
 *
 * <p><b>规则来源</b>：取自 gitleaks 默认规则集的高置信子集（
 * <a href="https://github.com/gitleaks/gitleaks/blob/master/config/gitleaks.toml">gitleaks.toml</a>），
 * 按本项目语料简化了长度与字符集；不做熵检测（实现复杂度与收益不匹配）。
 *
 * <p><b>误伤取向</b>：宁可误拒、不可泄露。命中只返回规则 id，<b>绝不记录命中内容本身</b>。
 */
public final class SecretGuard {

    /** 一条检测规则。{@code prefixOnly=true} 表示可用于上下文层扫描。 */
    public record Rule(String id, Pattern pattern, boolean prefixOnly) {
    }

    /** 命中结果：只带规则 id，不带命中的密文。 */
    public record Hit(String patternId) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("openai_style_key", Pattern.compile("\\bsk-[A-Za-z0-9_-]{32,}\\b"), true),
            new Rule("aws_access_key", Pattern.compile("\\b(?:AKIA|ASIA|ABIA|ACCA)[A-Z0-9]{16}\\b"), true),
            new Rule("alibaba_access_key", Pattern.compile("\\bLTAI[A-Za-z0-9]{16,24}\\b"), true),
            new Rule("github_token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"), true),
            new Rule("google_api_key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"), true),
            new Rule("slack_token", Pattern.compile("\\bxox[baprs]-[0-9A-Za-z-]{10,}\\b"), true),
            new Rule("private_key_block", Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY(?: BLOCK)?-----"), true),
            new Rule(
                    "credential_assignment",
                    Pattern.compile(
                            "(?i)\\b(?:api[_-]?key|token|secret|password|passwd|pwd)\\b[\"']?\\s*[:=]\\s*[\"']?"
                                    + "[A-Za-z0-9_+=./~-]{20,64}"),
                    false));

    /** 占位符白名单：文档里讲配置时大量出现，必须放过。 */
    private static final Pattern PLACEHOLDER_VALUE = Pattern.compile(
            "(?i)(?:\\$\\{[^}]*\\}|\\{\\{[^}]*\\}\\}|<[^>]+>|x{5,}|\\*{5,}|changeme\\w*|your[-_a-z0-9]*|"
                    + "placeholder|example\\w*|redacted|todo|none|null|xxx+)");

    private static final Pattern ASSIGNMENT_VALUE = Pattern.compile("^.*?[:=]\\s*");

    private SecretGuard() {
    }

    /** 上下文层：仅前缀型高置信规则。 */
    public static Optional<Hit> scanContext(String text) {
        return scan(text, true);
    }

    /** 答案层：全部规则（含赋值型，带占位符白名单）。 */
    public static Optional<Hit> scanAnswer(String text) {
        return scan(text, false);
    }

    private static Optional<Hit> scan(String text, boolean prefixRulesOnly) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            if (prefixRulesOnly && !rule.prefixOnly()) {
                continue;
            }
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                if (!rule.prefixOnly() && isPlaceholder(matcher.group())) {
                    continue;
                }
                return Optional.of(new Hit(rule.id()));
            }
        }
        return Optional.empty();
    }

    private static boolean isPlaceholder(String match) {
        String value = ASSIGNMENT_VALUE.matcher(match).replaceFirst("").trim();
        value = value.replaceAll("^[\"']", "").replaceAll("[\"']$", "");
        return PLACEHOLDER_VALUE.matcher(value).matches();
    }
}
