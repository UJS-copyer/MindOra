package com.mindora.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.ChatConversationService;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 会话 CRUD 迁移后的用户域约束（3.6b b3）：
 * 登录态、按 userId 隔离、他人/不存在 404、旧公开路径已删除。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagConversationControllerTest {

    private static final RagAnswerOutcome OUTCOME =
            new RagAnswerOutcome("答案", List.of(), null, false, null, null, false, null, "test-model", Instant.now());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatConversationService chatConversationService;

    private UserAccount alice;
    private UserAccount bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        alice = user("alice");
        bob = user("bob");
        aliceToken = tokenService.issue(alice);
        bobToken = tokenService.issue(bob);
    }

    @Test
    void listOnlyReturnsOwnConversations() throws Exception {
        String conversationId = seedConversation(alice, "Alice 的第一问");

        mockMvc.perform(get("/api/v1/rag/conversations").header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(conversationId));

        mockMvc.perform(get("/api/v1/rag/conversations").header(HttpHeaders.AUTHORIZATION, bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void detailIs404ForForeignConversation() throws Exception {
        String conversationId = seedConversation(alice, "Alice 的私密会话");

        mockMvc.perform(get("/api/v1/rag/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bobToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("chat_conversation_not_found"));

        mockMvc.perform(get("/api/v1/rag/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2));
    }

    @Test
    void deleteIsScopedToOwner() throws Exception {
        String conversationId = seedConversation(alice, "待删除会话");

        mockMvc.perform(delete("/api/v1/rag/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bobToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/rag/conversations").header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/v1/rag/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rag/conversations").header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/rag/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/rag/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_missing"));

        mockMvc.perform(delete("/api/v1/rag/conversations/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyPublicConversationPathIsGone() throws Exception {
        mockMvc.perform(get("/api/v1/public/rag/conversations"))
                .andExpect(status().isNotFound());
    }

    private String seedConversation(UserAccount owner, String question) {
        return chatConversationService.recordTurn(
                null, owner.id().toString(), question, OUTCOME);
    }

    private UserAccount user(String name) {
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                name + "-" + UUID.randomUUID() + "@example.com",
                "hash",
                Set.of(RoleName.USER.value()));
        userRepository.save(user);
        return user;
    }
}
