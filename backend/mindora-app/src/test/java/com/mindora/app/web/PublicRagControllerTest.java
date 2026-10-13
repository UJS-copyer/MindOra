package com.mindora.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 公开问答端点的入口约束：scope 由服务端硬编码、请求体收窄、参数校验。
 *
 * <p>把 {@link RagAnswerService} 换成 mock 的目的是<b>断言控制器实际交给领域层的请求</b>——
 * 公开问答最关键的安全属性是「客户端说了不算」，只有捕获入参才能证明这一点。
 */
@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    @BeforeEach
    void stubAnswer() {
        when(ragAnswerService.answer(any())).thenReturn(new RagAnswerOutcome(
                "答案正文", List.of(), null, false, null, null, false, null, "test-model", Instant.now()));
    }

    @Test
    void hardcodesPublicScopeEvenWhenClientSuppliesScope() throws Exception {
        // 请求体里塞 scope/topK 等管理端旋钮：控制器视图没有这些字段，Jackson 直接忽略
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"检索是怎么做的\",\"scope\":\"all\",\"topK\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("答案正文"));

        RagAnswerRequest captured = captureRequest();
        org.assertj.core.api.Assertions.assertThat(captured.scope()).isEqualTo("public");
        org.assertj.core.api.Assertions.assertThat(captured.query()).isEqualTo("检索是怎么做的");
        // 公开端点必须开启密钥兜底
        org.assertj.core.api.Assertions.assertThat(captured.secretGuard()).isTrue();
        // 调试旋钮不得被透传
        org.assertj.core.api.Assertions.assertThat(captured.topK()).isNull();
        org.assertj.core.api.Assertions.assertThat(captured.rerankEnabled()).isNull();
    }

    @Test
    void rejectsTooShortQuery() throws Exception {
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void rejectsTooLongQuery() throws Exception {
        String longQuery = "问".repeat(501);
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + longQuery + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void rejectsBlankQueryWithoutBody() throws Exception {
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void trimsQueryBeforeDispatch() throws Exception {
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  向量检索怎么做的  \"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captureRequest().query()).isEqualTo("向量检索怎么做的");
    }

    /**
     * 回归：错误分派路径 {@code /error} 此前落在 {@code anyRequest().authenticated()} 里，
     * 匿名请求的任何错误（404/500/内容协商失败）都会被改写成 403 空体。
     */
    @Test
    void 错误分派路径对匿名放行() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/error"))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as("/error 不能落进认证链（403/401），否则真实错误码全部丢失")
                .isNotIn(401, 403);
    }

    private RagAnswerRequest captureRequest() {
        ArgumentCaptor<RagAnswerRequest> captor = ArgumentCaptor.forClass(RagAnswerRequest.class);
        verify(ragAnswerService).answer(captor.capture());
        return captor.getValue();
    }
}
