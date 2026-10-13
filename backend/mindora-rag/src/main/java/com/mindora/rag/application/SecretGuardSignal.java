package com.mindora.rag.application;

/**
 * 密钥兜底命中信号。
 *
 * <p>用异常而不是返回值：流式转发发生在适配器的消费回调里，需要一条能**穿透回调栈**让适配器立即
 * 停止读取上游、关闭连接的路径（与「客户端断开」共用同一中断机制）。{@link RagAnswerService}
 * 捕获它并转成 {@code PROTECTED_CONTENT} 拒答产出。
 */
public class SecretGuardSignal extends RuntimeException {
    private final transient String patternId;

    public SecretGuardSignal(String patternId) {
        super("secret_guard:" + patternId);
        this.patternId = patternId;
    }

    /** 命中的规则 id（不含密文）。 */
    public String patternId() {
        return patternId;
    }
}
