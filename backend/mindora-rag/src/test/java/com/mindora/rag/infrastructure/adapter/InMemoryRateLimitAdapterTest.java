package com.mindora.rag.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 进程内固定窗口限流：阈值、窗口重置、身份隔离（时钟可注入，便于断言窗口过期）。 */
class InMemoryRateLimitAdapterTest {
    private static final long WINDOW_MILLIS = 60_000L;

    private long now;
    private InMemoryRateLimitAdapter adapter;

    @BeforeEach
    void setUp() {
        now = 1_000_000L;
        adapter = new InMemoryRateLimitAdapter(60, () -> now);
    }

    @Test
    void 额度内放行超出则拒绝() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(adapter.tryAcquire("ip:1.2.3.4", 3).allowed())
                    .as("第 %s 次请求应在额度内", attempt)
                    .isTrue();
        }

        assertThat(adapter.tryAcquire("ip:1.2.3.4", 3).allowed()).isFalse();
    }

    @Test
    void 窗口过期后重新计数() {
        for (int attempt = 0; attempt < 3; attempt++) {
            adapter.tryAcquire("ip:1.2.3.4", 3);
        }
        assertThat(adapter.tryAcquire("ip:1.2.3.4", 3).allowed()).isFalse();

        now += WINDOW_MILLIS;

        assertThat(adapter.tryAcquire("ip:1.2.3.4", 3).allowed()).isTrue();
    }

    @Test
    void 不同身份各自计数互不影响() {
        for (int attempt = 0; attempt < 3; attempt++) {
            adapter.tryAcquire("ip:1.2.3.4", 3);
        }

        assertThat(adapter.tryAcquire("ip:1.2.3.4", 3).allowed()).isFalse();
        assertThat(adapter.tryAcquire("u:someone-else", 3).allowed()).isTrue();
    }
}
