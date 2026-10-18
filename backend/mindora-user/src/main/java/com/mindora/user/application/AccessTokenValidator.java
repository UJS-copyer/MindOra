package com.mindora.user.application;

import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.TokenVerification;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.util.Optional;

/**
 * 受保护请求的身份校验（design-12 §7）。
 *
 * <p>验签之后还要读一次账户：<br>
 * ① 账户不存在 → {@code token_invalid}；<br>
 * ② 已删除或非 active → {@code account_disabled}（禁用即时生效）；<br>
 * ③ JWT 的 {@code tv} 与库中 {@code token_version} 不一致 → {@code token_revoked}（登出/撤销）。
 */
public class AccessTokenValidator {

    public enum Status {
        MISSING,
        VALID,
        EXPIRED,
        INVALID,
        ACCOUNT_DISABLED,
        TOKEN_REVOKED
    }

    public record Outcome(Status status, TokenPrincipal principal) {

        static Outcome of(Status status) {
            return new Outcome(status, null);
        }

        public boolean authenticated() {
            return status == Status.VALID;
        }

        /** 写进 401 响应体的错误码；VALID/MISSING 不产生错误体。 */
        public String errorCode() {
            return switch (status) {
                case EXPIRED -> "token_expired";
                case INVALID -> "token_invalid";
                case ACCOUNT_DISABLED -> "account_disabled";
                case TOKEN_REVOKED -> "token_revoked";
                case MISSING, VALID -> null;
            };
        }
    }

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AccessTokenValidator(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    public Outcome validate(String authorizationHeader) {
        TokenVerification verification = tokenService.verify(authorizationHeader);
        if (verification instanceof TokenVerification.Missing) {
            return Outcome.of(Status.MISSING);
        }
        if (verification instanceof TokenVerification.Expired) {
            return Outcome.of(Status.EXPIRED);
        }
        if (verification instanceof TokenVerification.Invalid) {
            return Outcome.of(Status.INVALID);
        }
        TokenPrincipal principal = ((TokenVerification.Valid) verification).principal();
        Optional<UserAccount> account = userRepository.findById(principal.userId());
        if (account.isEmpty()) {
            return Outcome.of(Status.INVALID);
        }
        if (!account.get().usable()) {
            return Outcome.of(Status.ACCOUNT_DISABLED);
        }
        if (account.get().tokenVersion() != principal.tokenVersion()) {
            return Outcome.of(Status.TOKEN_REVOKED);
        }
        return new Outcome(Status.VALID, principal);
    }
}
