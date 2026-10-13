package com.mindora.rag.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 问答访问日志（一行一条，结构化 key=value）。
 *
 * <p>用途：观察公开端点是否被爬、额度消耗分布、拒答原因构成——这些是限流阈值调优与异常发现的唯一依据。
 *
 * <p><b>不记录</b>完整 prompt、完整上下文与系统提示词（design-04 明令默认不存）；query 只截断保留
 * 前 {@value #QUERY_LIMIT} 字。落库（rag_chat_log 表）属 Stage 4 运维范围，本阶段只出日志行。
 */
public final class RagAccessLog {
    private static final Logger logger = LoggerFactory.getLogger("RAG_ACCESS");
    private static final int QUERY_LIMIT = 100;

    private RagAccessLog() {
    }

    public static void log(
            String endpoint,
            String scope,
            String userId,
            String clientIp,
            String query,
            String status,
            String refusalReason,
            String chatModel,
            long latencyMs,
            long firstDeltaMs,
            int citationCount) {
        logger.info(
                "endpoint={} scope={} user={} ip={} status={} refusal={} model={} latencyMs={} firstDeltaMs={} citations={} query={}",
                endpoint,
                scope,
                userId == null ? "-" : userId,
                clientIp == null ? "-" : clientIp,
                status,
                refusalReason == null ? "-" : refusalReason,
                chatModel == null ? "-" : chatModel,
                latencyMs,
                firstDeltaMs,
                citationCount,
                abbreviate(query));
    }

    private static String abbreviate(String query) {
        if (query == null) {
            return "";
        }
        String collapsed = query.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= QUERY_LIMIT ? collapsed : collapsed.substring(0, QUERY_LIMIT) + "…";
    }
}
