package com.mindora.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindora.knowledge.domain.ChunkDraft;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    private static ChunkingConfig config(int chunkSize, int overlap, int minimumSize, int maximumSize) {
        return new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, chunkSize, overlap, minimumSize, maximumSize, true, true);
    }

    @Test
    void tracksHeadingPathAndLevelAcrossSections() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                # 后端技术
                引言内容

                ## Spring Boot
                ### AQS
                AQS 是并发包的基础。

                #### 深层
                更深的说明
                """, ChunkingConfig.defaults());

        assertThat(drafts).hasSize(3);
        assertThat(drafts.get(0).content()).isEqualTo("引言内容");
        assertThat(drafts.get(0).headingPath()).isEqualTo("后端技术");
        assertThat(drafts.get(0).headingLevel()).isEqualTo(1);
        assertThat(drafts.get(1).headingPath()).isEqualTo("后端技术 > Spring Boot > AQS");
        assertThat(drafts.get(1).headingLevel()).isEqualTo(3);
        assertThat(drafts.get(2).headingPath()).isEqualTo("后端技术 > Spring Boot > AQS > 深层");
        assertThat(drafts.get(2).headingLevel()).isEqualTo(4);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.blockType())
                .isEqualTo(KnowledgeEnums.BlockType.PARAGRAPH));
    }

    @Test
    void preambleWithoutHeadingHasNoHeadingPath() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("前言段落，尚未出现标题。", ChunkingConfig.defaults());

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).headingPath()).isNull();
        assertThat(drafts.get(0).headingLevel()).isZero();
        assertThat(drafts.get(0).embeddingText()).isEqualTo("前言段落，尚未出现标题。");
    }

    @Test
    void keepsLongFencedCodeBlockAtomicAndCapturesLanguage() {
        MarkdownChunker chunker = new MarkdownChunker();
        String body = "x".repeat(400);

        List<ChunkDraft> drafts = chunker.chunk("# 代码示例\n\n```java\n" + body + "\n```\n", config(100, 0, 1, 100));

        assertThat(drafts).hasSize(1);
        ChunkDraft draft = drafts.get(0);
        assertThat(draft.blockType()).isEqualTo(KnowledgeEnums.BlockType.CODE);
        assertThat(draft.codeLanguage()).isEqualTo("java");
        assertThat(draft.content()).contains(body);
        assertThat(draft.content().length()).isGreaterThan(100);
        assertThat(draft.headingPath()).isEqualTo("代码示例");
    }

    @Test
    void keepsUnclosedFenceAsCodeBlock() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("```python\nprint(1)\nprint(2)\n", config(100, 0, 1, 400));

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).blockType()).isEqualTo(KnowledgeEnums.BlockType.CODE);
        assertThat(drafts.get(0).codeLanguage()).isEqualTo("python");
        assertThat(drafts.get(0).content()).contains("print(2)");
    }

    @Test
    void detectsFenceDirectlyAfterParagraphWithoutBlankLine() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                说明文字紧随代码块
                ```java
                class A {}
                ```
                """, config(100, 0, 1, 400));

        // 没有空行分隔时，围栏代码块也必须被识别，不能被并进普通段落
        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).blockType()).isEqualTo(KnowledgeEnums.BlockType.PARAGRAPH);
        assertThat(drafts.get(0).content()).isEqualTo("说明文字紧随代码块");
        assertThat(drafts.get(1).blockType()).isEqualTo(KnowledgeEnums.BlockType.CODE);
        assertThat(drafts.get(1).codeLanguage()).isEqualTo("java");
        assertThat(drafts.get(1).content()).contains("class A {}");
    }

    @Test
    void detectsHeadingDirectlyAfterParagraphWithoutBlankLine() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                第一段正文
                ## 紧邻的小节
                小节正文
                """, config(100, 0, 1, 400));

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).headingPath()).isNull();
        assertThat(drafts.get(0).content()).isEqualTo("第一段正文");
        assertThat(drafts.get(1).headingPath()).isEqualTo("紧邻的小节");
        assertThat(drafts.get(1).content()).isEqualTo("小节正文");
    }

    @Test
    void recognizesSetextHeadings() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                一级标题
                ========
                正文一

                二级标题
                --------
                正文二
                """, ChunkingConfig.defaults());

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).headingPath()).isEqualTo("一级标题");
        assertThat(drafts.get(0).headingLevel()).isEqualTo(1);
        assertThat(drafts.get(0).content()).isEqualTo("正文一");
        assertThat(drafts.get(1).headingPath()).isEqualTo("一级标题 > 二级标题");
        assertThat(drafts.get(1).headingLevel()).isEqualTo(2);
        assertThat(drafts.get(1).content()).isEqualTo("正文二");
    }

    @Test
    void skipsThematicBreaksInsteadOfEmittingNoiseChunks() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                第一段

                ---

                第二段
                """, ChunkingConfig.defaults());

        // 相邻同类型文本块按设计合并为一个切片；关键是分隔线本身不再产生只含符号的噪声切片
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).content()).contains("第一段").contains("第二段");
        assertThat(drafts.get(0).content()).doesNotContain("---");
    }

    @Test
    void fixedSizeStrategyIgnoresSectionBoundaries() {
        MarkdownChunker chunker = new MarkdownChunker();
        String markdown = "# A\n\n" + "a".repeat(150) + "\n\n# B\n\n" + "b".repeat(150) + "\n";

        List<ChunkDraft> bySection = chunker.chunk(markdown, new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, 400, 0, 1, 400, true, true));
        List<ChunkDraft> bySize = chunker.chunk(markdown, new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.FIXED_SIZE, 400, 0, 1, 400, true, true));

        // 章节策略：每个标题下的内容各自成块
        assertThat(bySection).hasSize(2);
        assertThat(bySection.get(0).headingPath()).isEqualTo("A");
        assertThat(bySection.get(1).headingPath()).isEqualTo("B");

        // 定长策略：忽略章节边界，两段合并为一个切片，元数据取起始位置所在章节
        assertThat(bySize).hasSize(1);
        assertThat(bySize.get(0).headingPath()).isEqualTo("A");
        assertThat(bySize.get(0).content()).contains("a".repeat(150)).contains("b".repeat(150));
    }

    @Test
    void fixedSizeStrategyAssignsMetadataByChunkStartSection() {
        MarkdownChunker chunker = new MarkdownChunker();
        String markdown = "# A\n\n" + "a".repeat(300) + "\n\n# B\n\n" + "b".repeat(300) + "\n";

        List<ChunkDraft> drafts = chunker.chunk(markdown, new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.FIXED_SIZE, 400, 0, 1, 400, true, true));

        // 跨章节合并后按「切片起始位置」归属章节，而不是一律使用第一个章节
        assertThat(drafts).hasSizeGreaterThan(1);
        assertThat(drafts.get(0).headingPath()).isEqualTo("A");
        assertThat(drafts.get(1).headingPath()).isEqualTo("B");
    }

    @Test
    void doesNotTreatConsecutiveThematicBreaksAsSetextHeadings() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                正文

                ---

                ---

                后续
                """, ChunkingConfig.defaults());

        assertThat(drafts).isNotEmpty();
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.headingPath()).isNull());
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content()).doesNotContain("---"));
    }

    @Test
    void repeatsTableHeaderWhenTableExceedsMaximumSize() {
        MarkdownChunker chunker = new MarkdownChunker();
        StringBuilder markdown = new StringBuilder("| Col A | Col B |\n| --- | --- |\n");
        for (int index = 1; index <= 12; index++) {
            markdown.append("| ").append(index).append(" | value |\n");
        }

        List<ChunkDraft> drafts = chunker.chunk(markdown.toString(), config(100, 0, 1, 100));

        assertThat(drafts).hasSizeGreaterThan(1);
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.blockType()).isEqualTo(KnowledgeEnums.BlockType.TABLE);
            assertThat(draft.content()).startsWith("| Col A | Col B |");
        });
    }

    @Test
    void keepsSmallTableAtomic() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                | Col A | Col B |
                | --- | --- |
                | 1 | value |
                """, config(100, 0, 1, 400));

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).blockType()).isEqualTo(KnowledgeEnums.BlockType.TABLE);
        assertThat(drafts.get(0).codeLanguage()).isNull();
    }

    @Test
    void splitsOversizedParagraphByChunkSize() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("a".repeat(250), config(100, 20, 40, 400));

        assertThat(drafts).hasSize(3);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content().length()).isLessThanOrEqualTo(100));
    }

    @Test
    void mergesTrailingFragmentBelowMinimumSize() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("b".repeat(210), config(100, 0, 40, 400));

        // 100 + "\n" + 10：尾块不足 minimumSize，并入前一块
        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(1).content()).hasSize(111);
    }

    @Test
    void assignsSequentialSequenceNumbers() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("# A\n\nalpha\n\n## B\n\nbeta\n", ChunkingConfig.defaults());

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).sequence()).isZero();
        assertThat(drafts.get(1).sequence()).isEqualTo(1);
    }

    @Test
    void classifiesListAndQuoteBlocks() {
        MarkdownChunker chunker = new MarkdownChunker();

        List<ChunkDraft> drafts = chunker.chunk("""
                - 第一项
                - 第二项

                > 引用内容
                """, config(100, 0, 1, 400));

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).blockType()).isEqualTo(KnowledgeEnums.BlockType.LIST);
        assertThat(drafts.get(1).blockType()).isEqualTo(KnowledgeEnums.BlockType.QUOTE);
    }

    @Test
    void omitsHeadingPathWhenHierarchyDisabled() {
        MarkdownChunker chunker = new MarkdownChunker();
        ChunkingConfig config = new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, 100, 0, 1, 400, false, true);

        List<ChunkDraft> drafts = chunker.chunk("# 标题\n\n正文内容\n", config);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).headingPath()).isNull();
        assertThat(drafts.get(0).headingLevel()).isZero();
        assertThat(drafts.get(0).content()).isEqualTo("正文内容");
    }

    @Test
    void degradesBlockTypeWhenMetadataDisabledButKeepsCodeAtomic() {
        MarkdownChunker chunker = new MarkdownChunker();
        ChunkingConfig config = new ChunkingConfig(
                KnowledgeEnums.ChunkStrategy.MARKDOWN_HEADING, 100, 0, 1, 100, true, false);
        String body = "y".repeat(300);

        List<ChunkDraft> drafts = chunker.chunk("# 标题\n\n```java\n" + body + "\n```\n", config);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).blockType()).isEqualTo(KnowledgeEnums.BlockType.PARAGRAPH);
        assertThat(drafts.get(0).codeLanguage()).isNull();
        assertThat(drafts.get(0).content()).contains(body);
    }

    @Test
    void returnsNoChunksForBlankInput() {
        MarkdownChunker chunker = new MarkdownChunker();

        assertThat(chunker.chunk("   \n\n  ", ChunkingConfig.defaults())).isEmpty();
        assertThat(chunker.chunk(null, ChunkingConfig.defaults())).isEmpty();
    }

    @Test
    void doesNotEmitNearDuplicateChunksWhenShortLinePrecedesLongLine() {
        MarkdownChunker chunker = new MarkdownChunker();
        // 回归用例：一条约 110 字符的短行，紧跟一条远超 chunkSize 的长行。
        // 修复前「最近换行」会紧贴起点，推进量被兜成 1 个字符，于是产出上百个逐字符位移的
        // 近重复切片（实库中 1857 字符的笔记曾因此产生 83 个切片）。
        String shortLine = "随手记下的零散结论。".repeat(11);
        // 长行内容必须逐段唯一：若用同一个字符重复填充，任意两块都会互相包含，
        // 「近重复」判断就失去意义
        String longLine = java.util.stream.IntStream.range(0, 200)
                .mapToObj(index -> "seg" + index)
                .collect(java.util.stream.Collectors.joining("-"));

        List<ChunkDraft> drafts = chunker.chunk(shortLine + "\n" + longLine, config(800, 120, 40, 1600));

        assertThat(drafts).hasSizeLessThanOrEqualTo(3);
        assertThat(drafts).extracting(ChunkDraft::content).doesNotHaveDuplicates();
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content().length()).isGreaterThanOrEqualTo(40));
        for (int index = 1; index < drafts.size(); index++) {
            String previous = drafts.get(index - 1).content();
            String current = drafts.get(index).content();
            assertThat(previous.contains(current) || current.contains(previous))
                    .as("切片 %d 与 %d 疑似近重复", index - 1, index)
                    .isFalse();
        }
    }

    @Test
    void keepsChunkCountBoundedForUnbrokenLongText() {
        MarkdownChunker chunker = new MarkdownChunker();
        // 长行本身超过 chunkSize 时必须按窗口边界硬切，而不是原地打转
        String text = "这是一段完全没有换行的长文本，用来模拟随手粘贴的一大段记录。".repeat(60);

        List<ChunkDraft> drafts = chunker.chunk(text, config(800, 120, 40, 1600));

        // 单次推进量不小于 (chunkSize - overlap) 的一半，因此切片数远小于字符数
        assertThat(drafts).hasSizeLessThanOrEqualTo(text.length() / 340 + 2);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.content().length()).isGreaterThanOrEqualTo(40));
    }

    @Test
    void stillSplitsNormalNotesIntoFewChunksWithHeadingContext() {
        MarkdownChunker chunker = new MarkdownChunker();
        String body = String.join(
                "\n",
                java.util.stream.IntStream.range(0, 40)
                        .mapToObj(index -> "第" + index + "行：一条长度稳定的笔记内容，用来确认正常输入不受影响。")
                        .toList());

        List<ChunkDraft> drafts = chunker.chunk("# 我的笔记\n" + body, config(800, 120, 40, 1600));

        assertThat(drafts).hasSizeLessThanOrEqualTo(3);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.headingPath()).isEqualTo("我的笔记"));
    }
}
