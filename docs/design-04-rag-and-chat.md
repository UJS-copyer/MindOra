# Design 04: RAG and Intelligent Chat

## Purpose

This document defines the retrieval-augmented generation system and intelligent
chat experience.

The RAG system uses the knowledge base as its source and supports both public
chat and admin-side chat.

Core flow:

```text
User question
-> Permission filtering
-> Retrieval
-> Fusion
-> Reranking
-> Context refinement
-> Context assembly
-> Model answer
-> Source citation
-> Conversation and log persistence
```

## Retrieval Strategy

The long-term retrieval interface should be designed for a complete RAG system,
even though the first version implements only part of it.

Planned retrieval interfaces:

- `VectorRetriever`
- `KeywordRetriever`
- `HybridRetriever`
- `Reranker`
- `RetrievalFusionService`
- `RetrievalFallbackPolicy`
- `ContextRefinementService`

First-version implementation:

```text
Vector retrieval + metadata filtering + model reranking
```

Future expansion:

- keyword or BM25 retrieval
- hybrid retrieval result fusion
- RRF fusion
- query rewriting
- more advanced reranking strategies
- context refinement before answer generation

Expanded target retrieval route:

```text
Embedding retrieval + keyword retrieval + hybrid search + RRF fusion + reranking + context refinement
```

Recommended backend mapping:

- vector retrieval: Milvus
- keyword retrieval: Lucene
- fusion strategy: Reciprocal Rank Fusion (RRF)
- rerank strategy: provider-backed rerank model
- context refinement: trim, deduplicate, and reorder chunks before prompt assembly

## Metadata Design

Metadata should be designed early because it affects permission filtering,
source citation, version invalidation, and future hybrid retrieval.

The vector store payload should contain only required fields. It must not copy
the full business model into the vector database.

Required metadata fields:

- `chunk_id`
- `document_id`
- `document_version_id`
- `source_type`
- `source_id`
- `visibility`
- `status`
- `article_id`
- `article_slug`
- `category_id`
- `tag_ids`
- `chunk_index`
- `content_hash`

Optional metadata fields:

- `source_path`
- `heading_path`

Fields that should not be stored in vector metadata:

- title
- category name
- tag names
- summary
- SEO metadata
- created time
- updated time
- author display name
- full article metadata

Display details should be queried from MySQL through `article_id` or
`document_id`.

## Model Configuration

Model configuration should be separated into three categories:

- Chat Model
- Embedding Model
- Rerank Model

### Chat Model

The chat model should be configured through API key based provider settings.

The first version should support OpenAI-compatible APIs so that providers such
as DeepSeek, Qwen, GPT-compatible services, or other compatible providers can be
configured through the admin console.

Typical configuration:

- provider
- base URL
- API key
- model name
- temperature
- max tokens
- timeout

### Embedding Model

The first-version embedding model is:

```text
Alibaba Cloud Bailian text-embedding-v4
```

The embedding model should still be accessed through an adapter boundary so
that future embedding providers can be added without changing knowledge or RAG
business logic.

Typical configuration:

- provider: Alibaba Cloud Bailian
- model: `text-embedding-v4`
- API key or access credential
- dimensions, if configurable
- batch size
- timeout

### Rerank Model

The first-version rerank model is:

```text
BAAI/bge-reranker-v2-m3 via SiliconFlow
```

Spring Boot should call the rerank capability through `RerankAdapter`.

The rerank implementation should be provider-configurable, but the first version
uses SiliconFlow-hosted `BAAI/bge-reranker-v2-m3`.

Typical configuration:

- provider: SiliconFlow
- model: `BAAI/bge-reranker-v2-m3`
- endpoint
- API key
- timeout
- fallback enabled

## Reranking Strategy

First-version retrieval flow:

```text
User question
-> query embedding
-> Milvus topN vector recall
-> BAAI/bge-reranker-v2-m3 reranking
-> select topK chunks
-> assemble context
-> generate answer
```

Expanded target retrieval flow:

```text
User question
-> query embedding
-> vector recall
-> Lucene keyword recall
-> hybrid merge
-> RRF fusion
-> reranking
-> context refinement
-> select topK chunks
-> assemble context
-> generate answer
```

Recommended defaults:

- `retrieval_top_n = 30`
- `rerank_top_k = 8`
- `rerank_timeout_ms = 3000`
- `fallback_enabled = true`

If reranking fails and fallback is enabled, the system should use Milvus vector
similarity ordering.

## Fallback Strategy

Fallback behavior must be explicit.

Rules:

```text
KeywordRetriever unavailable
-> skip keyword retrieval
-> use vector retrieval

Reranker unavailable
-> use Milvus vector score ordering
-> record fallback log

Embedding service unavailable
-> fail the current chat request
-> return "retrieval service is temporarily unavailable"

Vector database unavailable
-> fail the current chat request
-> return "knowledge retrieval is temporarily unavailable"

Chat model unavailable
-> retrieval may complete, but answer generation fails
-> return "model service is temporarily unavailable"

Metadata permission filtering fails
-> do not bypass permission filtering
-> public chat request fails
```

Core principle:

```text
Quality features may degrade. Permission isolation must not degrade.
```

## Public Chat Permission Model

Public chat should support trial use by visitors and persistent history for
logged-in users.

Unauthenticated visitors:

- can ask questions
- have stricter rate limits
- use short-lived anonymous sessions
- do not get long-term conversation history

Logged-in users:

