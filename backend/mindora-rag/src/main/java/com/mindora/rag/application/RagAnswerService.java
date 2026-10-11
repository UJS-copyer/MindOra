package com.mindora.rag.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalOutcome;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalQuery;
import com.mindora.knowledge.application.KnowledgeRetrievalService.Scope;
import com.mindora.rag.application.AnswerCitationValidator.ParsedAnswer;
import com.mindora.rag.application.AnswerCitationValidator.ValidationResult;
import com.mindora.rag.application.ContextAssembler.AssembledContext;
import com.mindora.rag.application.ContextAssembler.Segment;
import com.mindora.rag.application.port.RagAdapters.ChatClientPort;
import com.mindora.rag.application.port.RagAdapters.ChatMessage;
import com.mindora.rag.application.port.RagAdapters.ChatOptions;
import com.mindora.rag.application.port.RagAdapters.ChatReply;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.Citation;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import com.mindora.rag.domain.RagDomain.RagAnswerOutcome;
import com.mindora.rag.domain.RagDomain.RagAnswerRequest;
import com.mindora.rag.domain.RagDomain.RefusalReason;
import com.mindora.rag.domain.RagDomain.RetrievalMeta;
import java.time.Instant;
import java.util.List;

/**
 * 问答编排：检索 → 组装上下文 → 调用模型 → 服务端校验 → 产出答案与引用。
 *
 * <p><b>三条降级/拒答语义</b>（与 knowledge 模块的分级原则一致）：
 * <ol>
 *   <li><b>无候选</b>：直接拒答，不调用模型（省钱且防幻觉）；</li>
 *   <li><b>模型不可用</b>：属质量特性降级，返回已检索片段 + 提示语（degraded=true），不是 500；</li>
 *   <li><b>引用校验失败</b>：按「知识库没有记录」拒答（refused=true）。</li>
 * </ol>
 */
