package com.mindora.user.domain;

import java.util.Set;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        Set<String> roles,
        int tokenVersion) {

    /**
     * 默认令牌版本：注册与测试场景使用。
     *
     * <p>`token_version` 用于「一次 UPDATE 让该账号所有已签发令牌失效」
     * （登出全端失效、禁用账号），a3 才会真正自增它；在此之前恒为 0。
     */
    public UserAccount(UUID id, String email, String passwordHash, Set<String> roles) {
        this(id, email, passwordHash, roles, 0);
    }
}
