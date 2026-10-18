package com.mindora.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 登录限流：同 IP 超过额度返回 429（design-12 §6）。 */
@SpringBootTest(
        classes = MindOraApplication.class,
        properties = {
            "mindora.auth.rate-limit.backend=memory",
            "mindora.auth.rate-limit.login-per-minute=1",
            "mindora.auth.rate-limit.register-per-hour=1000",
            "mindora.auth.rate-limit.refresh-per-hour=1000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String body = """
                {"email":"nobody@example.com","password":"WrongPassword!"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth_rate_limited"));
    }
}
