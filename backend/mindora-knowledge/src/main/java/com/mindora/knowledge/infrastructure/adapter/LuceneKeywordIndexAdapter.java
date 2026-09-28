package com.mindora.knowledge.infrastructure.adapter;

import com.mindora.common.exception.BusinessException;
import com.mindora.knowledge.application.port.KnowledgeAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;

/**
 * 进程内嵌入式 Lucene 关键词索引。
 *
 * <p>定位是 MySQL 的**派生索引**：切片正文、权限字段都来自数据库，索引损坏或丢失都可以由
 * 「重建关键词索引」从数据库重新生成，因此不承担真实源职责。
 *
 * <p>写入以文档为粒度原子替换（{@code updateDocuments}），避免重新切片时产生孤儿切片；
 * 每次写操作后显式刷新 {@link SearcherManager}，使新切片立即可被检索到。
 */
public class LuceneKeywordIndexAdapter implements KnowledgeAdapters.KeywordIndexPort, AutoCloseable {

    static final String FIELD_CHUNK_ID = "chunkId";
    static final String FIELD_DOCUMENT_ID = "documentId";
    static final String FIELD_VISIBILITY = "visibility";
    static final String FIELD_STATUS = "status";
    static final String FIELD_KNOWLEDGE_ENABLED = "knowledgeEnabled";
    static final String FIELD_TITLE = "title";
    static final String FIELD_HEADING_PATH = "headingPath";
    static final String FIELD_CONTENT = "content";

    /** 参与关键词匹配的字段；权限与标识字段只做精确过滤，不做全文匹配。 */
    private static final List<String> SEARCH_FIELDS = List.of(FIELD_CONTENT, FIELD_TITLE, FIELD_HEADING_PATH);

    private final Directory directory;
    private final Analyzer indexAnalyzer;
    private final Analyzer queryAnalyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;

    public LuceneKeywordIndexAdapter(Path indexPath) {
        try {
            Files.createDirectories(indexPath);
            this.directory = FSDirectory.open(indexPath);
            this.indexAnalyzer = KeywordAnalyzers.indexing();
            this.queryAnalyzer = KeywordAnalyzers.querying();
            IndexWriterConfig config = new IndexWriterConfig(indexAnalyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
            this.searcherManager = new SearcherManager(writer, new SearcherFactory());
        } catch (IOException exception) {
            throw new BusinessException(
                    "keyword_index_unavailable", "Keyword index cannot be opened: " + exception.getMessage());
        }
    }

    @Override
    public void replaceDocument(UUID documentId, List<KnowledgeAdapters.KeywordDocument> documents) {
        List<Document> luceneDocuments = new ArrayList<>();
        for (KnowledgeAdapters.KeywordDocument chunk : documents) {
            luceneDocuments.add(toDocument(chunk));
        }
        // 空列表等价于删除该文档的全部切片
        update(writer -> writer.updateDocuments(documentTerm(documentId), luceneDocuments));
    }

    @Override
    public void deleteDocument(UUID documentId) {
        update(writer -> writer.deleteDocuments(documentTerm(documentId)));
    }

    @Override
    public void deleteAll() {
        update(IndexWriter::deleteAll);
    }

    @Override
    public List<KnowledgeAdapters.KeywordHit> search(
            String query, int topN, Map<String, Object> exactMatchFilter) {
        SearcherManager manager = searcherManager;
        IndexSearcher searcher = null;
        try {
            searcher = manager.acquire();
            Query parsed = buildQuery(query);
            if (parsed == null) {
                return List.of();
            }
            TopDocs topDocs = searcher.search(applyFilter(parsed, exactMatchFilter), Math.max(1, topN));
            List<KnowledgeAdapters.KeywordHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                UUID chunkId = uuid(document.get(FIELD_CHUNK_ID));
                if (chunkId == null) {
                    continue;
                }
                hits.add(new KnowledgeAdapters.KeywordHit(chunkId, documentId(searcher, scoreDoc), scoreDoc.score));
            }
            return List.copyOf(hits);
        } catch (IOException exception) {
            throw new BusinessException(
                    "keyword_search_failed", "Keyword search failed: " + exception.getMessage());
        } finally {
            if (searcher != null) {
                try {
                    manager.release(searcher);
                } catch (IOException ignored) {
                    // 释放失败不影响本次检索结果
                }
            }
        }
    }

