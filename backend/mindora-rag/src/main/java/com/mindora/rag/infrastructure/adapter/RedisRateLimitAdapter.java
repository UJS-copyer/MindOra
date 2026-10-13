package com.mindora.rag.infrastructure.adapter;

import com.mindora.rag.application.port.RagAdapters.RateLimitDecision;
import com.mindora.rag.application.port.RagAdapters.RateLimitPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 固定窗口限流（Redis {@code INCR} + 首次 {@code EXPIRE}）。
 *
 * <p><b>为什么是固定窗口而不是令牌桶</b>：个人站点流量下二者差异只在突发整形；固定窗口是 12 行、
 * 零新依赖的实现。若将来确需突发整形，只需替换本类（端口不变）：<br>
 * ① 用 Lua 脚本做令牌桶；② 或引入 bucket4j-redis。当前不引入额外依赖。
 *
 * <p><b>fail-open</b>：Redis 不可用时放行并 warn——限流是成本护栏，不是权限边界；把公开问答整体
 * 打成 429 是更糟的故障模式。泄露防护由 SecretGuard 独立承担（见 design-11）。
 */
public class RedisRateLimitAdapter implements RateLimitPort {
    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitAdapter.class);

    private final StringRedisTemplate redis;
    private final long windowSeconds;

    public RedisRateLimitAdapter(StringRedisTemplate redis, long windowSeconds) {
        this.redis = redis;
        this.windowSeconds = windowSeconds <= 0 ? 60 : windowSeconds;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, int limit) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                // 理论上 increment 不返回 null；真出现时按 fail-open 处理，避免误伤
                return RateLimitDecision.allowed(windowSeconds);
            }
            if (count == 1L) {
                // 仅窗口内第一次请求设置过期时间：窗口结束后键自动消失，计数自然重置
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count <= limit
                    ? RateLimitDecision.allowed(windowSeconds)
                    : RateLimitDecision.rejected(windowSeconds);
        } catch (DataAccessException exception) {
            logger.warn("限流后端不可用，本次放行（fail-open）：key={} 原因={}", key, exception.getMessage());
            return RateLimitDecision.allowed(windowSeconds);
        }
    }
}
