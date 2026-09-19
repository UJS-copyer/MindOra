# Design 09: Chunk Metadata and Vector Payload Contract

## Purpose

This document fixes the contract for chunk-level structure metadata and the
vector payload used by retrieval. It is the implementation counterpart of
`design-04` (RAG and Intelligent Chat), which requires metadata to be designed
early because it drives permission filtering, source citation, version
invalidation and future hybrid retrieval.

Stage 3 round 1 (Retrieval Foundation) delivers the write path enrichment and
the vector read path. It deliberately does not deliver chat, SSE, citations,
keyword (Lucene) retrieval, RRF fusion or context refinement.

## Chunk Model

`MarkdownChunker` performs block-level parsing before slicing. It produces
`ChunkDraft`:

| Field | Meaning |
| --- | --- |
| `sequence` | 0-based order inside the document version |
| `content` | raw chunk text, stored in MySQL and returned to callers |
| `headingPath` | full heading hierarchy, e.g. `后端技术 > Spring Boot > AQS`; null when absent |
| `headingLevel` | depth of the enclosing heading (1-6); 0 for preamble |
| `blockType` | `paragraph` / `code` / `table` / `list` / `quote` |
| `codeLanguage` | fenced code info string; null otherwise |

Chunking rules:

- Fenced code blocks (``` and ~~~) and tables are **atomic**: never cut by the
  size window. A table larger than `maximumSize` is split by rows with the
  header and separator rows repeated in every piece.
- Heading stack produces `headingPath`/`headingLevel`. Both ATX (`#` prefix) and
  Setext (`===` for level 1, `---` for level 2) heading styles are supported.
  Horizontal rules (`---`, `***`, `___`) are skipped and produce no chunks.
  Because thematic break detection occurs after Setext detection, a text line
  immediately followed by `---` is correctly recognised as a level-2 heading.
- A paragraph, list or quote run is interrupted by a fence, heading or table, so
  a code block that directly follows a paragraph without a blank line is still
  recognised.
- Consecutive text blocks of the **same** block type are merged and split by
  `chunkSize` with `overlap`; a trailing fragment below `minimumSize` is merged
  into the previous piece.
- `preserveHeadingHierarchy=false` clears `headingPath`/`headingLevel`.
  `includeMetadata=false` degrades `blockType` to `paragraph` and clears
  `codeLanguage`. Atomic block protection applies regardless of these flags.

### Chunking Strategies

Two chunking strategies are supported:

- **`MARKDOWN_HEADING`**: Text accumulation does not cross heading boundaries.
  Each section is sliced independently without accumulating text across headings.
- **`FIXED_SIZE`**: Heading boundaries are ignored during slicing; text is
  chunked continuously by `chunkSize`. Each chunk resolves and binds to its actual
  enclosing heading based on its start offset (skipping leading whitespace),
  rather than naively inheriting the first heading of the document.

Both strategies maintain block atomicity: code blocks and tables are never sliced
by the fixed-size window.

### Embedding text vs stored text

`content` stays raw. The text sent to the Embedding provider is
`headingPath + "\n" + content` (`ChunkDraft.embeddingText()`), so the heading
context participates in similarity without polluting the stored chunk.

## Chunk Metadata Storage

`V6__stage_3_chunk_metadata.sql` adds these columns to `knowledge_chunk`
(purely additive):

| Column | Type | Notes |
| --- | --- | --- |
| `heading_path` | `VARCHAR(767) NULL` | full hierarchy |
| `heading_level` | `INT NOT NULL DEFAULT 0` | 0 for preamble |
| `block_type` | `VARCHAR(32) NOT NULL DEFAULT 'paragraph'` | lowercase enum name |
| `code_language` | `VARCHAR(32) NULL` | fenced info string |
| `content_hash` | `VARCHAR(128) NULL` | sha256 of chunk content |

## Vector Payload Contract

Payload keys are **camelCase**, matching the pre-existing keys so that the
payload shape stays internally consistent.

| Payload key | Source | Purpose |
| --- | --- | --- |
| `chunkId` | `knowledge_chunk.id` | point identity; also the Qdrant point id |
| `documentId` | `knowledge_document.id` |回表 title / article link |
| `versionId` | document version | version invalidation + cleanup filter |
| `sequence` | chunk | ordering |
| `sourceType` | `gitee` / `blog` | citation rendering |
| `sourceId` | source or article id | traceability |
| `sourcePath` | document source path | citation + debugging |
| `visibility` | document visibility | **permission filtering** |
| `status` | document status (`active`/`archived`) | stale content exclusion |
| `knowledgeEnabled` | document flag | exclude documents opted out of the KB |
| `headingPath` | chunk | citation anchor + section context |
| `blockType` | chunk | structured citation (code/table/FAQ) |
| `codeLanguage` | chunk | code citation fidelity |
| `articleId` | `publicArticleId` | link back to published article |
| `articleSlug` | derived from `blog:<slug>` source path | public citation URL |
| `contentHash` | chunk | change detection |

Deliberate decisions:

- **Chunk body is not stored in the payload.** `design-04` forbids copying the
  business model into the vector store; content is fetched from MySQL by
  `chunkId` at read time.
- **`knowledgeEnabled` is an extension** beyond `design-04`'s required list.
  Without it a public query would retrieve documents explicitly opted out of the
  knowledge base, which would break permission isolation.
- **`categoryId` / `tagIds` are deferred.** The knowledge module must not query
  blog tables (`design-06` boundary rule); they will be added once a blog-facing
  query port exists.
- **`articleSlug` is derived** from the `blog:<slug>` source-path convention
  (`KnowledgeDocument.articleSlug()`) rather than adding a schema column.

## Vector Store Specifics (Qdrant)

- Collection uses a named vector (`dense`, size from
  `mindora.knowledge.embedding.dimensions`, Cosine) and is created on demand.
- The Qdrant point id is the **chunk id**, so `knowledge_chunk.vector_point_id`
  records the same value. Before this round it recorded `documentId:index`, which
  matched no real point.
- **Write** (`upsert`) accepts the `{"<name>": [...]}` map form.
- **Search** (`points/search`) uses `NamedVectorStruct` and must send
  `{"name": "<name>", "vector": [...]}`. Sending the write-style map form is
  rejected by Qdrant with a 400 JSON format error.
- Filters are expressed by the application layer as plain field equality
  (AND semantics) and translated into Qdrant syntax inside the adapter, so the
  vector-store query language never leaks into application code.

### Point lifecycle

Re-indexing and new-version ingestion **delete the obsolete version's points
before writing the new ones**, so a rebuild replaces rather than appends.
Without this, every rebuild accumulated a new generation of points. The cleanup
runs inside the same failure domain as the write: if it fails, the document is
recorded as an index failure instead of silently leaving stale points behind.

## Retrieval Scope Policy

`KnowledgeRetrievalService` requires an explicit `scope`:

| Scope | Filter |
| --- | --- |
| `ALL` | `status=active` AND `knowledgeEnabled=true` (no visibility restriction) |
| `PUBLIC` | the above plus `visibility=public` |

- A missing or invalid scope is rejected. There is deliberately no code path
  that queries without a filter, and the caller cannot obtain unfiltered results
  by omitting the parameter.
- Rerank is a quality feature: on failure or timeout it degrades to vector
  ordering when `fallback-enabled=true`, otherwise the request fails with
  `retrieval_rerank_unavailable`.
- Embedding and vector store failures are capability failures and surface as
  `retrieval_embedding_unavailable` / `retrieval_vector_unavailable`.

## Rerank Pipeline and Verification

The rerank pipeline has been verified end-to-end with real SiliconFlow credentials
(account funded):

- When `rerankEnabled=true`, `fallback=false` and real `rerankScore` values are
  returned. Reranking actively modifies the final ordering relative to raw vector
  order:
  - A chunk with vector score `0.573` was promoted to rank 1 with rerank score `0.9012`.
  - A chunk with vector score `0.682` dropped to rank 2 with rerank score `0.8242`.
  - Irrelevant chunks that had deceptively high vector scores (`0.643` / `0.575`)
    received near-zero rerank scores (`0.0125` / `0.0096`) and sank to the bottom.
- When `rerankEnabled=false`, results are ordered strictly by vector score, and
  `rerankScore` is `null`.
- Degradation to vector ordering occurs only when reranking fails or is unavailable.
  The prior state where requests always degraded due to insufficient account balance
  no longer applies.

## Known Limitations

- **Setext heading parsing**: Only single-line heading text immediately followed
  by an underline (`===` or `---`) is recognised as a Setext heading. Multi-line
  text followed by an underline degrades to a standard paragraph followed by a
  level-2 heading.
- A code block larger than `maximumSize` is kept atomic and may exceed the limit.
- Re-chunking a version archives the previous chunk rows and inserts new ones
  (previous generations remain as `pending`/`archived` rows). `listChunks`
  excludes archived rows; the historical rows are retained for traceability.

## Rebuild Requirement

Because both the embedding input and the payload shape changed, existing vectors
are not reusable: after deploying this round a **full re-index is required**.
The recreated collection starts empty, so the point count restarts from zero.
