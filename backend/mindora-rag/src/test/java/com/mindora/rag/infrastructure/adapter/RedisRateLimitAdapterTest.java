package com.mindora.rag.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 固定窗口限流（Redis 后端）：计数、只在首次命中设置过期、fail-open。 */
@SuppressWarnings("unchecked")
class RedisRateLimitAdapterTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisRateLimitAdapter adapter;

    @BeforeEach
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        values = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        adapter = new RedisRateLimitAdapter(redis, 60);
    }

    @Test
    void 首次命中设置窗口过期时间并放行() {
        when(values.increment("k")).thenReturn(1L);

        assertThat(adapter.tryAcquire("k", 6).allowed()).isTrue();
        verify(redis).expire(eq("k"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void 窗口内后续命中不重置过期时间() {
        when(values.increment("k")).thenReturn(4L);

        assertThat(adapter.tryAcquire("k", 6).allowed()).isTrue();
        // 若每次 INCR 都 EXPIRE，攻击者可持续刷新窗口（等于不限流）
        verify(redis, never()).expire(any(), any(Duration.class));
    }

    @Test
    void 超出额度时拒绝并给出窗口剩余时间() {
        when(values.increment("k")).thenReturn(7L);

        var decision = adapter.tryAcquire("k", 6);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void Redis不可用时放行而不是把公开问答打挂() {
        when(values.increment("k")).thenThrow(new QueryTimeoutException("redis down"));

        assertThat(adapter.tryAcquire("k", 6).allowed()).isTrue();
    }
}
