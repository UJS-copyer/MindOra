package com.mindora.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalHit;
import com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalOutcome;
import com.mindora.knowledge.domain.KnowledgeChunk;
import com.mindora.knowledge.domain.KnowledgeEnums;
import com.mindora.rag.application.port.RagAdapters.RetrievalFacadePort;
import com.mindora.rag.domain.RagDomain.ContextPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 上下文组装器单测：全文补全、扩展（邻居 ±1 与同章节）、新增预算、总预算截断、
 * Lost-in-the-Middle 排序、编号连续性、ID 方言归一化。
 */
class ContextAssemblerTest {

    private static final UUID VERSION = UUID.randomUUID();
    private static final String DOC_A = PublicIds.newId();

    /** 造一个切片：sequence 决定邻居关系，headingPath 决定同章节关系。 */
    private static KnowledgeChunk chunk(int sequence, String heading, String content) {
        return new KnowledgeChunk(
                UUID.randomUUID(), VERSION, sequence, content, content.length(),
                heading, 2, KnowledgeEnums.BlockType.PARAGRAPH, null, "hash",
                KnowledgeEnums.IndexStatus.INDEXED, "model", "point", null, 0, Instant.now());
    }

    /** 检索命中里的 documentId/chunkId 都是带连字符的 36 位形式（与真实接口一致）。 */
    private static RetrievalHit hit(KnowledgeChunk chunk, String documentId, String title, int rank) {
        return new RetrievalHit(
                chunk.id().toString(),
                documentId,
                title,
                "gitee",
                "工作/x.md",
                chunk.headingPath(),
                "paragraph",
                null,
                0.9d,
                0.8d,
                0.03d,
                rank,
                rank,
                "both",
                chunk.content().substring(0, Math.min(30, chunk.content().length())),
                null,
                null);
    }

    /** facade：按归一化 chunkId 建索引，与真实适配器行为一致。 */
    private static RetrievalFacadePort facade(List<KnowledgeChunk> chunks) {
        return new RetrievalFacadePort() {
            @Override
            public RetrievalOutcome search(
                    com.mindora.knowledge.application.KnowledgeRetrievalService.RetrievalQuery query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, KnowledgeChunk> chunksOf(List<String> documentIds) {
                Map<String, KnowledgeChunk> all = new LinkedHashMap<>();
                for (KnowledgeChunk chunk : chunks) {
                    all.put(RetrievalFacadePort.normalizeId(PublicIds.toPublicId(chunk.id())), chunk);
                }
                return all;
            }
        };
    }

    private static RetrievalOutcome outcome(List<RetrievalHit> hits) {
        return new RetrievalOutcome(
                "all", 30, 30, 20, 8, true, true, 30, 30, false, null, false, null, 12L, hits);
    }

    @Test
    void returnsEmptyWhenNoHits() {
        ContextAssembler assembler = new ContextAssembler(facade(List.of()));
        var assembled = assembler.assemble(outcome(List.of()), ContextPolicy.defaults());
        assertThat(assembled.segments()).isEmpty();
        assertThat(assembled.contextChars()).isZero();
        assertThat(assembled.expansionApplied()).isFalse();
    }

    @Test
    void 补齐全文而不是使用三百字预览() {
        String longContent = "内容".repeat(400); // 800 字符，远超 300 字预览
        KnowledgeChunk target = chunk(5, "章节A", longContent);
        ContextAssembler assembler = new ContextAssembler(facade(List.of(target)));

        var assembled = assembler.assemble(
                outcome(List.of(hit(target, DOC_A, "文档A", 1))),
                new ContextPolicy(4000, false, 1500, 4));

        assertThat(assembled.segments()).hasSize(1);
        assertThat(assembled.segments().get(0).text()).isEqualTo(longContent);
        assertThat(assembled.segments().get(0).documentTitle()).isEqualTo("文档A");
        // 对外 ID 统一为 32 位无连字符
        assertThat(assembled.segments().get(0).chunkId()).isEqualTo(PublicIds.toPublicId(target.id()));
        assertThat(assembled.segments().get(0).documentId()).isEqualTo(DOC_A);
    }

    @Test
    void 扩展邻居正负一且排除已命中() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 6; sequence++) {
            chunks.add(chunk(sequence, "章节" + sequence, "第" + sequence + "段内容，用于占位。"));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(2); // sequence = 3

        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(4000, true, 1500, 0));

