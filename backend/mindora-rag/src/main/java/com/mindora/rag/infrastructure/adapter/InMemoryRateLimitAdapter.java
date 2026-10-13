package com.mindora.rag.infrastructure.adapter;

import com.mindora.rag.application.port.RagAdapters.RateLimitDecision;
import com.mindora.rag.application.port.RagAdapters.RateLimitPort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 进程内固定窗口限流：用于测试环境（测试 profile 不连 Redis）与单实例无 Redis 的部署。
 *
 * <p>语义与 {@link RedisRateLimitAdapter} 保持一致；时钟可注入，便于测试窗口过期重置。
 * 多实例部署下本实现各自计数（不共享），因此生产默认走 Redis 后端。
 */
public class InMemoryRateLimitAdapter implements RateLimitPort {
    private final long windowSeconds;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimitAdapter(long windowSeconds, LongSupplier clock) {
        this.windowSeconds = windowSeconds <= 0 ? 60 : windowSeconds;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, int limit) {
        long now = clock.getAsLong();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startMs >= windowSeconds * 1000L) {
                window.startMs = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= limit
                    ? RateLimitDecision.allowed(windowSeconds)
                    : RateLimitDecision.rejected(windowSeconds);
        }
    }

    private static final class Window {
        private long startMs;
        private long count;

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
