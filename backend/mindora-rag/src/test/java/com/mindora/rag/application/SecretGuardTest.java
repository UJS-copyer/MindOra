package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 密钥兜底规则集：前缀型命中、上下文层不含赋值型、占位符白名单。 */
class SecretGuardTest {

    @Test
    void 前缀型规则各自命中() {
        assertThat(SecretGuard.scanAnswer("key=sk-abcdefghijklmnopqrstuvwxyz0123456789").orElseThrow().patternId())
                .isEqualTo("openai_style_key");
        assertThat(SecretGuard.scanAnswer("AWS: AKIAIOSFODNN7EXAMPLE").orElseThrow().patternId())
                .isEqualTo("aws_access_key");
        assertThat(SecretGuard.scanAnswer("aliyun LTAI5tAbCdEfGhIjKlMnOpQr").orElseThrow().patternId())
                .isEqualTo("alibaba_access_key");
        assertThat(SecretGuard.scanAnswer("ghp_" + "a".repeat(36)).orElseThrow().patternId())
                .isEqualTo("github_token");
        assertThat(SecretGuard.scanAnswer("AIza" + "b".repeat(35)).orElseThrow().patternId())
                .isEqualTo("google_api_key");
        assertThat(SecretGuard.scanAnswer("xoxb-1234567890-abcdefghij").orElseThrow().patternId())
                .isEqualTo("slack_token");
        assertThat(SecretGuard.scanAnswer("-----BEGIN RSA PRIVATE KEY-----").orElseThrow().patternId())
                .isEqualTo("private_key_block");
    }

    @Test
    void 上下文层只用前缀型规则() {
        // 文档里讲配置时大量出现这种赋值示例，用它拦上下文会大面积误伤
        String context = "配置示例：api_key = abcdefghij0123456789xyz";

        assertThat(SecretGuard.scanContext(context)).isEmpty();
        assertThat(SecretGuard.scanAnswer(context).orElseThrow().patternId()).isEqualTo("credential_assignment");
    }

    @Test
    void 赋值型规则的占位符白名单() {
        assertThat(SecretGuard.scanAnswer("api_key = ${API_KEY}")).isEmpty();
        assertThat(SecretGuard.scanAnswer("api_key = <your-api-key>")).isEmpty();
        assertThat(SecretGuard.scanAnswer("token = xxxxxxxxxxxxxxxx")).isEmpty();
        assertThat(SecretGuard.scanAnswer("password = ************")).isEmpty();
        assertThat(SecretGuard.scanAnswer("secret = changeme-please")).isEmpty();
        assertThat(SecretGuard.scanAnswer("token = ${env:MY_TOKEN}")).isEmpty();
    }

    @Test
    void 真实赋值型凭据命中() {
        assertThat(SecretGuard.scanAnswer("password = \"hunter2hunter2hunter2\"").orElseThrow().patternId())
                .isEqualTo("credential_assignment");
        assertThat(SecretGuard.scanAnswer("token: ghp0123456789abcdefghij").orElseThrow().patternId())
                .isEqualTo("credential_assignment");
    }

    @Test
    void 普通技术文本不误伤() {
        String text = "向量检索与关键词检索通过 RRF 融合，重排后取 top-8；切片 overlap 为 120 字符。";

        assertThat(SecretGuard.scanContext(text)).isEmpty();
        assertThat(SecretGuard.scanAnswer(text)).isEmpty();
    }

    @Test
    void 空输入安全() {
        assertThat(SecretGuard.scanContext(null)).isEmpty();
        assertThat(SecretGuard.scanAnswer("")).isEmpty();
    }
}
