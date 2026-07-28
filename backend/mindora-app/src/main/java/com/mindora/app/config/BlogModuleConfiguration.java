package com.mindora.app.config;

import com.mindora.blog.application.ArticleService;
import com.mindora.blog.domain.BlogRepository;
import com.mindora.blog.infrastructure.persistence.memory.InMemoryBlogRepository;
import com.mindora.blog.infrastructure.persistence.jdbc.JdbcBlogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlogModuleConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.blog", havingValue = "memory")
    BlogRepository blogRepository() {
        return new InMemoryBlogRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.blog", havingValue = "jdbc", matchIfMissing = true)
    BlogRepository jdbcBlogRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcBlogRepository(jdbcTemplate);
    }

    @Bean
    ArticleService articleService(BlogRepository blogRepository) {
        return new ArticleService(blogRepository);
    }
}
