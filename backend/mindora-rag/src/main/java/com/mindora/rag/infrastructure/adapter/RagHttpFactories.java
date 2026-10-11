package com.mindora.rag.infrastructure.adapter;

import java.time.Duration;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;

/**
 * HTTP 请求工厂：与 knowledge 模块的 AdapterHttpFactories 等效的最小实现。
 *
 * <p>不复用那个类，因为它的 {@code withTimeout} 是 package-private——RAG 作为独立模块
 * 不应跨越 knowledge 的包边界（见计划文档「风险与回退」）。
 */
final class RagHttpFactories {
    private RagHttpFactories() {
    }

    static ClientHttpRequestFactory withTimeout(int timeoutMillis) {
        int timeout = timeoutMillis <= 0 ? 15000 : timeoutMillis;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(timeout).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(timeout).toMillis());
        return factory;
    }

    static String requireBaseUrl(String baseUrl, String fallback) {
        return StringUtils.hasText(baseUrl) ? baseUrl : fallback;
    }
}
