package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 流式密钥过滤：滑动窗口必须能拦住被 delta 边界切断的密钥串。
 *
 * <p>这是本层最关键的用例——真实流式输出里密钥几乎必然跨段，逐段独立扫描会漏检。
 */
class StreamingSecretFilterTest {

    @Test
    void 窗口内文本不转发() {
        StreamingSecretFilter filter = new StreamingSecretFilter();

        assertThat(filter.accept("短文本")).isEmpty();
        assertThat(filter.flush()).isEqualTo("短文本");
    }

    @Test
    void 超过窗口后转发前缀并保留尾部() {
        StreamingSecretFilter filter = new StreamingSecretFilter();
        String text = "a".repeat(200);

        String emittable = filter.accept(text);

        assertThat(emittable).hasSize(200 - StreamingSecretFilter.HOLDBACK);
        // 尾部仍在窗口内，等下一段确认安全后才发
        assertThat(filter.flush()).hasSize(StreamingSecretFilter.HOLDBACK);
    }

    @Test
    void 密钥跨delta切断时命中() {
        StreamingSecretFilter filter = new StreamingSecretFilter();

        // 前半段无害，可以正常转发
        assertThat(filter.accept("这是配置示例：sk-")).isEmpty();

        // 后半段补齐 32 位以上，必须命中
        assertThatThrownBy(() -> filter.accept("abcdefghijklmnopqrstuvwxyz0123456789"))
                .isInstanceOf(SecretGuardSignal.class)
                .hasMessageContaining("secret_guard:openai_style_key");
    }

    @Test
    void 赋值型密钥跨delta切断时命中() {
        StreamingSecretFilter filter = new StreamingSecretFilter();
        filter.accept("配置：api_key = abcdefghij");

        assertThatThrownBy(() -> filter.accept("0123456789xyz"))
                .isInstanceOf(SecretGuardSignal.class)
                .hasMessageContaining("credential_assignment");
    }

    @Test
    void 命中后flush也不会泄露凭据() {
        StreamingSecretFilter filter = new StreamingSecretFilter();

        // accept 阶段即命中（窗口内也整体扫描，所以不必等到 flush）
        assertThatThrownBy(() -> filter.accept("token = ghp0123456789abcdefghij"))
                .isInstanceOf(SecretGuardSignal.class);

        // 即使调用方没有正确处理异常而继续 flush，凭据也不会被吐出
        assertThatThrownBy(filter::flush).isInstanceOf(SecretGuardSignal.class);
    }

    @Test
    void 长文本逐段转发内容不丢字() {
        StreamingSecretFilter filter = new StreamingSecretFilter();
        StringBuilder forwarded = new StringBuilder();
        String text = "向量检索与关键词检索通过 RRF 融合后重排；".repeat(20);

        for (int index = 0; index < text.length(); index += 17) {
            forwarded.append(filter.accept(text.substring(index, Math.min(text.length(), index + 17))));
        }
        forwarded.append(filter.flush());

        assertThat(forwarded.toString()).isEqualTo(text);
    }
}
