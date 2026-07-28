# Design 02: System Architecture

## Architecture Choice

The platform should use a modular monolith architecture.

This keeps the first version practical for local-first development and Docker
Compose deployment, while preserving clean module boundaries for future growth.
The system is not planned as a microservice platform in the first stage.

## High-Level Structure

### Frontend

The frontend should use a Vue 3 + Vite monorepo.

Proposed apps:

- `site`: public homepage, blog, topic pages, public AI chat
- `admin`: admin console and operations dashboard

Shared packages:

- `api-client`: request layer and shared API types
- `ui`: shared UI components
- `utils`: common frontend helpers

### Backend

The backend should use Spring Boot 3 as the core application framework.

Recommended stack:

- Spring Boot 3
- Spring Security
- Spring AI
- MyBatis-Plus
- Redis
- RocketMQ
- MySQL
- Lucene
- Milvus
- local file storage

### Infrastructure

The first version should support local-first development and Docker Compose
deployment.

Core infrastructure components:

- MySQL for business data
- Redis for cache and authentication support
- Milvus for vector storage
- Lucene for keyword and inverted-index retrieval
- RocketMQ for asynchronous workflows and domain events
- local file storage for first-version managed assets

## Backend Modules

The backend should be split into clearly bounded modules.

Recommended modules:

- `user`: registration, login, GitHub OAuth, messages, user management
- `blog`: articles, drafts, categories, tags, publishing, reading stats
- `knowledge`: data source configuration, sync workflows, parsing, chunking,
  ingestion, indexing state
- `asset`: local file storage, asset metadata, file references, public URL
  generation, hash-based deduplication
- `rag`: retrieval, multi-turn chat, conversation history, citations
- `admin`: admin aggregation APIs, configuration, operations views
- `adapter`: data source adapters, vector store adapters, LLM adapters, OAuth
  adapters, storage adapters
- `task`: RocketMQ consumers, retries, scheduled or manual jobs
- `common`: shared utilities, exceptions, response models, audit fields

## Boundary Rules

Module boundaries must be explicit. Internal implementation must not be called
freely across modules.

### Allowed

- `controller -> application -> domain -> infrastructure`
- `module A -> module B api/facade`
- business modules -> adapter interfaces
- event producer -> event consumer through the task/event layer

### Not Allowed

- `module A -> module B mapper/repository`
- `module A -> module B infrastructure`
- `module A -> module B domain mutation`
- business code -> concrete external SDK directly
- `common` becoming a place for business logic

### Boundary Enforcement

The architecture should enforce boundaries through package structure and
interface design.

Rules:

- Each module exposes only explicit API or facade interfaces.
- Cross-module reads should use query services or facades.
- Cross-module writes should use domain events or task workflows.
- External provider logic should live behind adapter interfaces.

## Dependency Model

Suggested dependency direction:

```text
controller -> application -> domain -> infrastructure
module -> module.api / module.facade
module -> adapter interface
task/event -> module application service
```

Examples:

- `blog` publishes `ArticlePublishedEvent`.
- `knowledge` consumes the event and triggers ingestion, chunking, and
  indexing.
- `rag` reads only from knowledge-exposed query services and vector adapter
  interfaces.
- `blog`, `knowledge`, and `site` configuration use the asset module or storage
  adapter interfaces instead of writing files directly.
- `admin` aggregates data through module facades instead of querying internal
  tables directly.

## Data Responsibilities

### MySQL

Use MySQL for:

- users and authentication state
- blog articles, drafts, categories, tags, statistics
- knowledge base metadata
- data source configuration
- sync task records
- chunking and indexing status
- chat conversations and messages
- messages or guestbook entries
- system configuration and logs
- asset metadata and file reference records

### Local File Storage

Use local file storage for the first version of managed files.

It should store:

- Obsidian/Gitee image attachments
- blog cover images
- blog article images
- homepage avatars
- homepage background images
- banner images
- website theme or background images
- admin-uploaded configuration images

