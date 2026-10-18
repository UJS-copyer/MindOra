package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.AuthRefreshToken;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.IssuedRefreshToken;
import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 会话端点（refresh / logout / me）的应用服务。
 *
 * <p>刷新顺序刻意固定：先校验刷新令牌本身 → 再校验账户状态与 `tv` → 最后才轮换，
 * 避免「已登出/已禁用账号仍能换到新令牌」。
 */
public class AuthSessionService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimitGuard rateLimitGuard;
    private final int refreshPerHour;

    public AuthSessionService(
            UserRepository userRepository,
            TokenService tokenService,
            RefreshTokenService refreshTokenService,
            AuthRateLimitGuard rateLimitGuard,
            int refreshPerHour) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimitGuard = rateLimitGuard;
        this.refreshPerHour = refreshPerHour;
    }

    public AuthResult refresh(String rawRefreshToken) {
        AuthRefreshToken current = refreshTokenService.requireActive(rawRefreshToken);
        // 按用户计（design-12 §6），先于账户读取，避免禁用账号反复打库
        rateLimitGuard.require(
                "auth:refresh:u:" + current.userId(), refreshPerHour, Duration.ofHours(1));
        UUID userId = PublicIds.toUuid(current.userId());
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("token_invalid", "Account no longer exists"));
        if (!user.usable()) {
            throw new BusinessException("account_disabled", "Account is disabled");
        }
        if (user.tokenVersion() != current.tokenVersion()) {
            refreshTokenService.revokeFamily(current.familyId(), "token_revoked");
            throw new BusinessException("token_revoked", "Refresh token has been revoked");
        }
        IssuedRefreshToken next = refreshTokenService.rotate(current);
        return new AuthResult(user, tokenService.issue(user), next);
    }

    /** 幂等：Cookie 缺失/已失效也视为成功（前端随后清状态）。 */
    public void logout(String rawRefreshToken) {
        refreshTokenService.find(rawRefreshToken).ifPresent(current -> {
            userRepository.bumpTokenVersion(PublicIds.toUuid(current.userId()));
            refreshTokenService.revokeAllForUser(current.userId(), "logout");
        });
    }

    public AccountProfile me(TokenPrincipal principal) {
        UserAccount user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException("token_invalid", "Account no longer exists"));
        if (!user.usable()) {
            throw new BusinessException("account_disabled", "Account is disabled");
        }
        return new AccountProfile(
                PublicIds.toPublicId(user.id()),
                user.email(),
                displayName(user),
                user.roles());
    }

    private String displayName(UserAccount user) {
        String name = user.displayName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String email = user.email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    public record AccountProfile(String userId, String email, String displayName, Set<String> roles) {
    }
}
