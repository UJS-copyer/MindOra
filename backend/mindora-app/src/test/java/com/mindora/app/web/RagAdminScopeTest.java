package com.mindora.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理端入口的权限与 scope 白名单（3.6b b4）：
 * 只允许 all/public；USER 进不来；匿名 401。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagAdminScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = tokenFor(RoleName.SUPER_ADMIN);
        userToken = tokenFor(RoleName.USER);
        when(ragAnswerService.answer(any())).thenReturn(new RagAnswerOutcome(
                "答案", List.of(), null, false, null, null, false, null, "test-model", Instant.now()));
    }

    @Test
    void rejectsUnknownScopeBeforeCallingDomain() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"越界 scope\",\"scope\":\"secret\",\"persist\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("rag_scope_invalid"));

        verify(ragAnswerService, never()).answer(any());
    }

    @Test
    void acceptsPublicScopeForAdminRetest() throws Exception {
        answer("{\"query\":\"按公开域复测\",\"scope\":\"public\",\"persist\":false}");

        Assertions.assertThat(captured().scope()).isEqualTo("public");
    }

    @Test
    void defaultsToAllScope() throws Exception {
        answer("{\"query\":\"全库调试\",\"persist\":false}");

        Assertions.assertThat(captured().scope()).isEqualTo("all");
    }

    @Test
    void requiresSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"普通用户不能调试\",\"persist\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"匿名\",\"persist\":false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_missing"));
    }

    private void answer(String body) throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private RagAnswerRequest captured() {
        ArgumentCaptor<RagAnswerRequest> captor = ArgumentCaptor.forClass(RagAnswerRequest.class);
        verify(ragAnswerService).answer(captor.capture());
        return captor.getValue();
    }

    private String tokenFor(RoleName role) {
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                role.value() + "-" + UUID.randomUUID() + "@example.com",
                "hash",
                Set.of(role.value()));
        userRepository.save(user);
        return tokenService.issue(user);
    }
}
