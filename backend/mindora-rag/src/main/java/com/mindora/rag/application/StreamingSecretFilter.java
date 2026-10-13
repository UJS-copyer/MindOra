package com.mindora.rag.application;

import java.util.Optional;

/**
 * 流式密钥过滤：在把模型增量文本转给前端之前做滑动窗口检测。
 *
 * <p><b>为什么需要窗口</b>：密钥串可能被 delta 边界切成两半，逐段独立扫描会漏检。因此始终保留尾部
 * {@link #HOLDBACK} 个字符不转发，等下一段到来后连同新内容一起重扫。
 *
 * <p><b>不变式</b>：{@code HOLDBACK} 必须 ≥ 最长规则长度。若成立，任何完整命中都不可能跨越
 * 「已发射 / 未发射」的边界——跨越边界需要命中长度 &gt; HOLDBACK，与规则表矛盾。
 * 最长规则是 {@code credential_assignment}：关键字（≤8）+ 分隔（≤3）+ 值（≤64）+ 引号 ≈ 80，
 * 故取 96 留出余量。<b>修改规则表时若最长模式变长，必须同步调大 HOLDBACK。</b>
 *
 * <p>代价：显示滞后 96 个字符（人眼不可感知），换来的是不会漏掉被切断的密钥。
 */
public class StreamingSecretFilter {
    public static final int HOLDBACK = 96;

    private final StringBuilder pending = new StringBuilder();

    /**
     * 追加一段新文本，返回可以安全转发的前缀（未超过窗口时返回空串）。
     *
     * @throws SecretGuardSignal 命中规则（调用方应停止转发并拒答）
     */
    public String accept(String text) {
        if (text != null && !text.isEmpty()) {
            pending.append(text);
        }
        Optional<SecretGuard.Hit> hit = SecretGuard.scanAnswer(pending.toString());
        if (hit.isPresent()) {
            throw new SecretGuardSignal(hit.get().patternId());
        }
        if (pending.length() <= HOLDBACK) {
            return "";
        }
        int emitLength = pending.length() - HOLDBACK;
        String emittable = pending.substring(0, emitLength);
        pending.delete(0, emitLength);
        return emittable;
    }

    /**
     * 流结束时把窗口内剩余文本全部交出；仍会先做最后一次扫描。
     *
     * @throws SecretGuardSignal 命中规则
     */
    public String flush() {
        Optional<SecretGuard.Hit> hit = SecretGuard.scanAnswer(pending.toString());
        if (hit.isPresent()) {
            throw new SecretGuardSignal(hit.get().patternId());
        }
        String rest = pending.toString();
        pending.setLength(0);
        return rest;
    }
}
