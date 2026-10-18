package com.mindora.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 校验检索调试接口的 fail-closed 入口约束。
 *
 * <p>这些用例都在触达向量库之前就失败，因此不依赖外部 Qdrant 是否可用。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeRetrievalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rejectsMissingScope() throws Exception {
        search("{\"query\":\"AQS 是什么\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("retrieval_scope_required"));
    }

    @Test
    void rejectsInvalidScope() throws Exception {
        search("{\"query\":\"AQS\",\"scope\":\"secret\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("retrieval_scope_invalid"));
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        search("{\"query\":\"   \",\"scope\":\"public\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("retrieval_query_required"));
    }

    private ResultActions search(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/knowledge/retrieval/search")
                .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.SUPER_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String tokenFor(RoleName role) {
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                role.value() + "@example.com",
                "hash",
                Set.of(role.value()));
        userRepository.save(user);
        return tokenService.issue(user);
    }
}
