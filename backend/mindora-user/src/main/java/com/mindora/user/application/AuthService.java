package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.domain.AuthResult;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.infrastructure.PasswordHasher;
import com.mindora.user.infrastructure.TokenService;
import com.mindora.user.infrastructure.UserRepository;
import java.util.Set;
import java.util.UUID;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AuthResult register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException("user_email_exists", "Email already exists");
        }
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHasher.hash(rawPassword),
                Set.of(RoleName.USER.value()));
        return new AuthResult(userRepository.save(user), tokenService.issue(user));
    }

    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(
                        "invalid_credentials", "Invalid email or password"));
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new BusinessException("invalid_credentials", "Invalid email or password");
        }
        return new AuthResult(user, tokenService.issue(user));
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
