package com.mindora.app.config;

import com.mindora.knowledge.application.KnowledgeRetrievalService;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.rag.application.AnswerCitationValidator;
import com.mindora.rag.application.ContextAssembler;
import com.mindora.rag.application.RagAnswerService;
import com.mindora.rag.api.web.RagStreamTaskExecutor;
import com.mindora.rag.application.port.RagAdapters.CallerIdentityPort;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.RateLimitPort;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import com.mindora.rag.infrastructure.adapter.InMemoryRateLimitAdapter;
import com.mindora.rag.infrastructure.adapter.KnowledgeRetrievalFacadeAdapter;
import com.mindora.rag.infrastructure.adapter.OpenAiCompatibleChatAdapter;
import com.mindora.rag.infrastructure.adapter.RedisRateLimitAdapter;
import com.mindora.rag.infrastructure.adapter.UnavailableRagAdapters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(RagModuleConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "mindora.rag.chat.enabled", havingValue = "true", matchIfMissing = true)
    ChatClientPort chatClientPort(
            @Value("${mindora.rag.chat.provider:siliconflow}") String provider,
            @Value("${mindora.rag.chat.base-url:${RAG_CHAT_BASE_URL:https://api.siliconflow.cn/v1}}") String baseUrl,
            @Value("${mindora.rag.chat.api-key:${RAG_CHAT_API_KEY:}}") String apiKey,
            @Value("${mindora.rag.chat.model:${RAG_CHAT_MODEL:Qwen/Qwen3-30B-A3B-Instruct-2507}}") String model,
            @Value("${mindora.rag.chat.timeout-ms:${RAG_CHAT_TIMEOUT_MS:15000}}") int timeoutMillis) {
        if (apiKey == null || apiKey.isBlank()) {
            // 静默降级太难排查（现象只是「对话模型不可用」），启动期就把原因喊出来
            logger.warn("RAG 对话模型密钥为空（mindora.rag.chat.api-key / RAG_CHAT_API_KEY）：问答会走降级路径，"
                    + "只返回检索片段。本地开发可在 config/mindora-local.yml 里配置 "
                    + "mindora.knowledge.rerank.api-key 作为兜底（同一 SiliconFlow 账号）。");
        }
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
     * 限流后端（生产默认 Redis）。声明顺序在内存实现之前：
     * {@link ConditionalOnMissingBean} 依赖已注册的 Bean 定义，顺序反了会两个都建或都不建。
     */
    @Bean
    @ConditionalOnProperty(name = "mindora.rag.public.rate-limit.backend", havingValue = "redis", matchIfMissing = true)
    RateLimitPort redisRateLimitPort(
            StringRedisTemplate redisTemplate,
            @Value("${mindora.rag.public.rate-limit.window-seconds:60}") long windowSeconds) {
        return new RedisRateLimitAdapter(redisTemplate, windowSeconds);
    }

    /** 测试环境与单实例无 Redis 部署：进程内固定窗口。 */
    @Bean
    @ConditionalOnMissingBean
    RateLimitPort inMemoryRateLimitPort(
            @Value("${mindora.rag.public.rate-limit.window-seconds:60}") long windowSeconds) {
        return new InMemoryRateLimitAdapter(windowSeconds, System::currentTimeMillis);
    }

    /**
     * SSE 流式任务的专用线程池。
     *
     * <p>有界（队列容量 0 + 最大线程数上限）：流式任务会长时间占用线程等待上游逐帧返回，
     * 无界队列等于把站点拖垮。超出容量时抛 {@code TaskRejectedException}，由控制器回一个
     * {@code server_busy} 事件（明确告知，而不是静默断流）。
     */
    @Bean
    RagStreamTaskExecutor ragStreamExecutor(
            @Value("${mindora.rag.public.stream.executor-core:2}") int coreSize,
            @Value("${mindora.rag.public.stream.executor-max:16}") int maxSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("rag-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return new RagStreamTaskExecutor(executor);
    }

    /**
     * 调用者身份适配器：读 Spring Security 的 {@code SecurityContextHolder}。
     *
     * <p>mindora-rag 不依赖 Spring Security，身份经 {@link CallerIdentityPort} 端口传入；
     * 匿名请求会被 {@code anonymous()} 配置成 {@code anonymousUser}，这里统一归一为「无身份」。
     */
    @Bean
    @ConditionalOnMissingBean
    CallerIdentityPort callerIdentityPort() {
        return () -> {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            Object principal = authentication == null ? null : authentication.getPrincipal();
            // 只认真实登录：匿名请求的 principal 是字符串 anonymousUser，不构成身份
            if (principal instanceof com.mindora.user.domain.TokenPrincipal tokenPrincipal
                    && tokenPrincipal.userId() != null) {
                return java.util.Optional.of(tokenPrincipal.userId().toString());
            }
            return java.util.Optional.empty();
        };
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
            @Value("${mindora.rag.chat.max-tokens:2048}") int maxTokens,
            @Value("${mindora.rag.security.secret-guard.enabled:true}") boolean secretGuardEnabled) {
        return new RagAnswerService(
                retrievalFacadePort,
                ragContextAssembler,
                chatClientPort,
                answerCitationValidator,
                new ContextPolicy(budgetChars, expansionEnabled, expansionAddedChars, sectionCap),
                new ChatOptions(null, temperature, maxTokens, null),
                secretGuardEnabled);
    }
}
