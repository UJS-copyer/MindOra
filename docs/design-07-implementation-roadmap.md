# Design 07: Implementation Roadmap

## Purpose

This document defines the implementation stages for the personal knowledge
management platform.

The roadmap follows this principle:

```text
Stable engineering foundation + runnable loop in every stage
```

The project should avoid two extremes:

- building only infrastructure for a long time without visible product progress
- quickly stacking features while leaving module boundaries and operations
  unmanaged

## Stage Overview

Recommended stages:

1. Stage 0: Engineering Foundation
2. Stage 1: Content and Admin
3. Stage 2: Knowledge Base Loop
4. Stage 3: RAG Chat
5. Stage 4: Operations Enhancement

## Stage 0: Engineering Foundation

Goal:

Build a stable project foundation that can run locally and support future
feature work.

Scope:

- Spring Boot 3 modular monolith
- Vue 3 + Vite monorepo
- Docker Compose
- MySQL
- Redis
- Milvus
- RocketMQ
- Spring Security + JWT
- basic RBAC
- unified response model
- unified exception handling
- logging baseline
- configuration baseline
- module boundary conventions

Quality baseline:

- JUnit 5
- Spring Boot Test
- backend service-level tests for core logic
- frontend ESLint
- Prettier
- TypeScript checking
- environment template
- README startup guide
- Docker Compose health checks

Not required in Stage 0:

- high test coverage threshold
- full CI pipeline
- end-to-end tests
- code scanning platform
- complex quality reports

Stage 0 deliverable:

```text
The project can start locally, core infrastructure can connect, and basic
authentication is available.
```

## Stage 1: Content and Admin

Goal:

Build the minimum content loop: admin-side article management and public-side
blog display.

Scope:

- admin login
- admin shell
- article list
- article creation
- article editing
- draft, publish, and unpublish states
- `md-editor-v3`
- category and tag management
- cover image upload
- basic asset management
- public homepage baseline
- public blog list
- public article detail page
- category and tag filtering
- article read count

Editor decision:

```text
First-version Markdown editor: md-editor-v3
```

Editor requirements:

- Markdown-first editing
- split preview
- image upload through the asset module
- pasted image upload through the asset module
- frontend rendering not tightly coupled to editor internals

Not required in Stage 1:

- complex topics or series
- advanced SEO
- advanced analytics
- comments
- RAG chat
- complete visual site configuration

Stage 1 deliverable:

```text
Admin can create and publish an article. The public site can display the
article list and article detail page.
```

## Stage 2: Knowledge Base Loop

Goal:

Build the knowledge ingestion, chunking, embedding, and vector indexing loop.

Implementation order:

1. Gitee data source configuration
2. Manual Gitee synchronization
3. Markdown parsing
4. Import without frontmatter
5. Image reference parsing
6. Knowledge Document and Version creation
7. Chunking configuration
8. Embedding call
9. Milvus write
10. Index status management
11. Blog publish-to-knowledge ingestion

Scope:

- Gitee data source
- manual sync
- scheduled sync
- incremental sync
- full sync
- document version records
- global chunking strategy
- Alibaba Cloud Bailian `text-embedding-v4`
- Milvus vector write
- index status tracking
- failure retry
- single document re-index
- batch re-index

Stage 2 data flow:

```text
Gitee Markdown
-> sync
-> Knowledge Document
-> Knowledge Document Version
-> chunks
-> embedding
-> Milvus
-> indexed state
```

After the Gitee flow works, add:

```text
Blog Article published
-> Knowledge Document
-> chunks
-> embedding
-> Milvus
```

Stage 2 deliverable:

```text
Markdown from the Gitee repository can be synchronized, parsed, chunked,
embedded, written to Milvus, and inspected from the admin console.
```

## Stage 3: RAG Chat

Goal:

Build the complete RAG question-answering loop and release it progressively.

Release sequence:

```text
admin-side testing page
-> hidden public chat page
-> officially exposed public chat
```

The implementation is phased for risk control, but the final Stage 3 scope is
the complete retrieval and generation chain rather than vector-only chat.

Recommended implementation phases:

1. Retrieval foundation: Milvus vector retrieval, Lucene keyword retrieval,
   metadata permission filtering, and index status.
2. Retrieval quality: Hybrid Search, RRF fusion, reranking, and context
   refinement.
3. Conversation foundation: admin chat testing, conversation persistence,
   citations, logs, fallback handling, and SSE streaming.
4. Public release: public chat, visitor limits, logged-in history, strict
   grounding, and progressive rollout.

