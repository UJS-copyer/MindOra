package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.application.port.AuthRateLimitPort;
import java.time.Duration;

/** 超限即抛 {@code auth_rate_limited}（429），调用方负责给出具体的分桶键与额度。 */
public class AuthRateLimitGuard {
    private final AuthRateLimitPort rateLimitPort;

    public AuthRateLimitGuard(AuthRateLimitPort rateLimitPort) {
        this.rateLimitPort = rateLimitPort;
    }

    public void require(String key, int limit, Duration window) {
        if (!rateLimitPort.tryAcquire(key, limit, window)) {
            throw new BusinessException("auth_rate_limited", "Too many authentication attempts");
        }
    }
}
