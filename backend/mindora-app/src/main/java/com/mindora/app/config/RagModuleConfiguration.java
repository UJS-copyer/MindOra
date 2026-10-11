package com.mindora.app.config;

import com.mindora.knowledge.application.KnowledgeRetrievalService;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.rag.application.AnswerCitationValidator;
import com.mindora.rag.application.ContextAssembler;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import com.mindora.rag.infrastructure.adapter.KnowledgeRetrievalFacadeAdapter;
import com.mindora.rag.infrastructure.adapter.OpenAiCompatibleChatAdapter;
import com.mindora.rag.infrastructure.adapter.UnavailableRagAdapters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生成层 Bean 装配。
 *
 * <p>沿用 knowledge 的范式：真实实现用 {@code @ConditionalOnProperty(matchIfMissing = true)}，
 * 降级实现用 {@code @ConditionalOnMissingBean}。关闭 {@code mindora.rag.chat.enabled}
 * 时会注入 {@link UnavailableRagAdapters.ChatClient}，问答接口因此返回「只给检索片段」的
 * 降级结果而不是启动失败——测试环境无需真实密钥。
 */
@Configuration
public class RagModuleConfiguration {

    @Bean
    @ConditionalOnProperty(name = "mindora.rag.chat.enabled", havingValue = "true", matchIfMissing = true)
    ChatClientPort chatClientPort(
            @Value("${mindora.rag.chat.provider:siliconflow}") String provider,
            @Value("${mindora.rag.chat.base-url:${RAG_CHAT_BASE_URL:https://api.siliconflow.cn/v1}}") String baseUrl,
            @Value("${mindora.rag.chat.api-key:${RAG_CHAT_API_KEY:}}") String apiKey,
            @Value("${mindora.rag.chat.model:${RAG_CHAT_MODEL:Qwen/Qwen3-30B-A3B-Instruct-2507}}") String model,
            @Value("${mindora.rag.chat.timeout-ms:${RAG_CHAT_TIMEOUT_MS:15000}}") int timeoutMillis) {
        return new OpenAiCompatibleChatAdapter(provider, baseUrl, apiKey, model, timeoutMillis);
    }

    @Bean
    @ConditionalOnMissingBean
    ChatClientPort unavailableChatClientPort() {
        return new UnavailableRagAdapters.ChatClient();
    }

    @Bean
    @ConditionalOnMissingBean
    RetrievalFacadePort retrievalFacadePort(
            KnowledgeRetrievalService retrievalService, KnowledgeService knowledgeService) {
        return new KnowledgeRetrievalFacadeAdapter(retrievalService, knowledgeService);
    }

    @Bean
    @ConditionalOnMissingBean
    ContextAssembler ragContextAssembler(RetrievalFacadePort retrievalFacadePort) {
        return new ContextAssembler(retrievalFacadePort);
    }

    @Bean
    @ConditionalOnMissingBean
    AnswerCitationValidator answerCitationValidator(
            @Value("${mindora.rag.validation.require-verbatim-quote:true}") boolean requireVerbatimQuote) {
        return new AnswerCitationValidator(requireVerbatimQuote);
    }

    /**
     * 问答编排服务。
     *
     * <p>上下文预算与扩展参数默认取离线实验的拐点值：
     * 邻居 ±1 + 同章节 ≤4、新增 ≤1500 字符（SpanHit@8 88.1% → 91.6%）。
     */
    @Bean
    @ConditionalOnMissingBean
    RagAnswerService ragAnswerService(
            RetrievalFacadePort retrievalFacadePort,
            ContextAssembler ragContextAssembler,
            ChatClientPort chatClientPort,
            AnswerCitationValidator answerCitationValidator,
            @Value("${mindora.rag.context.budget-chars:${RAG_CONTEXT_BUDGET:4000}}") int budgetChars,
            @Value("${mindora.rag.context.expansion-enabled:${RAG_CONTEXT_EXPANSION:true}}") boolean expansionEnabled,
            @Value("${mindora.rag.context.expansion-added-chars:${RAG_EXPANSION_ADDED:1500}}") int expansionAddedChars,
            @Value("${mindora.rag.context.section-cap:${RAG_SECTION_CAP:4}}") int sectionCap,
            @Value("${mindora.rag.chat.temperature:0.2}") double temperature,
            @Value("${mindora.rag.chat.max-tokens:2048}") int maxTokens) {
        return new RagAnswerService(
                retrievalFacadePort,
                ragContextAssembler,
                chatClientPort,
                answerCitationValidator,
                new ContextPolicy(budgetChars, expansionEnabled, expansionAddedChars, sectionCap),
                new ChatOptions(null, temperature, maxTokens, null));
    }
}
