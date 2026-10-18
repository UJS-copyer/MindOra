package com.mindora.user.application.port;

import java.time.Duration;

/**
 * 认证端点限流端口。
 *
 * <p>与公开问答的限流刻意分开：认证需要三种窗口（注册 1 小时、登录 1 分钟、刷新 1 小时），
 * 而 rag 的端口是构造期固定单窗口；共用需要在同一批里重构 rag 的端口与测试，
 * 属于横切改动，暂不做（出现第三处需求时再抽公共实现）。
 */
public interface AuthRateLimitPort {

    /**
     * 固定窗口计数。
     *
     * @return true 表示本次请求在额度内
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