- can create new conversations
- can view conversation history
- can continue old conversations
- may have higher usage limits

Public chat must retrieve only `public` content.

Admin-side chat may retrieve private content according to admin permissions.

## Citation Strategy

The first version should show article-level citations on the public frontend.

Example:

```text
References:
1. Article title A -> /blog/{slug}
2. Article title B -> /blog/{slug}
```

Requirements:

- citations must link to the original article
- public frontend does not show chunk-level details
- backend logs should keep chunk-level retrieval details for analysis

## Answer Boundary

Public chat should be strictly grounded in the knowledge base.

Rules:

- answer only from retrieved public content
- if retrieved content is insufficient, say that there is not enough evidence
- do not freely use general model knowledge
- do not fabricate facts, links, or sources
- do not cite private content

Admin-side chat should default to knowledge-grounded answers, but future
configuration may allow limited answers or general-knowledge supplementation.

## Multi-Turn Memory

The first version should keep recent raw conversation turns.

Recommended defaults:

- `chat_context_turns = 4`
- `max_history_tokens = 3000`

Configurable range:

- `chat_context_turns`: 1 to 8

If the token budget is exceeded, the system should prioritize the most recent
messages.

Future upgrade:

```text
conversation summary + recent N turns
```

## Streaming Output

The first version should support streaming chat output.

Recommended transport:

```text
SSE
```

Flow:

```text
retrieval completed
-> context assembled
-> chat model called
-> tokens streamed through SSE
-> frontend renders incrementally
-> final answer, citations, and logs are persisted
```

## Logs and Quality Analysis

The first version should record basic chat logs and retrieval logs.

Recommended fields:

- `conversation_id`
- `message_id`
- `user_id` or `anonymous_id`
- `question`
- `answer`
- `cited_article_ids`
- `cited_document_ids`
- `retrieved_chunk_ids`
- `vector_scores`
- `rerank_scores`
- `selected_top_k`
- `retrieval_top_n`
- `chat_model`
- `embedding_model`
- `rerank_model`
- `latency_ms`
- `status`
- `error_message`

Do not store the following by default:

- full prompt
- full context
- complete private chunk content
- complete system prompt

Future admin-side debug mode may temporarily store full prompt and context with
explicit controls.

## Strategy Switches

The admin console should provide unified RAG strategy configuration.

Recommended first-version defaults:

```text
retrieval.vector.enabled = true
retrieval.keyword.enabled = false
retrieval.hybrid.enabled = false
retrieval.rerank.enabled = true
retrieval.metadata_filter.enabled = true
retrieval.fallback.enabled = true

chat.streaming.enabled = true
chat.guest.enabled = true
chat.history.enabled = true
chat.context_turns = 4
chat.max_history_tokens = 3000

answer.strict_grounding.enabled = true
answer.allow_general_knowledge.enabled = false
answer.citation.required = true

log.retrieval.enabled = true
log.chunk_detail.enabled = true
log.full_prompt.enabled = false
```

## Resource-Aware Module Enablement

The AI and retrieval stack should support selective enablement based on machine
resources and deployment goals.

Suggested toggles:

- `retrieval.vector.enabled`
- `retrieval.keyword.enabled`
- `retrieval.hybrid.enabled`
- `retrieval.rrf.enabled`
- `retrieval.rerank.enabled`
- `retrieval.context_refinement.enabled`
- `chat.public.enabled`

Examples:

- low-resource deployment: keyword retrieval only, no rerank, no public chat
- balanced deployment: vector + keyword + rerank, no public chat
- full deployment: vector + keyword + hybrid + RRF + rerank + public chat

This allows the public site to remain lightweight while the admin and knowledge
capabilities stay available in richer deployments.

## Confirmed Decisions

- Long-term retrieval design includes vector, keyword, hybrid, reranking, fusion,
  context refinement, and fallback interfaces.
- First version implements vector retrieval, metadata filtering, and reranking, while preserving phased rollout toward full hybrid retrieval.
- Lucene is the planned keyword retrieval engine.
- Milvus is the planned vector backend.
- Metadata is intentionally minimal and avoids redundant display fields.
- Chat Model uses API key based configuration.
- Embedding Model uses Alibaba Cloud Bailian `text-embedding-v4`.
- Rerank Model uses SiliconFlow `BAAI/bge-reranker-v2-m3`.
- Reranking failure falls back to vector similarity ordering.
- Permission filtering must never be bypassed.
- Public visitors can try chat; logged-in users can save history.
- Public citations are article-level and link to original articles.
- First version keeps 4 recent conversation turns by default.
- First version supports streaming output through SSE.
- Full prompt and full context are not stored by default.

## Trade-Offs And Drawbacks

The richer RAG route improves answer quality, but it also introduces important
trade-offs:

- Hybrid retrieval plus RRF plus reranking plus context refinement can
  materially increase latency compared with pure vector retrieval.
- Running Lucene and a vector store together creates consistency and reindex
  coordination work whenever documents change.
- Milvus improves vector retrieval capacity and future scale, but raises
  deployment and operational complexity compared with lighter vector setups.
- Context refinement can improve prompt efficiency, but if tuned poorly it may
  drop evidence that was useful for grounding.
- More toggles make low-resource deployment safer, but increase operational
  testing combinations and the risk of misconfiguration.
- Public chat and richer retrieval pipelines consume CPU, memory, model quota,
  and storage faster; without guardrails they can affect the main site's
  responsiveness.
