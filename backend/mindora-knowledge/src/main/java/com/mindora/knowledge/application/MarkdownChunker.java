package com.mindora.knowledge.application;

import com.mindora.knowledge.domain.ChunkDraft;
import com.mindora.knowledge.domain.ChunkingConfig;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 块级切片器。
 *
 * <p>与「按固定长度截断」不同，本实现先做块级结构解析，再按结构切片：
 *
 * <ul>
 *   <li>围栏代码块与表格视为<b>原子块</b>，不会被定长窗口拦腰切断；
 *       表格超过 {@code maximumSize} 时按行切分并重复表头行。</li>
 *   <li>维护标题栈，为每个切片产出完整 {@code headingPath} 与 {@code headingLevel}，
 *       使切片带有从属章节上下文。</li>
 *   <li>连续文本块（段落/列表/引用）按 {@code chunkSize} 与 {@code overlap} 切分，
 *       末尾小于 {@code minimumSize} 的碎片并入前一块。</li>
 * </ul>
 *
 * <p>支持的标题形式：ATX（{@code #} 前缀）与 Setext（{@code ===} / {@code ---} 下划线）。
 * 分隔线（{@code ---} / {@code ***} / {@code ___}）本身不是内容，会被跳过；由于判断位于
 * Setext 之后，「文本 + {@code ---}」仍会被识别为二级标题。
 *
 * <p>切片策略：{@code MARKDOWN_HEADING} 不跨章节边界累计文本，切片天然带上所属章节；
 * {@code FIXED_SIZE} 忽略章节边界、按尺寸连续切分，每个切片仍按起始偏移归属真实章节。
 *
 * <p>{@code preserveHeadingHierarchy=false} 时不维护标题栈，切片不带章节路径；
 * {@code includeMetadata=false} 时块类型统一退化为 {@code PARAGRAPH} 且不带代码语言。
 * 注意：原子块保护与元数据开关、切片策略均无关，代码块与表格在任何配置下都不会被切断。
 *
 * <p>已知限制：Setext 仅识别紧邻的单行标题文本，多行文本加下划线的写法会退化为
 * 「普通段落 + 二级标题」。
 */
public class MarkdownChunker {

    private static final int MAX_FENCE_INDENT = 3;

    public List<ChunkDraft> chunk(String markdown, ChunkingConfig config) {
        ChunkingConfig effective = config == null ? ChunkingConfig.defaults() : config;
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n");
        if (text.isBlank()) {
            return List.of();
        }
        return buildChunks(parseSections(text, effective), effective);
    }

    // ------------------------------------------------------------------
    // 阶段一：块级结构解析
    // ------------------------------------------------------------------

    private List<Section> parseSections(String text, ChunkingConfig config) {
        List<Section> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<Block> currentBlocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int index = 0;
        while (index < lines.length) {
            Fence fence = fence(lines[index]);
            if (fence != null) {
                FencedBlock code = readFencedBlock(lines, index, fence);
                currentBlocks.add(new Block(KnowledgeEnums.BlockType.CODE, code.text(), fence.language()));
                index = code.nextIndex();
                continue;
            }

            Heading heading = heading(lines[index]);
            int headingLines = 1;
            if (heading == null) {
                heading = setextHeading(lines, index);
                headingLines = 2;
            }
            if (heading != null) {
                addSection(sections, headingStack, currentBlocks);
                currentBlocks.clear();
                if (config.preserveHeadingHierarchy()) {
                    while (headingStack.size() >= heading.level()) {
                        headingStack.remove(headingStack.size() - 1);
                    }
                    headingStack.add(heading.title());
                }
                index += headingLines;
                continue;
            }

            if (isTableStart(lines, index)) {
                currentBlocks.add(new Block(KnowledgeEnums.BlockType.TABLE, collectTable(lines, index), null));
                index = indexAfterTable(lines, index);
                continue;
            }

            // 分隔线（--- / *** / ___）本身不是内容，跳过以免生成只含符号的噪声切片。
            // 注意：此处位于 setext 标题判断之后，因此「文本 + ---」仍会被识别为二级标题。
            if (isThematicBreak(lines[index])) {
                index++;
                continue;
            }

            Kind kind = classify(lines[index]);
            if (kind == Kind.BLANK) {
                index++;
                continue;
            }
            int start = index;
            index++;
            // 段落/列表/引用的连续行中一旦出现围栏、标题或表格必须中断累计，
            // 否则紧随其后的代码块会被并进普通段落，丢失 blockType 与原子性保护。
            while (index < lines.length
                    && classify(lines[index]) == kind
                    && !startsStructuredBlock(lines, index)) {
                index++;
            }
            String blockText = String.join("\n", List.of(lines).subList(start, index)).trim();
            if (!blockText.isEmpty()) {
                currentBlocks.add(new Block(blockTypeOf(kind), blockText, null));
            }
        }
        addSection(sections, headingStack, currentBlocks);
        return sections;
    }

    /**
     * 该行是否开启一个结构化块（围栏代码块、标题或表格）。用于中断普通文本的连续累计。
     */
    private boolean startsStructuredBlock(String[] lines, int index) {
        return fence(lines[index]) != null
                || heading(lines[index]) != null
                || setextHeading(lines, index) != null
                || isTableStart(lines, index)
                || isThematicBreak(lines[index]);
    }

    /**
     * 分隔线：整行由 3 个及以上相同的 {@code -}、{@code *} 或 {@code _} 组成（可含空格）。
     */
    private boolean isThematicBreak(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        char marker = trimmed.charAt(0);
        if (marker != '-' && marker != '*' && marker != '_') {
            return false;
        }
        int count = 0;
        for (int position = 0; position < trimmed.length(); position++) {
            char character = trimmed.charAt(position);
            if (character == marker) {
                count++;
            } else if (character != ' ') {
                return false;
            }
        }
        return count >= 3;
    }

    private void addSection(List<Section> sections, List<String> headingStack, List<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        sections.add(new Section(List.copyOf(headingStack), headingStack.size(), List.copyOf(blocks)));
    }

    private FencedBlock readFencedBlock(String[] lines, int start, Fence fence) {
        StringBuilder builder = new StringBuilder(lines[start].stripTrailing());
        int index = start + 1;
        while (index < lines.length) {
            builder.append('\n').append(lines[index].stripTrailing());
            index++;
            if (isClosingFence(lines[index - 1], fence.marker())) {
                break;
            }
        }
        return new FencedBlock(builder.toString(), index);
    }

    private String collectTable(String[] lines, int start) {
        int index = start;
        List<String> rows = new ArrayList<>();
        while (index < lines.length && isTableRow(lines[index])) {
            rows.add(lines[index].stripTrailing());
            index++;
        }
        return String.join("\n", rows);
    }

    private int indexAfterTable(String[] lines, int start) {
        int index = start;
        while (index < lines.length && isTableRow(lines[index])) {
            index++;
        }
        return index;
    }

    // ------------------------------------------------------------------
    // 阶段二：按结构切片
    // ------------------------------------------------------------------

    /**
     * 把解析出的章节转换为切片。
     *
     * <p>{@code MARKDOWN_HEADING} 策略下，文本累计不跨章节边界，切片天然带上所属章节；
     * {@code FIXED_SIZE} 策略下忽略章节边界、把全文按尺寸连续切分，切片保留其起始位置
     * 所在章节的元数据。两种策略都保持代码块与表格的原子性。
     */
    private List<ChunkDraft> buildChunks(List<Section> sections, ChunkingConfig config) {
        boolean sectionBoundaries = config.strategy() != KnowledgeEnums.ChunkStrategy.FIXED_SIZE;
        List<ChunkDraft> drafts = new ArrayList<>();
        List<Buffered> buffer = new ArrayList<>();

        for (Section section : sections) {
            for (Block block : section.blocks()) {
                if (block.isAtomic()) {
                    flushBuffer(drafts, buffer, config);
                    appendAtomicBlock(drafts, block, section, config);
                    continue;
                }
                boolean typeChanged = !buffer.isEmpty() && buffer.get(0).block().type() != block.type();
                boolean sectionChanged = sectionBoundaries
                        && !buffer.isEmpty()
                        && buffer.get(0).section() != section;
                if (typeChanged || sectionChanged) {
                    flushBuffer(drafts, buffer, config);
                }
                buffer.add(new Buffered(block, section));
            }
        }
        flushBuffer(drafts, buffer, config);
        return List.copyOf(drafts);
    }

    private void appendAtomicBlock(
            List<ChunkDraft> drafts, Block block, Section section, ChunkingConfig config) {
        if (block.type() == KnowledgeEnums.BlockType.CODE) {
            addDraft(drafts, block.text(), section, config, KnowledgeEnums.BlockType.CODE, block.codeLanguage());
            return;
        }
        for (String piece : splitTable(block.text(), config)) {
            addDraft(drafts, piece, section, config, KnowledgeEnums.BlockType.TABLE, null);
        }
    }

    private void flushBuffer(List<ChunkDraft> drafts, List<Buffered> buffer, ChunkingConfig config) {
        if (buffer.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        List<OffsetMarker> markers = new ArrayList<>();
        for (Buffered entry : buffer) {
            if (joined.length() > 0) {
                joined.append("\n\n");
            }
            markers.add(new OffsetMarker(joined.length(), entry.section()));
            joined.append(entry.block().text());
        }
        KnowledgeEnums.BlockType type = buffer.get(0).block().type();
        buffer.clear();
        for (Piece piece : mergeTrailingSmall(splitText(joined.toString(), config), config)) {
            addDraft(drafts, piece.text(), sectionAt(markers, piece.start()), config, type, null);
        }
    }

    /**
     * 找出指定偏移量落在哪个章节内。{@code markers} 按偏移量递增，因此取最后一个不超过该偏移的标记。
     */
    private Section sectionAt(List<OffsetMarker> markers, int offset) {
        Section found = markers.isEmpty() ? null : markers.get(0).section();
        for (OffsetMarker marker : markers) {
            if (marker.offset() > offset) {
                break;
            }
            found = marker.section();
        }
        return found;
    }

    private void addDraft(
            List<ChunkDraft> drafts,
            String content,
            Section section,
            ChunkingConfig config,
            KnowledgeEnums.BlockType type,
            String codeLanguage) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        boolean withHierarchy = section != null && config.preserveHeadingHierarchy();
        boolean withMetadata = config.includeMetadata();
        drafts.add(new ChunkDraft(
                drafts.size(),
                trimmed,
                withHierarchy ? section.headingPathText() : null,
                withHierarchy ? section.headingLevel() : 0,
                withMetadata ? type : KnowledgeEnums.BlockType.PARAGRAPH,
                withMetadata ? codeLanguage : null));
    }

    private List<Piece> splitText(String text, ChunkingConfig config) {
        List<Piece> pieces = new ArrayList<>();
        int target = config.chunkSize();
        int overlap = config.overlap();
        int length = text.length();
        if (length <= target) {
            Piece whole = pieceAt(text, 0);
            if (!whole.text().isEmpty()) {
                pieces.add(whole);
            }
            return pieces;
        }
        int start = 0;
        while (start < length) {
            int limit = Math.min(length, start + target);
            int end = limit;
            if (limit < length) {
                int boundary = text.lastIndexOf('\n', limit);
                if (boundary > start + config.minimumSize()) {
                    end = boundary;
                }
            }
            Piece piece = pieceAt(text.substring(start, end), start);
            if (!piece.text().isEmpty()) {
                pieces.add(piece);
            }
            if (end >= length) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return pieces;
    }

    /**
     * 生成片段，并把起始偏移推进到第一个非空白字符，保证按偏移反查章节时不会落到分隔空白上。
     */
    private Piece pieceAt(String raw, int offset) {
        int leading = 0;
        while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) {
            leading++;
        }
        return new Piece(raw.trim(), offset + leading);
    }

    private List<Piece> mergeTrailingSmall(List<Piece> pieces, ChunkingConfig config) {
        if (pieces.size() < 2) {
            return pieces;
        }
        Piece last = pieces.get(pieces.size() - 1);
        if (last.text().length() >= config.minimumSize()) {
            return pieces;
        }
        List<Piece> merged = new ArrayList<>(pieces.subList(0, pieces.size() - 1));
        int lastIndex = merged.size() - 1;
        Piece previous = merged.get(lastIndex);
        merged.set(lastIndex, new Piece(previous.text() + "\n" + last.text(), previous.start()));
        return merged;
    }

    private List<String> splitTable(String tableText, ChunkingConfig config) {
        List<String> rows = new ArrayList<>(List.of(tableText.split("\n")));
        if (rows.size() <= 2) {
            return List.of(tableText);
        }
        String header = rows.get(0);
        String separator = rows.get(1);
        int minimumBodyLength = header.length() + separator.length() + 2;
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder(header).append('\n').append(separator);
        for (int index = 2; index < rows.size(); index++) {
            String row = rows.get(index);
            if (current.length() + row.length() + 1 > config.maximumSize() && current.length() > minimumBodyLength) {
                pieces.add(current.toString());
                current = new StringBuilder(header).append('\n').append(separator);
            }
            current.append('\n').append(row);
        }
        pieces.add(current.toString());
        return pieces;
    }

    // ------------------------------------------------------------------
    // 行级识别
    // ------------------------------------------------------------------

    private Fence fence(String line) {
        String stripped = stripIndent(line);
        if (stripped == null) {
            return null;
        }
        if (stripped.startsWith("```")) {
            return new Fence("```", language(stripped.substring(3)));
        }
        if (stripped.startsWith("~~~")) {
            return new Fence("~~~", language(stripped.substring(3)));
        }
        return null;
    }

    private boolean isClosingFence(String line, String marker) {
        String stripped = stripIndent(line);
        return stripped != null && stripped.startsWith(marker) && stripped.chars().allMatch(this::isFenceCharacter);
    }

    private boolean isFenceCharacter(int value) {
        char character = (char) value;
        return character == '`' || character == '~' || character == ' ' || character == '\t';
    }

    private String language(String info) {
        String trimmed = info.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private String stripIndent(String line) {
        int indent = 0;
        while (indent < line.length() && indent < MAX_FENCE_INDENT + 1 && line.charAt(indent) == ' ') {
            indent++;
        }
        if (indent > MAX_FENCE_INDENT) {
            return null;
        }
        return line.substring(indent);
    }

    private Heading heading(String line) {
        String stripped = stripIndent(line);
        if (stripped == null || !stripped.startsWith("#")) {
            return null;
        }
        int level = 0;
        while (level < stripped.length() && level < 6 && stripped.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level >= stripped.length() || stripped.charAt(level) != ' ') {
            return null;
        }
        String title = stripped.substring(level).trim();
        while (title.endsWith("#")) {
            title = title.substring(0, title.length() - 1).trim();
        }
        return title.isEmpty() ? null : new Heading(level, title);
    }

    /**
     * Setext 标题：标题文本单独成行，下一行由 {@code =} 或 {@code -} 组成。
     *
     * <pre>
     * 一级标题
     * ========
     *
     * 二级标题
     * --------
     * </pre>
     *
     * 仅识别紧邻的单行标题文本；列表项、引用、表格行与代码围栏不会被误判为标题。
     */
    private Heading setextHeading(String[] lines, int index) {
        if (index + 1 >= lines.length) {
            return null;
        }
        String text = lines[index].trim();
        if (text.isEmpty()
                || text.startsWith(">")
                || isListItem(text)
                || fence(lines[index]) != null
                || heading(lines[index]) != null
                || isTableRow(lines[index])
                || isThematicBreak(lines[index])) {
            return null;
        }
        String underline = lines[index + 1].trim();
        if (underline.isEmpty()) {
            return null;
        }
        char marker = underline.charAt(0);
        if (marker != '=' && marker != '-') {
            return null;
        }
        for (int position = 0; position < underline.length(); position++) {
            if (underline.charAt(position) != marker) {
                return null;
            }
        }
        return new Heading(marker == '=' ? 1 : 2, text);
    }

    private boolean isTableStart(String[] lines, int index) {
        return isTableRow(lines[index])
                && index + 1 < lines.length
                && isTableSeparator(lines[index + 1]);
    }

    private boolean isTableRow(String line) {
        return line.trim().startsWith("|");
    }

    private boolean isTableSeparator(String line) {
        String trimmed = line.trim();
        if (!trimmed.contains("-") || !trimmed.startsWith("|")) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (character != '|' && character != '-' && character != ':' && character != ' ') {
                return false;
            }
        }
        return true;
    }

    private Kind classify(String line) {
        if (line.isBlank()) {
            return Kind.BLANK;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith(">")) {
            return Kind.QUOTE;
        }
        if (isListItem(trimmed)) {
            return Kind.LIST;
        }
        return Kind.PARAGRAPH;
    }

    private boolean isListItem(String trimmed) {
        if (trimmed.length() >= 2) {
            char marker = trimmed.charAt(0);
            char next = trimmed.charAt(1);
            if ((marker == '-' || marker == '*' || marker == '+') && next == ' ') {
                return true;
            }
        }
        int digits = 0;
        while (digits < trimmed.length() && Character.isDigit(trimmed.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits + 1 >= trimmed.length()) {
            return false;
        }
        char delimiter = trimmed.charAt(digits);
        return (delimiter == '.' || delimiter == ')') && trimmed.charAt(digits + 1) == ' ';
    }

    private KnowledgeEnums.BlockType blockTypeOf(Kind kind) {
        return switch (kind) {
            case LIST -> KnowledgeEnums.BlockType.LIST;
            case QUOTE -> KnowledgeEnums.BlockType.QUOTE;
            default -> KnowledgeEnums.BlockType.PARAGRAPH;
        };
    }

    // ------------------------------------------------------------------
    // 内部结构
    // ------------------------------------------------------------------

    private enum Kind {
        BLANK,
        LIST,
        QUOTE,
        PARAGRAPH
    }

    private record Block(KnowledgeEnums.BlockType type, String text, String codeLanguage) {
        boolean isAtomic() {
            return type == KnowledgeEnums.BlockType.CODE || type == KnowledgeEnums.BlockType.TABLE;
        }
    }

    /** 待合并的文本块及其所属章节。 */
    private record Buffered(Block block, Section section) {
    }

    /** 合并文本中的章节起始偏移标记。 */
    private record OffsetMarker(int offset, Section section) {
    }

    /** 切分结果：文本片段及其在合并文本中的起始偏移，用于反查所属章节。 */
    private record Piece(String text, int start) {
    }

    private record Fence(String marker, String language) {
    }

    private record FencedBlock(String text, int nextIndex) {
    }

    private record Heading(int level, String title) {
    }

    private record Section(List<String> headingPath, int headingLevel, List<Block> blocks) {
        String headingPathText() {
            return headingPath.isEmpty() ? null : String.join(" > ", headingPath);
        }
    }
}
