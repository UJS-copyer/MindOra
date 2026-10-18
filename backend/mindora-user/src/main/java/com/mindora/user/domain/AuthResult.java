package com.mindora.user.domain;

/**
 * 登录/注册/刷新成功后返回给端点的结果：用户、访问令牌、以及只回传一次的刷新令牌。
 */
public record AuthResult(
        UserAccount user,
        String accessToken,
        IssuedRefreshToken refreshToken) {
}