    /** 当前索引中的切片总数，用于运维观测与测试断言。 */
    public long chunkCount() {
        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            return searcher.getIndexReader().numDocs();
        } catch (IOException exception) {
            throw new BusinessException(
                    "keyword_index_unavailable", "Keyword index cannot be read: " + exception.getMessage());
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (IOException ignored) {
                    // 忽略
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            searcherManager.close();
        } catch (IOException ignored) {
            // 关闭阶段不再抛出，避免影响容器停机
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // 同上
        }
        try {
            directory.close();
        } catch (IOException ignored) {
            // 同上
        }
        indexAnalyzer.close();
        queryAnalyzer.close();
    }

    private void update(WriteOperation operation) {
        try {
            operation.apply(writer);
            writer.commit();
            searcherManager.maybeRefresh();
        } catch (IOException exception) {
            throw new BusinessException(
                    "keyword_index_write_failed", "Keyword index write failed: " + exception.getMessage());
        }
    }

    private Query buildQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        QueryBuilder builder = new QueryBuilder(queryAnalyzer);
        BooleanQuery.Builder combined = new BooleanQuery.Builder();
        boolean hasClause = false;
        for (String field : SEARCH_FIELDS) {
            Query clause = builder.createBooleanQuery(field, text, BooleanClause.Occur.SHOULD);
            if (clause != null) {
                combined.add(clause, BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }
        return hasClause ? combined.build() : null;
    }

    /**
     * 把应用层的字段精确匹配翻译为 Lucene 过滤子句，语义与向量检索侧的过滤保持一致。
     */
    private Query applyFilter(Query query, Map<String, Object> exactMatchFilter) {
        if (exactMatchFilter == null || exactMatchFilter.isEmpty()) {
            return query;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(query, BooleanClause.Occur.MUST);
        boolean hasFilter = false;
        for (Map.Entry<String, Object> entry : exactMatchFilter.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            builder.add(new TermQuery(new Term(entry.getKey(), String.valueOf(entry.getValue()))),
                    BooleanClause.Occur.FILTER);
            hasFilter = true;
        }
        return hasFilter ? builder.build() : query;
    }

    private Document toDocument(KnowledgeAdapters.KeywordDocument chunk) {
        Document document = new Document();
        document.add(new StringField(FIELD_CHUNK_ID, chunk.chunkId().toString(), Field.Store.YES));
        document.add(new StringField(FIELD_DOCUMENT_ID, chunk.documentId().toString(), Field.Store.YES));
        document.add(new StringField(FIELD_VISIBILITY, orEmpty(chunk.visibility()), Field.Store.NO));
        document.add(new StringField(FIELD_STATUS, orEmpty(chunk.status()), Field.Store.NO));
        document.add(new StringField(
                FIELD_KNOWLEDGE_ENABLED, Boolean.toString(chunk.knowledgeEnabled()), Field.Store.NO));
        addText(document, FIELD_TITLE, chunk.title());
        addText(document, FIELD_HEADING_PATH, chunk.headingPath());
        addText(document, FIELD_CONTENT, chunk.content());
        return document;
    }

    private void addText(Document document, String field, String value) {
        if (value != null && !value.isBlank()) {
            document.add(new TextField(field, value, Field.Store.NO));
        }
    }

    private UUID documentId(IndexSearcher searcher, ScoreDoc scoreDoc) throws IOException {
        return uuid(searcher.storedFields().document(scoreDoc.doc).get(FIELD_DOCUMENT_ID));
    }

    private Term documentTerm(UUID documentId) {
        return new Term(FIELD_DOCUMENT_ID, documentId.toString());
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface WriteOperation {
        void apply(IndexWriter writer) throws IOException;
    }
}
