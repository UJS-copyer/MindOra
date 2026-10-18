package com.mindora.app.config;

import com.mindora.user.application.AuthService;
import com.mindora.user.application.AdminIdentityService;
import com.mindora.user.application.RbacPermissionService;
import com.mindora.user.application.RefreshTokenService;
import com.mindora.user.application.UnavailableAdminIdentityGateway;
import com.mindora.user.application.port.AdminIdentityGateway;
import com.mindora.user.application.port.AuthRefreshTokenRepository;
import com.mindora.user.application.port.PasswordHasher;
import com.mindora.user.application.port.RbacPermissionGateway;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.UserRepository;
import com.mindora.user.infrastructure.persistence.memory.InMemoryUserRepository;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcAdminIdentityGateway;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcAuthRefreshTokenRepository;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcRbacPermissionGateway;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcUserRepository;
import com.mindora.user.infrastructure.security.JwtSecretPolicy;
import com.mindora.user.infrastructure.security.JwtTokenService;
import com.mindora.user.infrastructure.security.Sha256PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class UserModuleConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserModuleConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "memory")
    UserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "jdbc", matchIfMissing = true)
    UserRepository jdbcUserRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcUserRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "jdbc", matchIfMissing = true)
    AuthRefreshTokenRepository jdbcAuthRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthRefreshTokenRepository(jdbcTemplate);
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new Sha256PasswordHasher();
    }

    @Bean
    TokenService tokenService(
            @Value("${mindora.auth.jwt.secret:}") String configuredSecret,
            @Value("${mindora.auth.access-ttl:PT30M}") Duration accessTtl,
            Environment environment) {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("local"));
        JwtSecretPolicy.ResolvedSecret secret = JwtSecretPolicy.resolve(configuredSecret, localProfile);
        if (secret.generated()) {
            LOGGER.warn("JWT secret is not configured; generated an ephemeral key for this process. "
                    + "Access tokens issued before the next restart will be rejected.");
        }
        return new JwtTokenService(secret.value(), Clock.systemUTC(), accessTtl);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "jdbc", matchIfMissing = true)
    RefreshTokenService refreshTokenService(
            AuthRefreshTokenRepository authRefreshTokenRepository,
            @Value("${mindora.auth.refresh-ttl:P14D}") Duration refreshTtl,
            @Value("${mindora.auth.refresh-absolute-ttl:P30D}") Duration absoluteTtl,
            @Value("${mindora.auth.refresh-grace:PT60S}") Duration grace) {
        return new RefreshTokenService(
                authRefreshTokenRepository, refreshTtl, absoluteTtl, grace, Clock.systemUTC());
    }

    @Bean
    AuthService authService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenService tokenService) {
        return new AuthService(userRepository, passwordHasher, tokenService);
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "jdbc", matchIfMissing = true)
    RbacPermissionGateway rbacPermissionGateway(JdbcTemplate jdbcTemplate) {
        return new JdbcRbacPermissionGateway(jdbcTemplate);
    }

    @Bean
    RbacPermissionService rbacPermissionService(ObjectProvider<RbacPermissionGateway> gateway) {
        return new RbacPermissionService(gateway.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.user", havingValue = "jdbc", matchIfMissing = true)
    AdminIdentityGateway adminIdentityGateway(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        return new JdbcAdminIdentityGateway(jdbcTemplate, passwordHasher);
    }

    @Bean
    @ConditionalOnMissingBean(AdminIdentityGateway.class)
    AdminIdentityGateway unavailableAdminIdentityGateway() {
        return new UnavailableAdminIdentityGateway();
    }

    @Bean
    AdminIdentityService adminIdentityService(AdminIdentityGateway gateway) {
        return new AdminIdentityService(gateway);
    }
}
