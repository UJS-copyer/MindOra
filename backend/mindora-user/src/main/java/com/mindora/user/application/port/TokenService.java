package com.mindora.user.application.port;

import com.mindora.user.domain.TokenVerification;
import com.mindora.user.domain.UserAccount;

public interface TokenService {
    /** 签发访问令牌（裸 JWS，前缀由客户端按需拼）。 */
    String issue(UserAccount user);

    /**
     * 校验请求里的 Authorization 头。
     *
     * <p>同时接受 `Bearer <token>` 与裸 `<token>`：前者是标准写法，后者用于
     * 「客户端把登录返回值原样当请求头」的旧前端（Stage 3.6a 过渡期）。
     */
    TokenVerification verify(String authorizationHeader);

    /** Access token 有效期（秒），供 `AuthView.expiresIn` 与前端续期计时使用。 */
    long accessTtlSeconds();
}
