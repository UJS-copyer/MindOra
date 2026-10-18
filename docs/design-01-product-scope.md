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

## 范围复核（2026-10-17）

状态：**部分取代**上文各节。本节不改写历史，只声明哪些早期假设已作废、当前范围实际是什么。
依据与证据：`docs/ragnote-insights-and-architecture-takeaways.md` §5。

| 上文条款 | 处置 | 当前口径 |
| --- | --- | --- |
| §1「Email registration and login / GitHub login / Message or guestbook / moderation and notification workflows」 | **部分作废** | **账号体系保留并启用**：开放注册（2026-10-18 决定），后续接 Google / GitHub 登录（`auth_account` 多 provider 结构已就绪）；评论、留言、审核、通知、邮件仍不做 |
| §1「public user interaction」 | **收窄** | 访客**匿名即可完整使用**公开问答；登录后拥有**自己的**会话历史（不进管理端），并获得更高限流额度 |
| §4「New conversation / Multi-turn conversation / Conversation history」作为**公开**能力 | **修订（2026-10-18）** | 公开站当前仍是单次问答（无会话 UI）；**会话历史归账号**，公开站登录后可见（暂记 Stage 4.7），管理端只是私有域工具 |
| §7「operational configuration should be managed through the admin UI」 | **收窄为「端口可换、配置以 yml 为主」** | 不再承诺后台配置编辑器；管理端做只读观测与少数必要操作（Stage 3.5 的教训：配置面越大越难验收） |
| 「should not hard-code a single … vector database」 | **保留端口，收窄默认** | 检索可运行在**非向量**模式（关键词 + 全文）；向量是可选增强，**保留**，是否参与公开链路由后续实测决定（现状：参与） |
| 「configurable and extensible full-stack personal knowledge management system」 | **收窄** | 个人笔记 + RAG 介绍站，边开发边学习；**不对标 RAGNote 的完整度**，目标是「简单舒适的使用体验」 |
| 「Published blog articles can enter the knowledge base pipeline」 | **澄清（2026-10-17）** | **全部来源都入库**（含草稿——那正是私人助手的价值）；**只有已发布文章进公开检索域**，fail-closed（design-11 §15.3） |
| 交付标准（上文隐含） | **明确定义（2026-10-17）** | **生产级 = 能给陌生人用、不烧钱、坏了能发现、改了能回退、问答体验接近常见产品。** 明确**不含**多实例 / 高可用 / SLA（单实例是接受的上限）。工程与产品都按这一条判，不再用「像不像大厂」当标准 |
| 多用户 / 多租户 | **已定（2026-10-18）** | 「多用户」= **A 方案：单站点、单知识库，各人各自会话**；站长与访客是**同一类用户**，差别只在查询范围，且范围**由入口决定**（公开站永远 `public`，管理端才允许 `all`）。「多租户」（一套部署多个独立站点/知识库）**明确不做**，成本评估见 takeaways §5.8 |

不变的：内容闭环优先、首页长期是个人首页、博客是完整内容管理模块、发布即入知识库、资产统一管理、
后端 Java / Spring Boot、前端 Vue 3 + Vite、本地优先。
