# Design 01: Product Scope

## Platform Positioning

The platform is a configurable and extensible full-stack personal knowledge
management system.

It uses content creation as the entry point, knowledge-base synchronization and
structured indexing as the foundation, and RAG-based intelligent Q&A as the
interaction layer. The core loop is:

```text
Content creation -> Structured chunking -> Retrieval index -> Intelligent Q&A
```

The system should not hard-code a single repository, vector database, or model
provider. Instead, it should expose key configuration through the admin console
and use adapter-style service boundaries so that future sources and providers
can be added gradually.

File and asset storage is also a first-class platform capability. Images and
other managed files should not be scattered across blog, knowledge base, and
site configuration logic. They should be managed through a unified asset system
with configurable storage backends.

## Core Modules

### 1. User System

The user system supports public user interaction and admin-side management.

First-stage capabilities:

- Email registration and login.
- GitHub login.
- User identity management.
- Message or guestbook submission.
- Admin-side user and message management.

Future expansion:

- Additional OAuth providers.
- More detailed user profiles.
- Moderation and notification workflows.

### 2. Knowledge Base System

The knowledge base is the content and AI data foundation of the platform.

Data sources should be configurable rather than fixed to one Gitee repository.
The first version can implement one default source, while the architecture
should allow future sources such as Gitee, GitHub, Feishu, Yuque, Notion, or
other platforms.

First-stage capabilities:

- Data source configuration.
- Local or repository-based synchronization.
- Synchronization workflow management.
- Markdown parsing.
- Sync status tracking.
- Failed-task retry.
- Manual synchronization.
- Public blog content ingestion.
- Structured content storage for retrieval and Q&A.

### 3. Blog System

The blog system provides complete content management rather than only rendering
Markdown files.

First-stage capabilities:

- Article creation.
- Article editing.
- Publishing and unpublishing.
- Draft management.
- Batch import.
- Categories.
- Tags.
- Reading statistics.
- SEO metadata.
- Public visibility management.

After an article is published, it can be synchronized into the knowledge base
pipeline, where it is chunked, vectorized, indexed, and made available to the
RAG system.

### 4. RAG Intelligent Q&A System

The RAG system uses the knowledge base as its source.

First-stage capabilities:

- Independent ChatGPT-like public Q&A page.
- New conversation creation.
- Multi-turn conversation.
- Conversation history.
- Retrieval-grounded answers.
- Source citations.
- Admin-side Q&A logs.

The vector database layer should support multiple implementations by design,
but the first version should use one selected vector database.

The model integration layer should first support OpenAI-compatible APIs, such
as DeepSeek, Qwen, GPT, and similar providers. Other model protocols can be
added later.

### 5. Chunking and Vectorization Management

Article chunking and vectorization should be configurable and manageable from
the admin console where practical.

First-stage capabilities:

- Configurable chunking strategy.
- Configurable chunk size.
- Configurable chunk overlap.
- Embedding model configuration.
- Manual vector index update.
- Manual index rebuild.
- Vectorization status tracking.
- Failed-task handling.

### 6. File and Asset Storage

The file and asset storage system manages files used by the public site, blog
system, and knowledge base synchronization flow.

First-stage capabilities:

- Local file storage as the default implementation.
- Obsidian/Gitee image attachment management.
- Blog cover image management.
- Blog article image management.
- Homepage avatar, background, and banner image management.
- Website theme or background image replacement.
- Asset metadata management.
- File hash tracking for deduplication and change detection.
- Storage path and public URL generation.

The first version of the message system is text-only. Message attachments are
not included in the first version.

Future expansion:

- MinIO storage.
- Aliyun OSS.
- Tencent COS.
- S3-compatible storage.
- More file types such as PDF, documents, audio, and video.

### 7. Admin Management and Operations

The admin console is the control center of the system.

First-stage capabilities:

- User management.
- Message management.
- Blog management.
- Knowledge base management.
- Data source configuration.
- Asset and file management.
- Synchronization task management.
- Vector index management.
- Model provider configuration.
- Q&A log management.
- Data management.
- Basic runtime status overview.

The key principle is that operational configuration should be managed through
the admin UI instead of being fixed directly in code.

## First-Version Strategy

The first version should use extensible boundaries but implement one default
provider for each major capability.

Examples:

- One default data source implementation first.
- One vector database implementation first.
- One file storage implementation first.
- One OpenAI-compatible model protocol first.
- Lightweight RBAC first, with `super_admin` and `user` as initial roles.

This keeps the project realistic while preserving future extensibility.

## Confirmed Decisions

- The platform is content-loop-first, not only a static personal site.
- The homepage remains a long-term personal homepage.
- The blog system is a complete content management module.
- Published blog articles can enter the knowledge base pipeline.
- Managed files and images use a unified asset storage system.
- The first file storage implementation is local file storage.
- The RAG system uses the knowledge base as its source.
- Public AI Q&A should support multi-turn conversations and new conversations.
- Configuration should be managed through the admin console where possible.
- The backend direction is Java-first, based on Spring Boot.
- The frontend direction is Vue 3 and Vite.
- Development should support local-first use and Docker Compose deployment.
