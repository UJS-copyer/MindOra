package com.mindora.app.config;

import com.mindora.user.application.AuthService;
import com.mindora.user.application.AdminIdentityService;
import com.mindora.user.application.RbacPermissionService;
import com.mindora.user.infrastructure.InMemoryUserRepository;
import com.mindora.user.infrastructure.JdbcUserRepository;
import com.mindora.user.infrastructure.PasswordHasher;
import com.mindora.user.infrastructure.Sha256PasswordHasher;
import com.mindora.user.infrastructure.SimpleJwtTokenService;
import com.mindora.user.infrastructure.TokenService;
import com.mindora.user.infrastructure.UserRepository;
import java.time.Clock;
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
    RbacPermissionService rbacPermissionService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        return new RbacPermissionService(jdbcTemplate.getIfAvailable());
    }

    @Bean
    AdminIdentityService adminIdentityService(
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            PasswordHasher passwordHasher) {
        return new AdminIdentityService(jdbcTemplate.getIfAvailable(), passwordHasher);
    }
}