### RAG Backend Stack

The RAG backend should stay inside the Spring Boot 3 modular monolith as a
dedicated `rag` module, with provider logic behind adapters.

Recommended stack:

- Spring Boot 3
- Spring AI
- Spring Security
- MyBatis-Plus
- Redis
- RocketMQ
- Milvus Java SDK or HTTP wrapper
- Alibaba Cloud Bailian `text-embedding-v4`
- SiliconFlow `BAAI/bge-reranker-v2-m3`
- OpenAI-compatible Chat API
- SSE

RAG backend responsibilities:

- retrieval orchestration
- Milvus vector retrieval
- Lucene keyword retrieval
- Hybrid Search and RRF fusion
- prompt assembly
- chat model calls
- embedding calls
- rerank calls
- context refinement
- multi-turn context handling
- streaming output
- conversation persistence
- retrieval logging
- fallback handling

External model, embedding, rerank, and vector store integrations should be
behind adapter boundaries. The exact code structure should be decided during
implementation planning.

### RAG Retrieval Scope

First-version retrieval:

```text
Milvus vector retrieval + Lucene keyword retrieval + metadata filtering
```

The final Stage 3 retrieval chain:

```text
Milvus vector retrieval
-> Lucene keyword retrieval
-> Hybrid Search
-> RRF fusion
-> reranking
-> context refinement
-> prompt assembly
-> grounded answer
```

The implementation should still be delivered in phases, but all of the above
capabilities are part of the final target.

Long-term interfaces should still reserve:

- keyword retrieval
- hybrid retrieval
- result fusion
- query rewrite

### RAG Model Providers

Chat Model:

- API key based
- OpenAI-compatible provider configuration
- supports DeepSeek, Qwen, GPT-compatible services, or similar providers

Embedding Model:

- Alibaba Cloud Bailian `text-embedding-v4`

Rerank Model:

- SiliconFlow `BAAI/bge-reranker-v2-m3`

Vector Store:

- Milvus

### RAG Runtime Features

Scope:

- admin-side chat testing page
- public ChatGPT-like page
- visitor trial
- login user history
- vector retrieval
- keyword retrieval
- Hybrid Search
- RRF fusion
- metadata permission filtering
- reranking
- context refinement
- article-level citations
- citation links to `/blog/{slug}`
- SSE streaming output
- conversation history
- retrieval logs
- fallback logs

Fallback:

- rerank failure falls back to Milvus vector score ordering
- keyword retrieval failure falls back to vector retrieval
- permission filtering failure must fail closed
- embedding/vector/chat service failures return explicit temporary-unavailable
  responses

Stage 3 deliverable:

```text
The public chat page can answer from published content, stream output through
SSE, cite original articles, and save history for logged-in users.
```

## Stage 4: Operations Enhancement

Goal:

Make the synchronization and RAG system observable and recoverable enough for
first-version release.

Required before first public release:

- sync task status
- failed task list
- failure retry
- latest Gitee sync result
- chunking status
- vectorization status
- Milvus write status
- model service status
- rerank fallback logs
- RAG error logs
- basic health checks

Can be postponed:

- popular article dashboard
- user growth trends
- reading trend charts
- chat usage trend charts
- content operation analytics
- complex BI dashboard

Stage 4 deliverable:

```text
Admin can observe and recover the high-risk synchronization and RAG workflows.
```

## Release Criteria

The first release should satisfy:

- admin can log in
- admin can publish blog articles
- public site can display homepage and articles
- Gitee Markdown can be synchronized into the knowledge base
- knowledge documents can be chunked and indexed
- public chat can answer from public content
- citations link back to original articles
- failures in sync, embedding, vector writing, and reranking are visible
- high-risk operations require confirmation

## Confirmed Decisions

- Roadmap style: engineering stability first, while preserving MVP loops.
- Stage 0 includes basic tests and code quality tools.
- Stage 1 builds admin article management and public blog display in parallel.
- Stage 2 implements both Gitee sync and blog publish ingestion, with Gitee sync
  first.
- Stage 3 implements complete RAG capabilities in phases: admin test page,
  retrieval quality chain, hidden public page, then public entry.
- Stage 4 is risk-based: synchronization and RAG operations are required,
  content operation analytics can be postponed.
- RAG backend uses Spring Boot 3, Spring AI, Milvus, Lucene, Bailian embedding,
  SiliconFlow rerank, OpenAI-compatible chat, RocketMQ, Redis, and SSE.
