package com.mindora.rag.infrastructure.adapter;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.KnowledgeRetrievalService;
import com.mindora.knowledge.application.KnowledgeService;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索门面：对 mindora-knowledge 的最小封装。
 *
 * <p>只依赖 knowledge 的应用服务（API/facade），不触及其 repository 或 mapper，
 * 符合「业务模块之间只经 API 访问」的硬性边界。
 *
 * <p>负责消化两个接口的 ID 方言差异：检索命中是带连字符的 36 位 UUID，
 * 而切片实体是 UUID、切片接口输出 32 位无连字符。这里统一按
 * {@link RetrievalFacadePort#normalizeId} 归一化后作为 Map 的键。
 */
public class KnowledgeRetrievalFacadeAdapter implements RetrievalFacadePort {
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeService knowledgeService;

    public KnowledgeRetrievalFacadeAdapter(
            KnowledgeRetrievalService retrievalService, KnowledgeService knowledgeService) {
        this.retrievalService = retrievalService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public KnowledgeRetrievalService.RetrievalOutcome search(KnowledgeRetrievalService.RetrievalQuery query) {
        return retrievalService.search(query);
    }

    @Override
    public Map<String, KnowledgeChunk> chunksOf(List<String> documentIds) {
        Map<String, KnowledgeChunk> byChunkId = new LinkedHashMap<>();
        if (documentIds == null || documentIds.isEmpty()) {
            return byChunkId;
        }
        for (String documentId : documentIds) {
            if (documentId == null || documentId.isBlank()) {
                continue;
            }
            java.util.UUID uuid;
            try {
                uuid = PublicIds.toUuid(documentId);
            } catch (IllegalArgumentException exception) {
                // 脏 ID 不应让整次问答失败：跳过该文档，其余照常组装
                continue;
            }
            for (KnowledgeChunk chunk : knowledgeService.listChunks(uuid)) {
                byChunkId.put(RetrievalFacadePort.normalizeId(PublicIds.toPublicId(chunk.id())), chunk);
            }
        }
        return byChunkId;
    }
}
