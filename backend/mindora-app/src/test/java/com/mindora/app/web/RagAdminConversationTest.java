package com.mindora.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.ChatConversationService;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * 管理端问答接入会话域（3.6b b2）：
 * 首次提问创建会话、追问注入同一会话历史、非法 id 不报错、persist=false 不写库。
 *
 * <p>只 mock 领域问答服务，会话仓储用测试环境的进程内实现，因此能真实断言「写了什么/没写什么」。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagAdminConversationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatConversationService chatConversationService;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    private UserAccount admin;
    private String authorization;

    @BeforeEach
    void setUp() {
        admin = new UserAccount(
                UUID.randomUUID(),
                "rag-admin-" + UUID.randomUUID() + "@example.com",
                "hash",
                Set.of(RoleName.SUPER_ADMIN.value()));
        userRepository.save(admin);
        authorization = tokenService.issue(admin);
        when(ragAnswerService.answer(any())).thenReturn(new RagAnswerOutcome(
                "答案正文",
                List.of(),
                null,
                false,
                null,
                null,
                false,
                null,
                "test-model",
                Instant.now()));
    }

    @Test
    void firstAnswerCreatesConversationAndReturnsId() throws Exception {
        String body = answer("{\"query\":\"第一问\"}");

        String conversationId = JsonPath.read(body, "$.data.conversationId");
        Assertions.assertThat(conversationId).isNotBlank();
        Assertions.assertThat(conversationsOfAdmin()).hasSize(1);
        Assertions.assertThat(capturedRequest().history()).isEmpty();
    }

    @Test
    void followUpCarriesHistoryAndReusesConversationId() throws Exception {
        String first = answer("{\"query\":\"第一问\"}");
        String conversationId = JsonPath.read(first, "$.data.conversationId");

        String second = answer(
                "{\"query\":\"第二问\",\"conversationId\":\"" + conversationId + "\"}");

        String echoedId = JsonPath.read(second, "$.data.conversationId");
        Assertions.assertThat(echoedId).isEqualTo(conversationId);
        ArgumentCaptor<RagAnswerRequest> captor = ArgumentCaptor.forClass(RagAnswerRequest.class);
        verify(ragAnswerService, times(2)).answer(captor.capture());
        RagAnswerRequest followUp = captor.getAllValues().get(1);
        Assertions.assertThat(followUp.history()).hasSize(2);
        Assertions.assertThat(followUp.history().get(0).role()).isEqualTo("user");
        Assertions.assertThat(followUp.history().get(0).content()).isEqualTo("第一问");
        Assertions.assertThat(followUp.history().get(1).role()).isEqualTo("assistant");
    }

    @Test
    void invalidConversationIdIsTreatedAsNoConversation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"带坏 id 的问题\",\"conversationId\":\"not-a-uuid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty())
                .andReturn();

        // 不报错，且落库成一个新会话（不是复用坏 id）
        Assertions.assertThat(conversationsOfAdmin()).hasSize(1);
        Assertions.assertThat(capturedRequest().history()).isEmpty();
        Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("not-a-uuid");
    }

    @Test
    void persistFalseReadsHistoryButWritesNothing() throws Exception {
        String first = answer("{\"query\":\"先建一个会话\"}");
        String conversationId = JsonPath.read(first, "$.data.conversationId");

        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"只复测不落库\",\"conversationId\":\""
                                + conversationId + "\",\"persist\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));

        // 历史仍被读取（复测需要上下文），但消息数没有增加（首问 = 2 条）
        ArgumentCaptor<RagAnswerRequest> captor = ArgumentCaptor.forClass(RagAnswerRequest.class);
        verify(ragAnswerService, times(2)).answer(captor.capture());
        Assertions.assertThat(captor.getAllValues().get(1).history()).hasSize(2);
        Assertions.assertThat(conversationsOfAdmin().getFirst().messageCount()).isEqualTo(2);
    }

    @Test
    void persistFalseWithoutConversationReturnsNoIdAndCreatesNothing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"一次性验证\",\"persist\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").doesNotExist());

        Assertions.assertThat(conversationsOfAdmin()).isEmpty();
    }

    private String answer(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/rag/answer")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private RagAnswerRequest capturedRequest() {
        ArgumentCaptor<RagAnswerRequest> captor = ArgumentCaptor.forClass(RagAnswerRequest.class);
        verify(ragAnswerService, times(1)).answer(captor.capture());
        return captor.getValue();
    }

    private List<com.mindora.rag.application.port.RagAdapters.ChatConversationRepository.ConversationSummary>
            conversationsOfAdmin() {
        // CallerIdentityPort 以 UUID 字符串作为会话归属键（与公开端点一致）
        return chatConversationService.listConversations(admin.id().toString());
    }
}
