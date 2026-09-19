package com.mindora.knowledge.infrastructure.adapter;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 外部模型适配器的 HTTP 客户端工厂。
 *
 * <p>统一为 connect / read 设置超时，避免外部模型服务无响应时长期占用请求线程。
 * 超时是 rerank 降级策略（{@code fallback-enabled}）能够生效的前提。
 *
 * <p>使用 {@link JdkClientHttpRequestFactory}（基于 {@code java.net.http.HttpClient}）：
 * 实测 {@code SimpleClientHttpRequestFactory} 的 {@code Duration} 超时重载在本项目
 * 依赖的 Spring Framework 6.2.x 下不生效，JDK 客户端可稳定触发读超时。
 */
final class AdapterHttpFactories {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private AdapterHttpFactories() {
    }

    /**
     * 创建带 connect / read 超时的请求工厂。
     *
     * @param timeoutMillis 超时毫秒数；非正数时回退为 3 秒
     */
    static ClientHttpRequestFactory withTimeout(int timeoutMillis) {
        Duration timeout = timeoutMillis > 0 ? Duration.ofMillis(timeoutMillis) : DEFAULT_TIMEOUT;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