MySQL should store file metadata and references. File binaries should not be
stored in MySQL.

### Redis

Use Redis for:

- cache
- login support
- verification codes
- rate limiting
- short-lived session helpers

### Milvus

Use Milvus for:

- chunk embeddings
- minimal chunk metadata
- citation IDs and lookup payloads
- retrieval index data

### RocketMQ

Use RocketMQ for:

- content published events
- knowledge sync events
- chunking and vectorization events
- index rebuild events
- asynchronous retry and manual trigger workflows

## Default Adapters

The first version should implement one default provider in each adapter area.

Planned defaults:

- data source: Gitee
- vector store: Milvus
- file storage: local file storage
- model provider: OpenAI-compatible API
- login provider: email + GitHub

The architecture should still allow future adapters such as local directory
sources, Feishu, Yuque, Notion, Milvus, MinIO, OSS, COS, S3-compatible storage,
or other model protocol variants.

## Authentication

Authentication should use Spring Security with JWT as the primary approach and
Redis as supporting infrastructure.

This works well for separate `site` and `admin` frontend applications while
keeping server-side control for token support, rate limiting, and short-lived
state.

Recommended first-version and near-term auth capabilities:

- access token + refresh token
- email verification code
- email/password login
- GitHub OAuth
- Redis-backed short-lived verification and refresh support

## Optional Module Strategy

Some AI and retrieval capabilities should be deployable as optional modules.

Examples:

- vector embedding pipeline
- rerank pipeline
- hybrid search
- public RAG chat
- vector retrieval infrastructure

This keeps the public site light while allowing the admin and knowledge
pipeline to scale up when resources are available. The system should support
feature flags, conditional bean wiring, or profile-based enablement for these
modules.

## Operational Strategy

The first version should prioritize working end-to-end over over-engineering.

Recommended delivery order:

1. Basic user auth and admin access
2. Blog content management
3. Asset storage and site/blog image management
4. Knowledge sync from Gitee
5. Chunking and vectorization
6. RAG chat with citations
7. Admin operations views and logs

## Confirmed Choices

- Backend architecture: modular monolith
- Backend stack: Spring Boot 3, Spring AI, MyBatis-Plus, Redis, RocketMQ
- Database: MySQL
- Vector database: Milvus
- Keyword retrieval engine: Lucene
- Data source: Gitee first
- File storage: local file storage first
- Frontend architecture: Vue 3 + Vite monorepo
- Frontend apps: `site` and `admin`
- Auth: Spring Security + JWT + refresh token + email verification + GitHub OAuth + Redis support
- Boundary rule: modules must not directly call each other's internals
- Future expansion: adapters for more data sources, vector stores, storage
  providers, and model providers

## Trade-Offs And Drawbacks

This architecture improves retrieval richness and operational flexibility, but
it also adds clear costs:

- Running both vector retrieval and keyword retrieval increases architecture,
  testing, and observability complexity compared with a single-retriever stack.
- Milvus provides stronger long-term vector scalability, but its deployment,
  operations, and tuning cost are higher than a lighter vector setup.
- Lucene improves keyword retrieval and fallback behavior, but it introduces an
  extra indexing lifecycle, consistency checks, and storage overhead.
- Hybrid search, RRF fusion, reranking, and context refinement improve answer
  quality, but each stage adds latency and more failure modes.
- JWT plus refresh token plus email verification plus GitHub OAuth gives a more
  complete auth system, but significantly increases token lifecycle, security,
  audit, and abuse-control complexity.
- Optional module enablement protects low-resource deployments, but it requires
  stricter configuration management, clearer degraded-mode UX, and more test
  permutations.
- Spring AI, vector stores, rerankers, and embedding providers deepen external
  dependency risk; provider instability can affect retrieval quality even when
  the main site is otherwise healthy.
