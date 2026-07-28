package com.mindora.app.config;

import com.mindora.user.application.AuthService;
import com.mindora.user.application.AdminIdentityService;
import com.mindora.user.application.RbacPermissionService;
import com.mindora.user.application.UnavailableAdminIdentityGateway;
import com.mindora.user.application.port.AdminIdentityGateway;
import com.mindora.user.application.port.PasswordHasher;
import com.mindora.user.application.port.RbacPermissionGateway;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.UserRepository;
import com.mindora.user.infrastructure.persistence.memory.InMemoryUserRepository;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcAdminIdentityGateway;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcRbacPermissionGateway;
import com.mindora.user.infrastructure.persistence.jdbc.JdbcUserRepository;
import com.mindora.user.infrastructure.security.Sha256PasswordHasher;
import com.mindora.user.infrastructure.security.SimpleJwtTokenService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class UserModuleConfiguration {
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
    PasswordHasher passwordHasher() {
        return new Sha256PasswordHasher();
    }

    @Bean
    TokenService tokenService() {
        return new SimpleJwtTokenService(
                System.getenv().getOrDefault("MINDORA_JWT_SECRET", "local-stage0-secret"),
                Clock.systemUTC());
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
