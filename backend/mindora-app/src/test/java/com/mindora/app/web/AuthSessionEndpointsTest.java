package com.mindora.app.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mindora.app.MindOraApplication;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 会话端点全链路：注册/登录下发 RT Cookie → me → refresh → logout → 旧令牌失效。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void fullSessionLifecycle() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("user"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("mindora_rt="),
                        containsString("Path=/api/v1/auth"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                        containsString("Max-Age=1209600"),
                        not(containsString("Secure")))))
                .andReturn();

        String accessToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.accessToken");
        String refreshToken = cookieValue(registered);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.displayName").value(email.substring(0, email.indexOf('@'))))
                .andExpect(jsonPath("$.data.roles[0]").value("user"));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("mindora_rt=")))
                .andReturn();
        String newAccessToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.data.accessToken");
        String newRefreshToken = cookieValue(refreshed);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, newAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie(newRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_revoked"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(newRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_reused"));
    }

    @Test
    void refreshWithoutCookieReportsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_missing"));
    }

    @Test
    void logoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"));
    }

    @Test
    void refreshRejectsDisabledAccount() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email)))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = cookieValue(registered);

        UserAccount user = userRepository.findByEmail(email).orElseThrow();
        userRepository.save(new UserAccount(
                user.id(), user.email(), user.passwordHash(), user.roles(), user.tokenVersion(),
                "disabled", false, user.displayName()));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("account_disabled"));
    }

    private String body(String email) {
        return """
                {"email":"%s","password":"Password123!"}
                """.formatted(email);
    }

    private Cookie cookie(String value) {
        return new Cookie("mindora_rt", value);
    }

    private String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie == null || !setCookie.startsWith("mindora_rt=")) {
            throw new AssertionError("Missing refresh token cookie: " + setCookie);
        }
        String value = setCookie.substring("mindora_rt=".length());
        int separator = value.indexOf(';');
        return separator < 0 ? value : value.substring(0, separator);
    }
}