public class RagAnswerService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RagAnswerService.class);
    private static final String REFUSAL_TEXT = "知识库中没有找到能回答这个问题的内容。";
    private static final String DEGRADED_PREFIX = "生成模型暂不可用，以下是与问题最相关的知识库片段，请自行查阅：";

    private final RetrievalFacadePort retrieval;
    private final ContextAssembler assembler;
    private final ChatClientPort chatClient;
    private final AnswerCitationValidator validator;
    private final ContextPolicy defaultPolicy;
    private final ChatOptions defaultChatOptions;

    public RagAnswerService(
            RetrievalFacadePort retrieval,
            ContextAssembler assembler,
            ChatClientPort chatClient,
            AnswerCitationValidator validator,
            ContextPolicy defaultPolicy,
            ChatOptions defaultChatOptions) {
        this.retrieval = retrieval;
        this.assembler = assembler;
        this.chatClient = chatClient;
        this.validator = validator;
        this.defaultPolicy = defaultPolicy == null ? ContextPolicy.defaults() : defaultPolicy;
        this.defaultChatOptions = defaultChatOptions == null ? ChatOptions.defaults() : defaultChatOptions;
    }

    public RagAnswerOutcome answer(RagAnswerRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new BusinessException("rag_query_required", "RAG query is required");
        }
        Scope scope = parseScope(request.scope());
        String question = request.query().trim();

        RetrievalOutcome outcome = retrieval.search(new RetrievalQuery(
                question,
                scope,
                request.topK(),
                null,
                request.rerankEnabled(),
                request.keywordEnabled()));

        ContextPolicy policy = policyFor(request);
        AssembledContext context = assembler.assemble(outcome, policy);
        RetrievalMeta meta = new RetrievalMeta(
                outcome.latencyMs(),
                outcome.vectorCandidates(),
                outcome.keywordCandidates(),
                context.segments().size(),
                context.expansionApplied(),
                context.contextChars());

        // 1) 无候选：不调用模型
        if (context.segments().isEmpty()) {
            return refused(meta, RefusalReason.NO_CONTEXT, "no_candidates");
        }

        // 2) 调用模型；不可用时降级为「只给检索片段」
        ChatReply reply;
        try {
            reply = chatClient.chat(
                    List.of(
                            ChatMessage.system(RagPrompt.systemPrompt()),
                            ChatMessage.user(RagPrompt.userMessage(assembler.render(context.segments()), question))),
                    defaultChatOptions);
        } catch (BusinessException exception) {
            if (isChatUnavailable(exception)) {
                return degraded(meta, context, exception);
            }
            throw exception;
        }

        // 3) 服务端确定性校验：引用必须落在本次片段内且 quote 逐字可核
        ParsedAnswer parsed = validator.parse(reply.content());
        ValidationResult validation = validator.validate(parsed, context);
        if (!validation.valid()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "RAG 引用校验未通过：reason={} modelRefused={} citations={} rawReply={}",
                        validation.reason(),
                        parsed.refused(),
                        parsed.citations().size(),
                        abbreviate(reply.content()));
            }
            return refused(meta, refusalReasonOf(validation.reason()), validation.reason());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("RAG 作答成功：模型={} 引用={}（逐字核验 {} 条 / 服务端摘录 {} 条 / 丢弃越界 {} 条）片段={} 上下文={}字",
                    reply.model(),
                    validation.citations().size(),
                    validation.verifiedCount(),
                    validation.snippetCount(),
                    validation.droppedCount(),
                    meta.matchedTopK(),
                    meta.contextChars());
        }
        return new RagAnswerOutcome(
                validation.answer(),
                validation.citations(),
                meta,
                false,
                null,
                null,
                false,
                null,
                reply.model(),
                Instant.now());
    }

    /** 把校验器的细分原因映射为对外枚举：模型主动拒答与引用不达标要能区分开。 */
    private RefusalReason refusalReasonOf(String validationReason) {
        return "model_refused".equals(validationReason)
                ? RefusalReason.MODEL_REFUSED
                : RefusalReason.INVALID_CITATION;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 400 ? collapsed : collapsed.substring(0, 400) + "…";
    }

    private ContextPolicy policyFor(RagAnswerRequest request) {
        if (request.expansion() == null) {
            return defaultPolicy;
        }
        return new ContextPolicy(
                defaultPolicy.budgetChars(),
                request.expansion(),
                defaultPolicy.expansionAddedChars(),
                defaultPolicy.sectionCap());
    }

    private Scope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("rag_scope_required", "RAG scope is required");
        }
        try {
            return Scope.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("rag_scope_invalid", "Unsupported RAG scope: " + raw);
        }
    }

    private boolean isChatUnavailable(BusinessException exception) {
        String code = exception.code();
        return "rag_chat_unavailable".equals(code)
                || "rag_chat_api_key_missing".equals(code)
                || "rag_chat_response_invalid".equals(code);
    }

    private RagAnswerOutcome refused(RetrievalMeta meta, RefusalReason reason, String detail) {
        if (logger.isDebugEnabled()) {
            logger.debug("RAG 拒答：reason={} detail={} 命中片段={}", reason, detail, meta.matchedTopK());
        }
        return new RagAnswerOutcome(
                REFUSAL_TEXT, List.of(), meta, true, reason, detail, false, null, null, Instant.now());
    }

    private RagAnswerOutcome degraded(RetrievalMeta meta, AssembledContext context, BusinessException cause) {
        StringBuilder builder = new StringBuilder(DEGRADED_PREFIX);
        for (Segment segment : context.segments()) {
            builder.append("\n- [").append(segment.index()).append("] ").append(segment.documentTitle());
            if (segment.headingPath() != null && !segment.headingPath().isBlank()) {
                builder.append(" › ").append(segment.headingPath());
            }
        }
        List<Citation> citations = context.segments().stream()
                .map(segment -> new Citation(
                        segment.index(),
                        segment.chunkId(),
                        segment.documentId(),
                        segment.documentTitle(),
                        segment.headingPath(),
                        segment.sourcePath(),
                        segment.articleSlug(),
                        segment.sequence(),
                        snippetOf(segment.text()),
                        false))
                .toList();
        return new RagAnswerOutcome(
                builder.toString(),
                citations,
                meta,
                false,
                null,
                null,
                true,
                cause.code(),
                null,
                Instant.now());
    }

    private String snippetOf(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 120 ? collapsed : collapsed.substring(0, 120);
    }
}
