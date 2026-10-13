package com.mindora.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.common.exception.BusinessException;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RefusalReason;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 公开 SSE 端点的**事件契约**验证：delta* → citation* → done（或 error）恰一终态。
 *
 * <p>把 {@link RagAnswerService} 换成 mock：本用例要验证的是控制器的流式编排与事件形状，
 * 而不是检索/生成链路（那由 rag 模块单测与实机联调覆盖）。mock 同步触发 delta 回调，
 * 断言时轮询响应体直到出现终态事件。
 */
@SpringBootTest(
        classes = MindOraApplication.class,
        properties = {
            "mindora.rag.public.rate-limit.backend=memory",
            "mindora.rag.public.rate-limit.anonymous-per-minute=1000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRagStreamControllerTest {

    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    @Test
    void 正常作答时依次发出delta_citation_done() throws Exception {
        when(ragAnswerService.answerStream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("向量检索");
            consumer.accept("与关键词检索[1]");
            return outcome("向量检索与关键词检索[1]", List.of(citation(1)), false, null, false);
        });

        MvcResult result = mockMvc.perform(stream("检索是怎么做的"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitTerminalEvent(result);

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        assertThat(body).contains("\"type\":\"delta\"");
        assertThat(body).contains("向量检索");
        assertThat(body).contains("\"type\":\"citation\"");
        assertThat(body).contains("\"type\":\"done\"");
        assertThat(body).contains("\"refused\":false");
        assertThat(body).contains("\"latencyMs\"");
        // 顺序：delta 在 citation 与 done 之前
        assertThat(body.indexOf("\"type\":\"delta\"")).isLessThan(body.indexOf("\"type\":\"citation\""));
        assertThat(body.indexOf("\"type\":\"citation\"")).isLessThan(body.indexOf("\"type\":\"done\""));
    }

    @Test
    void 拒答时零delta且done标记拒答原因() throws Exception {
        when(ragAnswerService.answerStream(any(), any()))
                .thenReturn(outcome("知识库中没有找到能回答这个问题的内容。", List.of(), true,
                        RefusalReason.NO_CONTEXT, false));

        MvcResult result = mockMvc.perform(stream("库里没有的问题"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitTerminalEvent(result);

        assertThat(body).doesNotContain("\"type\":\"delta\"");
        assertThat(body).doesNotContain("\"type\":\"citation\"");
        assertThat(body).contains("\"type\":\"done\"");
        assertThat(body).contains("\"refused\":true");
        assertThat(body).contains("\"refusalReason\":\"no_context\"");
    }

    @Test
    void 密钥兜底命中时以拒答事件收尾() throws Exception {
        when(ragAnswerService.answerStream(any(), any())).thenReturn(outcome(
                "知识库中没有找到能回答这个问题的内容。", List.of(), true, RefusalReason.PROTECTED_CONTENT, false));

        MvcResult result = mockMvc.perform(stream("配置是什么"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitTerminalEvent(result);

        assertThat(body).contains("\"type\":\"done\"");
        assertThat(body).contains("\"refusalReason\":\"protected_content\"");
    }

    @Test
    void 降级时仍给出引用并标记degraded() throws Exception {
        when(ragAnswerService.answerStream(any(), any()))
                .thenReturn(outcome("生成模型暂不可用，以下是与问题最相关的知识库片段：", List.of(citation(1)), false, null, true));

        MvcResult result = mockMvc.perform(stream("检索是怎么做的"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitTerminalEvent(result);

        assertThat(body).contains("\"type\":\"citation\"");
        assertThat(body).contains("\"type\":\"done\"");
        assertThat(body).contains("\"degraded\":true");
    }

    @Test
    void 意外失败时发出error终态事件() throws Exception {
        when(ragAnswerService.answerStream(any(), any()))
                .thenThrow(new BusinessException("rag_unexpected", "boom"));

        MvcResult result = mockMvc.perform(stream("检索是怎么做的"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitTerminalEvent(result);

        assertThat(body).contains("\"type\":\"error\"");
        assertThat(body).contains("rag_stream_error");
    }

    @Test
    void 参数非法时返回普通JSON错误而不是事件流() throws Exception {
        mockMvc.perform(stream("a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /**
     * 回归（联调发现的真实缺陷）：SSE 客户端按惯例只发 {@code Accept: text/event-stream}。
     * 若错误响应依赖内容协商，就会协商失败 → 分派到 /error → 被安全链拦成 403 空体，
     * 调用方拿不到任何错误码。修复点：错误响应显式声明 JSON + 放行 /error。
     */
    @Test
    void 预检失败在仅接受事件流的请求下仍返回JSON错误() throws Exception {
        mockMvc.perform(post("/api/v1/public/rag/answer/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder stream(String query) {
        return post("/api/v1/public/rag/answer/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + query + "\"}");
    }

    /** 轮询响应体直到出现终态事件（done 或 error）。SSE 规范要求 UTF-8，Mock 响应默认按 ISO-8859-1 解码，需显式指定。 */
    private String awaitTerminalEvent(MvcResult result) throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            if (body.contains("\"type\":\"done\"") || body.contains("\"type\":\"error\"")) {
                return body;
            }
            Thread.sleep(20L);
        }
        return body;
    }

    private static RagAnswerOutcome outcome(
            String answer,
            List<Citation> citations,
            boolean refused,
            RefusalReason refusalReason,
            boolean degraded) {
        return new RagAnswerOutcome(
                answer,
                citations,
                null,
                refused,
                refusalReason,
                refusalReason == null ? null : refusalReason.name().toLowerCase(),
                degraded,
                degraded ? "rag_chat_unavailable" : null,
                degraded ? null : "test-model",
                Instant.now());
    }

    private static Citation citation(int index) {
        return new Citation(
                index,
                "ce7587f3ee0c4735bccdf38a9a39ead6",
                "70fbf0bf49b44b20979c7ae6196e7333",
                "个人知识库开发记录",
                "检索设计",
                "工作/个人知识库开发记录.md",
                null,
                7,
                "向量检索与关键词检索通过 RRF 融合",
                true);
    }
}