        // 邻居 ±1 = sequence 2 与 4；同章节关闭（sectionCap=0）
        assertThat(assembled.segments()).hasSize(3);
        assertThat(assembled.segments()).extracting(segment -> segment.sequence())
                .containsExactlyInAnyOrder(2, 3, 4);
        assertThat(assembled.expansionApplied()).isTrue();
    }

    @Test
    void 扩展受新增字符预算约束() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 4; sequence++) {
            chunks.add(chunk(sequence, "章节" + sequence, "x".repeat(100)));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(1); // sequence = 2

        // 每个邻居 100 字符，新增预算只允许再加 1 个
        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(4000, true, 100, 0));

        assertThat(assembled.segments()).hasSize(2);
    }

    @Test
    void 同章节扩展受sectionCap限制() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        chunks.add(chunk(1, "同一章节", "命中段"));
        for (int sequence = 2; sequence <= 8; sequence++) {
            chunks.add(chunk(sequence, "同一章节", "同章节补充段" + sequence));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(0); // sequence = 1

        // 邻居：只有 sequence 2（1 个）；同章节 cap=2 → 再补 2 个
        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(40000, true, 40000, 2));

        assertThat(assembled.segments()).hasSize(4); // 命中1 + 邻居1 + 同章节2
    }

    @Test
    void 总预算触发截断() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 5; sequence++) {
            chunks.add(chunk(sequence, "章节" + sequence, "y".repeat(200)));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(2);

        // 总预算 450：命中的 200 + 一个邻居 200 = 400 可容纳，第二个邻居放不下
        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(450, true, 1500, 0));

        assertThat(assembled.contextChars()).isLessThanOrEqualTo(450);
        assertThat(assembled.segments()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 三段以上按lostInTheMiddle排序且编号连续() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 4; sequence++) {
            chunks.add(chunk(sequence, "章节" + sequence, "第" + sequence + "段"));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(1); // sequence = 2

        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(40000, true, 4000, 0));

        List<Integer> indexes = assembled.segments().stream().map(segment -> segment.index()).toList();
        assertThat(indexes).containsExactly(1, 2, 3);
        // 最强证据（命中的 sequence 2）保持在首位
        assertThat(assembled.segments().get(0).sequence()).isEqualTo(2);
    }

    @Test
    void 扩展片段继承来源命中的文档信息而不是用版本id冒充() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int sequence = 1; sequence <= 3; sequence++) {
            chunks.add(chunk(sequence, "章节", "第" + sequence + "段内容，用于占位。"));
        }
        ContextAssembler assembler = new ContextAssembler(facade(chunks));
        KnowledgeChunk hitChunk = chunks.get(1); // sequence = 2，邻居为 1 与 3

        var assembled = assembler.assemble(
                outcome(List.of(hit(hitChunk, DOC_A, "文档A", 1))),
                new ContextPolicy(40000, true, 40000, 0));

        assertThat(assembled.segments()).hasSize(3);
        for (var segment : assembled.segments()) {
            // 每一段（含扩展段）的 documentId 都必须是文档 id，且绝不能等于版本 id
            assertThat(segment.documentId()).isEqualTo(DOC_A);
            assertThat(segment.documentId()).isNotEqualTo(PublicIds.toPublicId(VERSION));
            assertThat(segment.documentTitle()).isEqualTo("文档A");
            assertThat(segment.sourcePath()).isEqualTo("工作/x.md");
        }
        // 只有非命中段被标记为 expanded
        assertThat(assembled.segments()).filteredOn(segment -> !segment.expanded()).hasSize(1);
    }

    @Test
    void 渲染包含编号与来源标题() {
        KnowledgeChunk target = chunk(7, "项目配置信息", "Nacos 地址在这里");
        ContextAssembler assembler = new ContextAssembler(facade(List.of(target)));

        var assembled = assembler.assemble(
                outcome(List.of(hit(target, DOC_A, "风险与隐患工点管理后端", 1))),
                new ContextPolicy(4000, false, 1500, 4));
        String rendered = assembler.render(assembled.segments());

        assertThat(rendered).startsWith("[1]");
        assertThat(rendered).contains("风险与隐患工点管理后端");
        assertThat(rendered).contains("项目配置信息");
        assertThat(rendered).contains("Nacos 地址在这里");
    }

    @Test
    void 全文索引键为归一化形式且可被带连字符命中查到() {
        KnowledgeChunk target = chunk(1, "章节", "内容");
        ContextAssembler assembler = new ContextAssembler(facade(List.of(target)));

        var assembled = assembler.assemble(
                outcome(List.of(hit(target, DOC_A, "文档A", 1))),
                new ContextPolicy(4000, false, 1500, 4));

        // 命中索引里只保留了真正取到全文的切片
        assertThat(assembled.fullChunks()).hasSize(1);
        assertThat(assembled.fullChunks().keySet().iterator().next())
                .isEqualTo(RetrievalFacadePort.normalizeId(target.id().toString()));
    }
}
