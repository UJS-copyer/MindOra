package com.mindora.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import com.mindora.user.infrastructure.security.JwtTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 401/403 必须是有 code 的 JSON 体（design-12 §4.1）。
 *
 * <p>这些响应以前由 Spring 默认实现返回空体，前端无法区分「过期续期」与「回登录页」。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthErrorEnvelopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Value("${mindora.auth.jwt.secret}")
    private String jwtSecret;

    @Test
    void missingTokenOnProtectedPathReturnsJson() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_missing"))
                .andExpect(jsonPath("$.traceId").value("untracked"));

        mockMvc.perform(get("/api/v1/admin/me").header("X-Trace-Id", "trace-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_missing"))
                .andExpect(jsonPath("$.traceId").value("trace-401"));
    }

    @Test
    void malformedTokenReportsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me").header(HttpHeaders.AUTHORIZATION, "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_invalid"));
    }

    @Test
    void expiredTokenReportsExpired() throws Exception {
        UserAccount admin = user(RoleName.SUPER_ADMIN, "active", false);
        userRepository.save(admin);
        JwtTokenService expiredIssuer = new JwtTokenService(
                jwtSecret,
                Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC),
                Duration.ofMinutes(30));

        mockMvc.perform(get("/api/v1/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, expiredIssuer.issue(admin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_expired"));
    }

    @Test
    void disabledAccountReportsAccountDisabled() throws Exception {
        UserAccount disabled = user(RoleName.SUPER_ADMIN, "disabled", false);
        userRepository.save(disabled);

        mockMvc.perform(get("/api/v1/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, tokenService.issue(disabled)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("account_disabled"));
    }

    @Test
    void bumpedTokenVersionReportsRevoked() throws Exception {
        UserAccount admin = user(RoleName.SUPER_ADMIN, "active", false);
        userRepository.save(admin);
        String token = tokenService.issue(admin);
        userRepository.bumpTokenVersion(admin.id());

        mockMvc.perform(get("/api/v1/admin/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_revoked"));
    }

    @Test
    void nonAdminAuthenticatedUserGetsJsonForbidden() throws Exception {
        UserAccount user = user(RoleName.USER, "active", false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, tokenService.issue(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    private UserAccount user(RoleName role, String status, boolean deleted) {
        return new UserAccount(
                UUID.randomUUID(), role.value() + "@envelope.example.com", "hash",
                Set.of(role.value()), 0, status, deleted, null);
    }
}
