package com.mindora.rag.application;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalHit;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalOutcome;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文组装器：检索命中 → 全文补全 → 可选扩展 → 预算截断 → Lost-in-the-Middle 排序 → 编号分段。
 *
 * <p><b>为什么需要扩展</b>：离线实验（docs/rag-retrieval-improvement-survey-cn.md §3.1）证明
 * 答案常常躺在已召回切片的隔壁（邻居 ±1 + 同章节 ≤4、新增 ≤1500 字符使
 * SpanHit@8 88.1% → 91.6%，且优于单纯把 k 提到 20）。
 *
 * <p><b>为什么按 Lost in the Middle 排序</b>：arXiv 2307.03172 的结论是相关信息放在
 * 上下文首尾最好、放在中间显著变差——因此最强证据放开头，次强证据放结尾，其余按序居中。
 *
 * <p><b>ID 处理</b>：检索接口给带连字符的 36 位 ID、切片接口给无连字符的 32 位 ID，
 * 内部一律用 {@link RetrievalFacadePort#normalizeId} 归一化后查表；对外输出统一为
 * 32 位无连字符形式（{@link PublicIds#toPublicId}），与切片查询接口保持一致。
 */
public class ContextAssembler {
    private final RetrievalFacadePort retrieval;

    public ContextAssembler(RetrievalFacadePort retrieval) {
        this.retrieval = retrieval;
    }

    /** 一段进入提示词的上下文。 */
    public record Segment(
            int index,
            String chunkId,
            String documentId,
            String documentTitle,
            String headingPath,
            String sourcePath,
            String articleSlug,
            int sequence,
            boolean expanded,
            String text) {
    }

    /** 组装结果。{@code fullChunks} 的键为归一化后的 chunkId，供引用校验使用。 */
    public record AssembledContext(
            List<Segment> segments,
            boolean expansionApplied,
            int contextChars,
            Map<String, KnowledgeChunk> fullChunks) {
    }

    public AssembledContext assemble(RetrievalOutcome outcome, ContextPolicy policy) {
        ContextPolicy effective = policy == null ? ContextPolicy.defaults() : policy;
        List<RetrievalHit> hits = outcome == null || outcome.results() == null
                ? List.of()
                : outcome.results();
        if (hits.isEmpty()) {
            return new AssembledContext(List.of(), false, 0, Map.of());
        }

        // 命中索引：归一化 chunkId -> hit（同一切片只保留第一次出现的排名）
        Map<String, RetrievalHit> hitsById = new LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            String key = RetrievalFacadePort.normalizeId(hit.chunkId());
            if (key != null) {
                hitsById.putIfAbsent(key, hit);
            }
        }

        // 1) 全文补全：contentPreview 只有 300 字，不足以支撑生成
        List<String> documentIds = hits.stream()
                .map(RetrievalHit::documentId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, KnowledgeChunk> fullChunks = retrieval.chunksOf(documentIds);
        if (fullChunks.isEmpty()) {
            return new AssembledContext(List.of(), false, 0, Map.of());
        }

        // 2) 命中切片按检索顺序（只保留确实取到全文的）
        LinkedHashSet<String> hitIds = new LinkedHashSet<>();
        for (String key : hitsById.keySet()) {
            if (fullChunks.containsKey(key)) {
                hitIds.add(key);
            }
        }
        if (hitIds.isEmpty()) {
            return new AssembledContext(List.of(), false, 0, fullChunks);
        }

        // 3) 可选扩展（邻居 ±1 + 同章节），并受「新增字符预算」约束
        List<String> expandedIds = List.of();
        boolean expansionApplied = false;
        Map<String, RetrievalHit> provenance = Map.of();
        if (effective.expansionEnabled()) {
            Expansion expansion = expand(hitIds, fullChunks, effective, hitsById);
            provenance = expansion.provenance();
            expandedIds = applyBudget(expansion.addedIds(), fullChunks, effective.expansionAddedChars());
            expansionApplied = !expandedIds.isEmpty();
        }

        List<String> allIds = new ArrayList<>(hitIds);
        for (String id : expandedIds) {
            if (!allIds.contains(id)) {
                allIds.add(id);
            }
        }

        // 4) 总预算截断
        if (charsOf(allIds, fullChunks) > effective.budgetChars()) {
            allIds = truncateByBudget(allIds, fullChunks, effective.budgetChars());
        }

        List<Segment> segments = buildSegments(allIds, fullChunks, hitsById, provenance);
        int contextChars = segments.stream().mapToInt(segment -> segment.text().length()).sum();
        return new AssembledContext(segments, expansionApplied, contextChars, fullChunks);
    }

    /** 扩展结果：新增的切片 id（最近优先），以及每个新增切片来自哪条命中（用于继承文档信息）。 */
    private record Expansion(List<String> addedIds, Map<String, RetrievalHit> provenance) {
    }

    /**
     * 邻居 ±1 + 同章节（每命中 ≤ sectionCap），最近优先、去重、排除已命中切片。
     *
     * <p>同时记录来源命中：扩展切片必然与触发它的命中属于**同一个文档版本**，
     * 因此可以直接继承文档 id/标题/路径——{@code KnowledgeChunk} 只有 documentVersionId，
     * 若拿版本 id 冒充文档 id，前端按它查切片接口会报「文档不存在」。
     */
    private Expansion expand(
            LinkedHashSet<String> hitIds,
            Map<String, KnowledgeChunk> fullChunks,
            ContextPolicy policy,
            Map<String, RetrievalHit> hitsById) {
        List<String> added = new ArrayList<>();
        Map<String, RetrievalHit> provenance = new LinkedHashMap<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>(hitIds);

        // 邻居优先（距离最近）
        for (String hitId : hitIds) {
            KnowledgeChunk chunk = fullChunks.get(hitId);
            if (chunk == null) {
                continue;
            }
            RetrievalHit source = hitsById.get(hitId);
            for (Map.Entry<String, KnowledgeChunk> entry : fullChunks.entrySet()) {
                KnowledgeChunk candidate = entry.getValue();
                if (candidate.documentVersionId().equals(chunk.documentVersionId())
                        && Math.abs(candidate.sequence() - chunk.sequence()) == 1
                        && seen.add(entry.getKey())) {
                    added.add(entry.getKey());
                    if (source != null) {
                        provenance.put(entry.getKey(), source);
                    }
                }
            }
        }

        // 同章节其次，按与该命中的距离由近到远
        for (String hitId : hitIds) {
            KnowledgeChunk chunk = fullChunks.get(hitId);
            if (chunk == null) {
                continue;
            }
            RetrievalHit source = hitsById.get(hitId);
            List<Map.Entry<String, KnowledgeChunk>> sameSection = fullChunks.entrySet().stream()
                    .filter(entry -> entry.getValue().documentVersionId().equals(chunk.documentVersionId()))
                    .filter(entry -> equalsNullable(entry.getValue().headingPath(), chunk.headingPath()))
                    .filter(entry -> entry.getValue().sequence() != chunk.sequence())
                    .sorted(Comparator.comparingInt(
                            entry -> Math.abs(entry.getValue().sequence() - chunk.sequence())))
                    .toList();
            int perHit = 0;
            for (Map.Entry<String, KnowledgeChunk> entry : sameSection) {
                if (perHit >= policy.sectionCap()) {
                    break;
                }
                if (seen.add(entry.getKey())) {
                    added.add(entry.getKey());
                    if (source != null) {
                        provenance.put(entry.getKey(), source);
                    }
                    perHit++;
                }
            }
        }
        return new Expansion(added, provenance);
    }

    /** 新增预算：按最近优先累加，超出即跳过（不再尝试更靠后的）。 */
    private List<String> applyBudget(
            List<String> candidates, Map<String, KnowledgeChunk> fullChunks, int addedBudget) {
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (String id : candidates) {
            KnowledgeChunk chunk = fullChunks.get(id);
            if (chunk == null) {
                continue;
            }
            int cost = charsOf(chunk);
            if (used + cost > addedBudget) {
                continue;
            }
            used += cost;
            kept.add(id);
        }
        return kept;
    }

    private List<String> truncateByBudget(
            List<String> ids, Map<String, KnowledgeChunk> fullChunks, int budgetChars) {
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (String id : ids) {
            KnowledgeChunk chunk = fullChunks.get(id);
            if (chunk == null) {
                continue;
            }
            int cost = charsOf(chunk);
            if (used + cost > budgetChars) {
                break;
            }
            used += cost;
            kept.add(id);
        }
        return kept;
    }

    /**
     * 分段并编号。段序按 Lost in the Middle：最强证据（检索首位）留在开头，
     * 次强（检索第二位）移到结尾，其余维持原序居中。
     *
     * <p>扩展片段没有自己的命中记录，其文档信息继承自触发扩展的那条命中
     * （{@code provenance}）——绝不能拿 documentVersionId 冒充 documentId。
     */
    private List<Segment> buildSegments(
            List<String> ids,
            Map<String, KnowledgeChunk> fullChunks,
            Map<String, RetrievalHit> hitsById,
            Map<String, RetrievalHit> provenance) {
        List<Segment> direct = new ArrayList<>();
        for (String id : ids) {
            KnowledgeChunk chunk = fullChunks.get(id);
            if (chunk == null) {
                continue;
            }
            RetrievalHit hit = hitsById.get(id);
            boolean expanded = hit == null;
            RetrievalHit source = expanded ? provenance.get(id) : hit;
            direct.add(new Segment(
                    0,
                    PublicIds.toPublicId(chunk.id()),
                    source == null ? "" : normalizePublicId(source.documentId()),
                    source == null || source.documentTitle() == null ? "" : source.documentTitle(),
                    chunk.headingPath(),
                    source == null || source.sourcePath() == null ? "" : source.sourcePath(),
                    source == null || source.articleSlug() == null ? "" : source.articleSlug(),
                    chunk.sequence(),
                    expanded,
                    chunk.content()));
        }

        List<Segment> ordered;
        if (direct.size() <= 2) {
            ordered = direct;
        } else {
            List<Segment> reordered = new ArrayList<>();
            reordered.add(direct.get(0));
            reordered.addAll(direct.subList(2, direct.size()));
            reordered.add(direct.get(1));
            ordered = reordered;
        }
        List<Segment> numbered = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            numbered.add(withIndex(ordered.get(index), index + 1));
        }
        return numbered;
    }

    private Segment withIndex(Segment segment, int index) {
        return new Segment(
                index, segment.chunkId(), segment.documentId(), segment.documentTitle(),
                segment.headingPath(), segment.sourcePath(), segment.articleSlug(),
                segment.sequence(), segment.expanded(), segment.text());
    }

    /** 把任意格式的公共 ID 统一成 32 位无连字符；非法格式原样返回，避免因脏数据抛异常。 */
    private String normalizePublicId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        try {
            return PublicIds.toPublicId(PublicIds.toUuid(id));
        } catch (IllegalArgumentException exception) {
            return id;
        }
    }

    private int charsOf(List<String> ids, Map<String, KnowledgeChunk> fullChunks) {
        int total = 0;
        for (String id : ids) {
            KnowledgeChunk chunk = fullChunks.get(id);
            if (chunk != null) {
                total += charsOf(chunk);
            }
        }
        return total;
    }

    private int charsOf(KnowledgeChunk chunk) {
        return chunk.characterCount() > 0 ? chunk.characterCount() : chunk.content().length();
    }

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /** 供提示词使用：把分段渲染为带编号与来源说明的文本块。 */
    public String render(List<Segment> segments) {
        return segments.stream()
                .map(segment -> {
                    String source = segment.documentTitle() == null || segment.documentTitle().isBlank()
                            ? ""
                            : "（来源：" + segment.documentTitle()
                                    + (segment.headingPath() == null || segment.headingPath().isBlank()
                                            ? ""
                                            : " › " + segment.headingPath())
                                    + "）";
                    return "[" + segment.index() + "]" + source + "\n" + segment.text();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
