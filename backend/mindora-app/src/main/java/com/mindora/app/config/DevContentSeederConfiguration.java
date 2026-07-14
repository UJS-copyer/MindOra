package com.mindora.app.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
public class DevContentSeederConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mindora.seed.enabled", havingValue = "true")
    ApplicationRunner devContentSeeder(DataSource dataSource) {
        return args -> new ResourceDatabasePopulator(new ClassPathResource("db/seed/dev-content.sql"))
                .execute(dataSource);
    }
}
