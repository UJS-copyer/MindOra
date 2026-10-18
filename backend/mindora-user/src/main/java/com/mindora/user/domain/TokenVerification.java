package com.mindora.user.domain;

/**
 * 访问令牌的校验结果。
 *
 * <p>刻意区分「缺失 / 过期 / 无效」三类失败：前端要用
 * {@code token_expired} 决定「续期后重放」，用 {@code token_invalid} 决定「回登录页」。
 * 本轮（Stage 3.6a a2）过滤器仍把过期与无效当作匿名，401 语义由 a3 落地。
 */
public sealed interface TokenVerification {

    /** 验签通过且未过期。 */
    record Valid(TokenPrincipal principal) implements TokenVerification {
    }

    /** 请求没有携带 Authorization（匿名继续）。 */
    record Missing() implements TokenVerification {
    }

    /** 签名正确但 exp 已过。 */
    record Expired() implements TokenVerification {
    }

    /** 格式错误、签名不匹配、缺少声明或算法不被接受。 */
    record Invalid() implements TokenVerification {
    }
}
