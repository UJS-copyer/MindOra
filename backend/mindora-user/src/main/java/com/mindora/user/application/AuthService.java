package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import com.mindora.user.application.port.PasswordHasher;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.IssuedRefreshToken;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.util.Set;
import java.util.UUID;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenService tokenService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResult register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        // 不过滤状态：禁用/删除账号的邮箱仍算占用，避免重新注册把账号「复活」。
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException("user_email_exists", "Email already exists");
        }
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHasher.hash(rawPassword),
                Set.of(RoleName.USER.value()));
        return resultOf(userRepository.save(user));
    }

    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(
                        "invalid_credentials", "Invalid email or password"));
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new BusinessException("invalid_credentials", "Invalid email or password");
        }
        return resultOf(user);
    }

    private AuthResult resultOf(UserAccount user) {
        IssuedRefreshToken refreshToken =
                refreshTokenService.issue(PublicIds.toPublicId(user.id()), user.tokenVersion());
        return new AuthResult(user, tokenService.issue(user), refreshToken);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("validation_error", "Email is required");
        }
        if (isInvalidEmail(email)) {
            throw new BusinessException("validation_error", "Email is invalid");
        }
        return email.trim().toLowerCase();
    }

    private boolean isInvalidEmail(String email) {
        return !email.contains("@");
    }
}
