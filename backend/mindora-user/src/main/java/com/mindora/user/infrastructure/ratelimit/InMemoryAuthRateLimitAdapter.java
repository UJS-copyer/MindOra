package com.mindora.user.infrastructure.ratelimit;

import com.mindora.user.application.port.AuthRateLimitPort;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 进程内固定窗口限流（测试与无 Redis 单实例部署）。
 *
 * <p>与 Redis 实现语义一致：窗口内第一次请求设定起点，窗口结束后计数自然重置。
 */
public class InMemoryAuthRateLimitAdapter implements AuthRateLimitPort {
    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryAuthRateLimitAdapter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        long windowMillis = Math.max(1, window.toMillis());
        Window updated = windows.compute(key, (ignored, current) ->
                current == null || now - current.startMillis() >= windowMillis
                        ? new Window(now, 1)
                        : new Window(current.startMillis(), current.count() + 1));
        return updated.count() <= limit;
    }
}
