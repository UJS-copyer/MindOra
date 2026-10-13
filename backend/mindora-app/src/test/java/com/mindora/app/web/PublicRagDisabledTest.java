package com.mindora.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 公开问答总开关关闭时的行为：端点等同于不存在（404），不暴露「曾经开放过」的信息。
 *
 * <p>对应 design-04 的策略开关 {@code chat.public.enabled}。
 */
@SpringBootTest(classes = MindOraApplication.class, properties = "mindora.rag.public.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRagDisabledTest {

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
    void returnsNotFoundWhenPublicAnswerDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/public/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"检索是怎么做的\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("rag_public_disabled"));
    }
}
