package com.mindora.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.RagAnswerService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 公开问答限流的端到端行为。
 *
 * <p>两点设计考虑：
 * <ol>
 *   <li>独立测试类 + 低额度 properties（限流计数在同一 Spring 上下文内跨用例累积，混跑会互相干扰）；</li>
 *   <li>每条用例显式指定不同的来源 IP —— 匿名分桶的键就是 IP，不区分 IP 的用例会彼此污染。</li>
 * </ol>
 */
@SpringBootTest(
        classes = MindOraApplication.class,
        properties = {
            "mindora.rag.public.rate-limit.backend=memory",
            "mindora.rag.public.rate-limit.anonymous-per-minute=1",
            "mindora.rag.public.rate-limit.logged-in-per-minute=100"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRagRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    @BeforeEach
    void stubAnswer() {
        when(ragAnswerService.answer(any())).thenReturn(new RagAnswerOutcome(
                "答案正文", List.of(), null, false, null, null, false, null, "test-model", Instant.now()));
        when(ragAnswerService.answerStream(any(), any())).thenReturn(new RagAnswerOutcome(
                "答案正文", List.of(), null, false, null, null, false, null, "test-model", Instant.now()));
    }

    @Test
    void streamEndpointSharesTheSameBucket() throws Exception {
        // 流式端点的预检与 JSON 端点共用同一限流：第一次受理（异步开始），第二次即 429 且是普通 JSON 错误
        mockMvc.perform(streamFrom("10.4.4.1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());

        mockMvc.perform(streamFrom("10.4.4.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rag_rate_limited"));
    }

    private MockHttpServletRequestBuilder streamFrom(String remoteAddr) {
        return post("/api/v1/public/rag/answer/stream")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"检索是怎么做的\"}");
    }

    @Test
    void rejectsAnonymousRequestBeyondLimit() throws Exception {
        mockMvc.perform(askFrom("10.1.1.1")).andExpect(status().isOk());

        mockMvc.perform(askFrom("10.1.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rag_rate_limited"));
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        mockMvc.perform(askFrom("10.2.2.1")).andExpect(status().isOk());
        mockMvc.perform(askFrom("10.2.2.1")).andExpect(status().isTooManyRequests());

        // 换一个来源 IP 不应受上一个 IP 的计数影响
        mockMvc.perform(askFrom("10.2.2.2")).andExpect(status().isOk());
    }

    /**
     * 回归（联调发现）：SSE 客户端只发 {@code Accept: text/event-stream} 时，
     * 限流预检的 429 曾退化成 403 空体——现在必须仍是可读的 JSON 错误。
     */
    @Test
    void 限流预检在仅接受事件流的请求下仍返回JSON_429() throws Exception {
        mockMvc.perform(streamFrom("10.5.5.1").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());

        mockMvc.perform(streamFrom("10.5.5.1").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rag_rate_limited"));
    }

    @Test
    void loggedInCallerGetsItsOwnBucket() throws Exception {
        // 匿名额度先打满（用独立 IP，避免与其它用例互相影响）
        mockMvc.perform(askFrom("10.3.3.1")).andExpect(status().isOk());
        mockMvc.perform(askFrom("10.3.3.1")).andExpect(status().isTooManyRequests());

        // 带合法令牌的登录用户按 userId 分桶，额度独立且更宽
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(askFrom("10.3.3.1")
                            .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.SUPER_ADMIN)))
                    .andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder askFrom(String remoteAddr) {
        return post("/api/v1/public/rag/answer")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"检索是怎么做的\"}");
    }

    private String tokenFor(RoleName role) {
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                role.value() + "-ratelimit@example.com",
                "hash",
                Set.of(role.value()));
        userRepository.save(user);
        return tokenService.issue(user);
    }
}
