# Design 07: Implementation Roadmap

> **状态注记（2026-10-17 rev 2）**：项目改为按**可上线**标准推进（预期真实用户访问），Stage 4 因此重排为
> **4.1 公开域收紧（只含已发布）→ 4.2 回答契约 v2（应答三态 + 服务端摘录）→ 4.3 质量闭环（记录 + 评价）→
> 4.4 韧性与成本护栏 → 4.5 运维可视化 → 4.6 双问答入口（仅预填）**，并新增 **Stage 5「生产化上线」**。
> 向量 / 重排 A/B 降为 backlog（向量保留）。上线判据为 D1–D7（见
> `docs/ragnote-insights-and-architecture-takeaways.md` §5.5），不是下面的 `Release Criteria`；
> 契约细节见 `docs/design-11-rag-answer-generation-layer.md` §15（此处只排顺序）。
> Stage 3.6（管理端问答与会话页）与 3.7（条件化查询改写）是主干内的子阶段，编号不变。
> **已知结构风险**：进程内 Lucene 关键词索引是单写者，后端无法水平扩展、重启即停机——见 Stage 5。

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

## Stage 4: 体验与质量

2026-10-17 重定范围（原为「Operations Enhancement」）。下面的顺序就是执行顺序：每项要么改契约、要么改数据，
因此串行而非并行。4.1 / 4.2 的契约细节见 design-11 §15。

| # | 项目 | 为什么排在这里 |
| --- | --- | --- |
| 4.1 | **公开域 = 只含已发布**（fail-closed、以 MySQL 为权威、补掉 unpublish 缺口） | 先定义「能问什么」；不先收域，后面的应答三态与记录都没有基准 |
| 4.2 | **回答契约 v2 + 提示词外置与版本化**：三态（有依据 / 无依据兜底 / 拒绝）、服务端摘录引用、取消逐字门禁；prompt 从代码移到「后台可改 + 版本历史 + 一键回退」 | 让「答不上来」变成可解释的说明；而契约的载体就是 prompt——**先外置再改**，否则「改一句要发版」立刻反噬（design-11 §15、§16） |
| 4.3 | **质量闭环**：记录 + 评价 + 配置快照 + 「同问题复测」 | 后面每个判断都要「改动前后」的可比数据 |
| 4.4 | **韧性与成本护栏**：向量挂→关键词、重排挂→融合序、模型挂→摘录；单问预算 | 公开站不能因一个可选依赖硬失败 |
| 4.5 | **运维可视化**：即原 Stage 4 的运维范围（见下） | 看得见才修得动 |
| 4.6 | **双问答入口（仅预填）** | 零契约成本的动线改进；真伴读等公开篇数够多再说 |

**Backlog（不排期）**：向量 / 重排 A/B。向量保留，只有质量或成本报警时才做。评测工具当前缺
`--vector-only` / `--keyword-only`（`scripts/rag-eval/README.md` §3）。

### 4.5 原运维范围（仍然需要）

目标：让同步与 RAG 链路「看得见、能恢复」，达到一期发布要求。

发布前必须有（即 4.5）：同步任务状态；失败列表 + 重试；最近一次 Gitee 同步结果；切片 / 向量化 / 向量写入状态；
模型服务状态；重排降级日志；RAG 错误日志；基础健康检查。

可推迟（不变）：热门文章看板、用户增长、阅读 / 对话趋势、内容分析、BI 大屏。

Stage 4 交付物：

```text
管理端能看见并恢复高风险同步与 RAG 链路。
```

## Stage 5: 生产化上线

目标：

```text
站点可以放到公网，单人运维且不会静默坏掉。
```

2026-10-17 新增：项目从「本地 demo」转为「真实站点」。**「生产级」的定义见
`docs/design-01-product-scope.md` 范围复核：能给陌生人用、不烧钱、坏了能发现、改了能回退、
问答体验接近常见产品**（不含多实例 / 高可用 / SLA）。Stage 5 是**门禁清单**，不是功能阶段——
只要站点公开，这里没有一项是可选的。

