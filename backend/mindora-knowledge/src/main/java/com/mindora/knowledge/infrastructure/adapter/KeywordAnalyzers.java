package com.mindora.knowledge.infrastructure.adapter;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.cjk.CJKWidthFilter;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * 关键词索引与关键词查询所用的分析器。
 *
 * <p>面向「中文技术笔记」语料，处理链为：
 * <ol>
 *   <li>{@link StandardTokenizer}：非 CJK 部分按标准英文/数字切分；</li>
 *   <li>{@link CJKWidthFilter}：全角转半角；</li>
 *   <li>{@link LowerCaseFilter}：统一小写；</li>
 *   <li>{@link CJKBigramFilter}：中文按二元切分，保证未登录技术新词（Rerank、Embedding 等）
 *       仍可被召回，不依赖词典；</li>
 *   <li>{@link WordDelimiterGraphFilter}：拆开 {@code headingPath}、{@code chunk_id} 这类
 *       驼峰与下划线标识符，便于按代码符号定位。</li>
 * </ol>
 *
 * <p>第 4、5 步都会产生 token 图形，因此索引侧在末尾追加 {@link FlattenGraphFilter} 把图形展平
 * 后再入库；查询侧保留图形，以便构造短语查询。两者必须共用同一前缀链路，否则写入与查询的
 * 词项不一致，关键词检索会静默失效。
 */
final class KeywordAnalyzers {

    private static final int CJK_FLAGS =
            CJKBigramFilter.HAN | CJKBigramFilter.HIRAGANA | CJKBigramFilter.KATAKANA | CJKBigramFilter.HANGUL;

    private static final int WORD_DELIMITER_FLAGS =
            WordDelimiterGraphFilter.GENERATE_WORD_PARTS
                    | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
                    | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
                    | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
                    | WordDelimiterGraphFilter.PRESERVE_ORIGINAL;

    private KeywordAnalyzers() {
    }

    /** 写入索引时使用。 */
    static Analyzer indexing() {
        return new Chain(true);
    }

    /** 查询时使用。 */
    static Analyzer querying() {
        return new Chain(false);
    }

    private static final class Chain extends Analyzer {
        private final boolean flatten;

        private Chain(boolean flatten) {
            this.flatten = flatten;
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            StandardTokenizer tokenizer = new StandardTokenizer();
            TokenStream stream = new CJKWidthFilter(tokenizer);
            stream = new LowerCaseFilter(stream);
            stream = new CJKBigramFilter(stream, CJK_FLAGS);
            stream = new WordDelimiterGraphFilter(stream, WORD_DELIMITER_FLAGS, null);
            if (flatten) {
                stream = new FlattenGraphFilter(stream);
            }
            return new TokenStreamComponents(tokenizer, stream);
        }
    }
}
