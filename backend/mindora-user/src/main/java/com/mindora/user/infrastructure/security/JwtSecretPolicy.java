package com.mindora.user.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * JWT 密钥策略（design-12 §6 / §9）。
 *
 * <p>规则：
 * 1. 合法密钥 = 非空白、不是已知占位符、长度 ≥ 32 字符（HS256 要求 256 位）；
 * 2. local 环境且**未配置或仍是占位符** → 生成本次进程的临时密钥（重启即换，需重新登录）；
 * 3. local 环境但**显式配置了过短密钥** → 仍然拒绝启动，避免开发者误以为配置生效；
 * 4. 非 local 环境只要不合法就拒绝启动——默认密钥上线是本阶段要堵的口子。
 */
public final class JwtSecretPolicy {
    public static final int MIN_LENGTH = 32;

    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "change-me",
            "change-me-to-a-long-random-stage0-secret",
            "local-stage0-secret");

    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtSecretPolicy() {
    }

    public record ResolvedSecret(String value, boolean generated) {
    }

    public static ResolvedSecret resolve(String configured, boolean allowEphemeral) {
        String value = configured == null ? "" : configured.trim();
        boolean placeholder = KNOWN_PLACEHOLDERS.contains(value);
        boolean tooShort = value.length() < MIN_LENGTH;
        if (!value.isEmpty() && !placeholder && !tooShort) {
            return new ResolvedSecret(value, false);
        }
        if (!allowEphemeral) {
            throw new IllegalStateException(
                    "JWT secret is missing, a known placeholder, or shorter than "
                            + MIN_LENGTH + " characters; set JWT_SECRET (mindora.auth.jwt.secret) "
                            + "before starting outside the local profile");
        }
        if (!value.isEmpty() && !placeholder) {
            throw new IllegalStateException(
                    "JWT secret is shorter than " + MIN_LENGTH
                            + " characters; set JWT_SECRET (mindora.auth.jwt.secret) to a longer value");
        }
        return new ResolvedSecret(generate(), true);
    }

    private static String generate() {
        byte[] bytes = new byte[MIN_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
