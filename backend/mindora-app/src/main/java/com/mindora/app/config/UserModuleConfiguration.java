package com.mindora.app.config;

import com.mindora.user.application.AuthService;
import com.mindora.user.infrastructure.InMemoryUserRepository;
import com.mindora.user.infrastructure.PasswordHasher;
import com.mindora.user.infrastructure.Sha256PasswordHasher;
import com.mindora.user.infrastructure.SimpleJwtTokenService;
import com.mindora.user.infrastructure.TokenService;
import com.mindora.user.infrastructure.UserRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserModuleConfiguration {
    @Bean
    UserRepository userRepository() {
        return new InMemoryUserRepository();
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
}
