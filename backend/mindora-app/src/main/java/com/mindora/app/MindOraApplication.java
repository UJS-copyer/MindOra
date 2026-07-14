package com.mindora.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.mindora",
        exclude = UserDetailsServiceAutoConfiguration.class)
public class MindOraApplication {
    public static void main(String[] args) {
        SpringApplication.run(MindOraApplication.class, args);
    }
}
