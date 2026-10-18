package com.mindora.user.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    /**
     * 按邮箱读取账号，**不过滤**状态与删除标记：注册据此判断邮箱占用，
     * 登录据此区分「密码错误」与「账号被禁用」。
     */
    Optional<UserAccount> findByEmail(String email);

    /** 按主键读取账号（过滤器、刷新、me 使用）。 */
    Optional<UserAccount> findById(UUID id);

    UserAccount save(UserAccount user);

    /** 令牌版本自增：让该账号所有已签发 access token 立即失效（登出/禁用）。 */
    int bumpTokenVersion(UUID id);
}
