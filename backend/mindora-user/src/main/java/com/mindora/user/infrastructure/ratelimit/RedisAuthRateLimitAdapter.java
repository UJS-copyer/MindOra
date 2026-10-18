package com.mindora.user.infrastructure.ratelimit;

import com.mindora.user.application.port.AuthRateLimitPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 固定窗口限流（{@code INCR} + 首次 {@code EXPIRE}）。
 *
 * <p><b>fail-open</b>：Redis 不可用时放行并 WARN——限流是防刷护栏，不是权限边界；
 * 把登录整体打成 429 是更糟的故障模式（与公开问答的既有取舍一致）。
 */
public class RedisAuthRateLimitAdapter implements AuthRateLimitPort {
    private static final Logger logger = LoggerFactory.getLogger(RedisAuthRateLimitAdapter.class);

    private final StringRedisTemplate redis;

    public RedisAuthRateLimitAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= limit;
        } catch (DataAccessException exception) {
            logger.warn("认证限流后端不可用，本次放行（fail-open）：key={} 原因={}", key, exception.getMessage());
            return true;
        }
    }
}
