package com.mindora.app.config;

import com.mindora.blog.application.ArticleService;
import com.mindora.blog.application.BlogRepository;
import com.mindora.blog.application.InMemoryBlogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlogModuleConfiguration {
    @Bean
    BlogRepository blogRepository() {
        return new InMemoryBlogRepository();
    }

    @Bean
    ArticleService articleService(BlogRepository blogRepository) {
        return new ArticleService(blogRepository);
    }
}
