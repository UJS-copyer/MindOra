package com.mindora.user.domain;

import java.util.Set;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        Set<String> roles,
        int tokenVersion,
        String status,
        boolean deleted,
        String displayName) {

    /**
     * 注册与测试场景的默认账户：启用、未删除、无显示名、令牌版本 0。
     *
     * <p>`token_version` 用于「一次 UPDATE 让该账号所有已签发令牌失效」
     * （登出全端失效、禁用账号）；`status`/`deleted` 由登录与每请求校验读取。
     */
    public UserAccount(UUID id, String email, String passwordHash, Set<String> roles) {
        this(id, email, passwordHash, roles, 0, "active", false, null);
    }

    public UserAccount(UUID id, String email, String passwordHash, Set<String> roles, int tokenVersion) {
        this(id, email, passwordHash, roles, tokenVersion, "active", false, null);
    }

    /** 账号当前是否可用（登录与每请求校验共用同一判定）。 */
    public boolean usable() {
        return !deleted && "active".equals(status);
    }
}