### 5.1 部署面

- 域名 + HTTPS + 反向代理；**按可信代理解析 `X-Forwarded-For`**（否则全体访客共用一个限流桶，design-11 §10.1）
- CORS 白名单切到真实源（本地默认只放行 5173 / 5174）
- 密钥只走环境变量；仓库与 `docs/` 里不得出现敏感值
- MySQL 备份 + **恢复演练**；关键词索引可重建；Qdrant 要么做快照，要么接受「可重建」

### 5.2 容量与成本

- 单实例是硬约束（进程内 Lucene 单写者）：接受它并公布维护窗口，或换外置检索（暂不计划）
- 声明单问预算（embedding + 重排 + 对话 token）；设日 / 月上限，并明确超限行为（降级，而不是 500）
- SSE 线程池（`2/16`，队列 0 → `server_busy`）就是并发上限，写进文档

### 5.3 可靠性

- 降级矩阵完整（向量挂→关键词；重排挂→融合序；关键词挂→向量；模型挂→摘录），且**每个降级都在响应里标记**
- 健康检查 + 外部可用性探测；日志轮转；Flyway 只追加，且需保证上一版向前兼容一版

### 5.4 安全与合规

- **T1 凭据轮换闭环**（公开暴露前的硬门禁，本人操作）
- 公开端点开 SecretGuard，管理端关（design-11 §11）
- 限流阈值按真实流量分布调，不用初始拍数
- 隐私说明 + AI 免责页；访问日志保留期定下来

### 5.5 发布与回滚

- 打 tag `stage/4`（后续 `stage/5`）；放量顺序：管理端内测 → 公开只读 → 公开问答
- 回滚 = 重新部署上一个 tag / 镜像；同一版本内不做破坏性迁移

### 5.6 明确接受的上限（不解决）

- 不做水平扩展、不做多区域、不做 SLA 承诺
- 重启期间不保证同步任务续跑（失败任务靠重试，不做断点续传）

### 5.7 上线就绪差距（2026-10-17 评估）

对照「能公开、不烧钱、坏了能发现、改了能回退」四条判据，当前缺口与归属（工作量为人日**估算**）：

| 缺口 | 归属 | 估算 |
| --- | --- | --- |
| **无 CI 回归门禁**：改检索 / 提示词 / 模型后没有任何东西挡指标回退 | Stage 5（建议前置到 4.3） | 2 |
| **评测对象 ≠ 线上对象**：323 条黄金集来自私有语料，公开语料还没有自己的黄金集 | 4.3（先建 20 条公开集，随篇数增长） | 1 |
| 只有检索指标（DocHit / SpanHit），**没有答案质量指标** | 4.3（人工小样本 + 抽样打分） | 1 |
| **成本无硬上限**：匿名 6/分钟 × 多 IP 即可失控 | 4.4（日预算 + 超限全局降级） | 1 |
| 无查询缓存：重复提问仍走一遍 embedding + rerank + LLM | 4.4（Redis 精确缓存，TTL 1h） | 1 |
| SSE 无心跳、429 无 `Retry-After` | 5.3 | 1 |
| 限流按单 IP 分桶（IPv6 /64 可绕过） | 5.1 | 0.5 |
| 无指标与 trace，只有日志行 | 5.3 | 2 |

合计约 10 人日（不含 4.1–4.3 主体）。**优先级最高的是前两条**：它们把「感觉变好了」变成「数字没退」，
也是唯一能阻止质量慢性下滑的机制。
> 提示词版本化原列在此表，2026-10-17 已被提为 **4.2 的正式交付项**（不再算差距），契约见 design-11 §16。

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

> 2026-10-17 起，**上线判据**以 D1–D7（`docs/ragnote-insights-and-architecture-takeaways.md` §5.5）与 Stage 5 门禁为准；
> 本节保留为一期发布的最低集，不再单独维护。

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
