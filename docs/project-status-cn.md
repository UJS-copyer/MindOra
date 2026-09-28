# MindOra 项目状态总览

本文用于记录当前项目迁移结果、启动方式、启动地址，以及前后端各阶段的开发状态，便于后续继续开发和回滚检查。

最近维护：2026-09-19

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`
- 旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，并可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## Git 集成状态

- 当前 Stage 1 集成分支：`feature/stage-1-content-admin`
- 当前集成提交：`62a5cab`
- 当前 Stage 2 基线提交：`b341a77`
- 当前 Stage 2 主线分支：`feature/stage-2-knowledge-base-loop`
- 当前 Stage 2 开发分支：`feature/stage-2-frontend-knowledge`
- 当前 Stage 3 基线提交：`34e7697`
- 当前 Stage 3 主线分支：`feature/stage-3-retrieval-foundation`
- 后端内容分支已合并：`d5b160d`，来源为 `feature/stage-1-backend-content`
- 前端内容分支已合并：`62a5cab`，来源为 `feature/stage-1-frontend-content`
- Stage 1 回滚点未修改；当前工作区包含 Stage 2 开发改动，提交前需再次检查 `git status`
- `frontend/apps/admin` 仍保留为旧后台回退版本，后续新后台页面继续写入 `frontend/apps/admin-art`

## 启动方式

后端结构、模块边界、日志和常驻进程规则见 `docs/backend-development-rules-cn.md`。启动服务前先确认端口未被占用，常驻进程请在 IDEA 或独立终端中运行。

### 前台站点前端

仓库根目录执行：

```powershell
npm run dev:site
```

### 后台站点前端

仓库根目录执行：

```powershell
npm run dev --workspace @mindora/admin-art
```

### 后端

推荐在 IDEA 中运行 `com.mindora.app.MindOraApplication`。命令行启动时进入 `backend/` 后执行：

```powershell
cd backend
$env:LOG_FILE='..\logs\backend-dev.log'
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

本地运行日志统一写入仓库根目录 `logs/`，例如 `logs/backend-dev.log`、`logs/site-dev.log`、`logs/admin-art-dev.log`。不要把后端、前台、后台三个常驻服务拼到一个 PowerShell 复合命令中启动。

## 启动地址

### 前台站点

- `http://localhost:5173/`

### 前端后台

- `http://localhost:5175/`

### 后端接口

- `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 开发阶段状态

### 后端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工程、模块边界、认证、统一返回、基础测试等已落地 |
| Stage 1 | 已完成 | 内容与后台管理能力已接入，包含文章、分类标签、资产、登录与管理页 |
| Stage 2 | 第一轮闭环已完成 | 已实现 Gitee Markdown 同步、文档版本、切片、Embedding/Qdrant 适配、MQ 异步任务、索引状态、失败重试、知识文档生成草稿文章和博客文章发布后进入知识库 |
| Stage 3 | 第一轮检索底座已完成 | 切片元数据化、向量 payload 富化与点生命周期修复、向量检索读链路、管理端检索调试接口；对话/SSE/引用展示/关键词检索仍未开始 |
| Stage 4 | 未开始 | 运维与可观测增强待实现 |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工作台壳、路由、请求层、权限基础、主题与布局能力已接入 |
| Stage 1 | 已完成 | 已迁移到 Art Design Pro 方案，并完成主要后台业务页面迁移与验证 |
| Stage 2 | 第一轮闭环已完成 | `frontend/apps/admin-art` 已增加知识库数据源、同步任务、文档、索引状态、失败重试、切片配置和模型配置页面；知识库 mock 默认关闭，联调默认请求真实接口 |
| Stage 3 | 检索调试页已完成 | `frontend/apps/admin-art` 新增 `/knowledge/retrieval` 页面 |
| Stage 4 | 未开始 | 监控、告警、运维视图待扩展 |

## 当前检查结果

- `cd backend; mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-knowledge,mindora-blog,mindora-app -am test` 通过
- `cd backend; mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-user,mindora-app -am test` 通过；新增后台菜单知识库入口契约测试
- `npm run api:sync` 通过，已更新 `docs/openapi/openapi.json` 和 `frontend/packages/types/src/generated/openapi.d.ts`
- `npm run api:check` 通过
- `npm run typecheck --workspace @mindora/admin-art` 通过
- `npm run build --workspace @mindora/admin-art` 通过
- `git diff --check` 通过；仅有 Windows `LF will be replaced by CRLF` 提示
- `npm run test --workspace @mindora/admin-art` 通过；当前无前端测试文件，脚本使用 `--passWithNoTests`
- 浏览器冷启动验证 `/#/500` 正常
- 文章编辑、用户管理、角色管理、菜单管理、分类与标签、资产库、404/403/500 页面可正常渲染
- 前端控制模式已接入；后端已提供 `/api/v1/admin/me` 和 `/api/v1/admin/menus`
- 当前后端管理接口仍以 `SUPER_ADMIN` 访问控制为主，动态后端权限模式尚未切换为 `VITE_ACCESS_MODE=backend`

## Stage 2 接口与行为

- `GET/POST/PUT /api/v1/admin/knowledge/sources`：配置 Gitee 数据源。
- `POST /api/v1/admin/knowledge/sources/{id}/sync`：触发增量或全量同步；默认通过 RocketMQ 派发异步任务，本地测试可切换 `mindora.knowledge.dispatcher=executor`。
- `GET /api/v1/admin/knowledge/tasks`、`POST /api/v1/admin/knowledge/tasks/{id}/retry`：查看同步任务和失败重试。
- `GET/PUT /api/v1/admin/knowledge/documents`、`GET /api/v1/admin/knowledge/documents/{id}/versions`、`GET /api/v1/admin/knowledge/documents/{id}/chunks`：查看文档、版本、切片和知识库启用状态。
- `POST /api/v1/admin/knowledge/documents/{id}/article-draft`：将知识文档生成关联博客草稿文章；不会自动公开发布，需到文章管理中人工确认发布。
- `POST /api/v1/admin/knowledge/documents/{id}/publish`：兼容旧路径，行为与 `article-draft` 相同。
- `POST /api/v1/admin/knowledge/documents/{id}/reindex`、`POST /api/v1/admin/knowledge/documents/reindex`：单文档或批量重建索引。
- `GET/PUT /api/v1/admin/knowledge/config/chunking`：查看和更新基础切片策略。
- `GET /api/v1/admin/knowledge/config/runtime`：查看当前后端读取到的 embedding、rerank 和 Qdrant 运行配置。
- `POST /api/v1/admin/knowledge/rerank/preview`：用于 Stage 2 联调时验证 rerank provider 连通性，暂不接入 RAG 对话页。
- `POST /api/v1/admin/articles/{id}/knowledge`：控制在线文章是否进入知识库；在线文章流程仍保持草稿、发布、下线，不回写 Gitee 仓库。

## Stage 3 接口与行为

- `POST /api/v1/admin/knowledge/retrieval/search`：管理端知识库检索调试接口。
  - 请求参数：`query`（检索词）、`scope`（必填，`all` 或 `public`）、`topN`（候选切片数）、`rerankTopK`（重排返回切片数）、`rerankEnabled`（是否启用重排）。
  - 响应参数：包含 `scope`、`topN`、`rerankTopK`、`rerankEnabled`、`fallback`、`fallbackReason`、`latencyMs` 以及 `results` 列表（切片项含 `chunkId`、`documentId`、`documentTitle`、`sourcePath`、`headingPath`、`blockType`、`vectorScore`、`rerankScore`、`contentPreview` 等）。
- `GET /api/v1/admin/knowledge/documents/{id}/chunks`：切片列表接口响应新增 `headingPath`、`headingLevel`、`blockType`、`codeLanguage` 四个元数据字段。
- 检索与模型超时配置：
  - `mindora.knowledge.retrieval.top-n`：向量召回候选数。
  - `mindora.knowledge.retrieval.rerank-top-k`：重排最终返回数。
  - `mindora.knowledge.retrieval.rerank-enabled`：是否默认开启 rerank 重排。
  - `mindora.knowledge.retrieval.fallback-enabled`：重排失败或异常时是否降级为纯向量相似度排序。
  - `mindora.knowledge.embedding.timeout-ms`：Embedding 外部调用超时时间（毫秒）。
  - `mindora.knowledge.rerank.timeout-ms`：Rerank 外部调用超时时间（毫秒）。

## Stage 3.2 接口与行为

关键词检索与 Hybrid/RRF 融合。完整契约见 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`，选型理由见 `docs/rag-guide-cn.md`。

- `POST /api/v1/admin/knowledge/retrieval/search` 请求新增 `keywordEnabled`：关闭后退化为纯向量检索，用于对比两路各自贡献了哪些结果。
- 同接口响应新增 `keywordTopN`、`fusionTopM`、`keywordEnabled`、`vectorCandidates`、`keywordCandidates`、`keywordFallback`、`keywordFallbackReason`；`results[]` 新增 `rrfScore`、`vectorRank`、`keywordRank`、`matchedBy`（`vector` / `keyword` / `both`），且 `vectorScore` 变为**可空**（仅关键词命中时为 `null`）。
- 新增 `POST /api/v1/admin/knowledge/keyword-index/rebuild`：以 MySQL 当前切片全量重建关键词索引，返回写入切片数，**不调用 embedding**，因此不消耗模型额度。
- 检索链路：向量召回 30 + 关键词召回 30 → RRF（k=60）融合 → 前 20 进入重排 → 返回 8。
- 关键词索引为进程内嵌入式 Lucene（9.12.3），目录 `<存储根>/lucene/keyword`，中文采用 CJK 二元分词并对代码标识符做拆分。
- 新增配置：`mindora.knowledge.keyword.enabled`、`mindora.knowledge.keyword.index-path`、`mindora.knowledge.retrieval.keyword-enabled`、`mindora.knowledge.retrieval.keyword-top-n`、`mindora.knowledge.retrieval.fusion-top-m`、`mindora.knowledge.retrieval.rrf-k`。

### Stage 3.2 权限修复（重要）

- **问题**：索引中的 `visibility`/`knowledgeEnabled` 是写入时固化的。文档在索引后被改为私密时，公开检索**仍会命中它**，构成私有内容泄漏（Stage 3.1 遗留缺陷）。
- **修复**：① 设置变更时回写两个派生索引的权限字段（Qdrant 经 payload 更新，不需重算 embedding）；② 检索时按 **MySQL 中的文档当前状态**复核访问许可。
- **结论**：**MySQL 是权限的权威来源，派生索引只用于缩小召回范围**。因此回写可以尽力而为，权限隔离不依赖索引新鲜度，文章发布/下线流程也不依赖索引可用性。

### Stage 3.2 运行约束

- **Lucene 单写者锁**：同一索引目录同一时刻只能被一个进程打开。本地**不能同时运行两个后端实例**，否则第二个实例启动即失败（`Lock held by another program: .../lucene/keyword/write.lock`）。多实例部署前必须改为远程/共享索引或调整写入模型。

## Stage 2 数据边界

- 本地 Gitee 笔记仓库是只读知识源，外部访问通过 `GiteeRepositoryPort` 适配器封装。
- Markdown frontmatter 会被解析为元数据，不作为正文内容进入版本快照和切片。
- Markdown 图片引用会记录为资源关联入口，当前不批量上传未引用附件。
- 从 Gitee 知识文档生成博客文章时默认生成草稿并保留原知识文档关联；文章人工发布后才按文章可见性更新知识库公开状态。
- 在线创建的博客文章默认 `knowledgeEnabled=true`，发布后进入 `source_type=BLOG` 的知识文档；下线后改稿再发布会更新同一个 BLOG 知识文档并生成新版本，该流程不会写回 Gitee。
- 向量库当前实现为 Qdrant adapter，设计文档中的 Milvus 仍作为后续可替换目标，边界是 `VectorStorePort`。
- admin-art 知识库 mock 默认关闭，只有显式设置 `VITE_KNOWLEDGE_USE_MOCK=true` 才启用 mock。
- 后端 `application.yml` 已导入本地运行配置 `backend/mindora-app/config/mindora-local.yml`；该文件由 `docs/project-env-local.md` 生成并已加入 `.gitignore`，用于本机动态读取 Gitee、RocketMQ、Embedding、Rerank 和 Qdrant 配置。

## Stage 3 数据边界

- 向量库目标已从 Qdrant Cloud 切换为 VM 本地 Qdrant（`http://192.168.222.128:6333`），集合为 `mindora_chunk`，named vector 使用 `dense`（1024 维，Cosine 相似度）。
- 向量 payload 仅存储最小检索与权限字段（`chunkId`、`documentId`、`versionId`、`sequence`、`sourceType`、`sourceId`、`sourcePath`、`visibility`、`status`、`knowledgeEnabled`、`blockType`、`contentHash`、`articleId` 以及层级 `headingPath`），切片正文不存入 payload，检索召回后按 `chunkId` 回查 MySQL 组装正文与预览。
- 权限隔离采用 fail-closed 策略：`PUBLIC` 检索范围在 Qdrant 侧强制过滤 `visibility=public` 且 `status=active` 且 `knowledgeEnabled=true`；若请求中的 `scope` 缺失或不合法，直接报错拒绝，杜绝越权召回私有切片。
- 向量点生命周期与点 ID 修复：向量点 ID 与 MySQL 中 `knowledge_chunk.id` 保持一致，修复了此前 `vector_point_id` 记录 `documentId:index` 的错配；文档重建索引或版本更新时会先显式清理旧版本向量点，避免脏点残留与点堆积。
- 索引重建要求：因切片嵌入文本（标题路径拼接正文）与 payload 结构变更，Stage 3 部署后必须执行一次全量重建索引，集合点数会从零重新累积。

## 前台流程

前台站点当前主流程如下：

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表
3. 从文章列表进入文章详情
4. 按分类或标签筛选文章
5. 从详情页返回列表或首页

当前前台站点的目标是提供博客展示与阅读入口，后续可继续补充搜索、标签页、归档和更多内容页。

## 下一阶段建议

Stage 3.2 已把检索的前四环（切块 / 索引 / 双路召回 / 重排）做成**可观测、权限正确**的底座；**第⑤环（生成作答）与评测仍为空白**。Stage 3.3 建议按以下顺序推进：

1. **先建检索评测基线（优先，成本最低）**：人工标注 30~50 条真实问答对（每条标出答案应在哪个文档/切片），用脚本统计 `Hit@8` 与 `MRR`。没有它，`rrf-k`、`fusion-top-m`、关键词开关与重排开关的调整都只能靠感觉；它同时是确定「重排分熔断阈值」的依据（实测真实命中 0.80~0.99，无对应内容 0.0001~0.0021）。
2. **再做生成层（`mindora-rag`）**：上下文组装（携带 `headingPath`）→ 严格 grounding 提示词 → LLM 作答 → 标注引用。要求「上下文无依据时明确回答不知道」，并按重排分阈值在调用 LLM 前短路拒答以省成本。
3. **再做引用溯源**：响应携带 `citations[]`（`chunkId` / `documentId` / `headingPath` / `sourcePath`），前端可点击跳转原文。
4. **然后做流式输出（SSE）**与生成侧降级。
5. **最后做公开问答入口与限流**（前台 `/chat`）。

暂不引入（待评测数据支撑后再评估）：HyDE / Multi-Query 查询改写、Contextual Retrieval 上下文前缀、父子块检索、Agentic RAG 与 GraphRAG。理由见 `rag-guide-cn.md` 第 2 章与决策矩阵。

## Stage 2 现场联调记录

- 使用公开 Gitee `https://gitee.com/oschina/git-osc` 的 `README.md` 验证：RocketMQ 异步任务成功，MySQL 中生成知识文档、版本和 chunk。
- 使用本地笔记源 `https://gitee.com/fjw08/obsidian` 验证：无 token 时任务失败并记录 `Not Found Project`，符合私有源失败路径；配置真实访问令牌后复用同一数据源即可同步。
- 生成文章草稿验证通过：文章状态为 `draft`，保留 `knowledgeDocumentId` 回链，不自动发布。
- 浏览器验证通过：`/#/knowledge/sources`、`sync-tasks`、`documents`、`indexes`、`rag-config` 均展示真实接口数据；知识文档可打开关联文章编辑页，README 标题、slug 和正文正确回填。
- 2026-07-28 复查：刷新后台后 `/#/knowledge/documents` 可见知识库菜单和真实 `README` 文档，显示 Gitee 笔记来源、私有可见性、知识库开关、草稿已生成和打开文章入口。
- 同步任务页在无 queued/running 任务时 7 秒内无轮询请求；有活动任务时只做 5 秒静默状态刷新，不触发整页刷新。
- 当前运行环境未向 IDEA 后端注入 `EMBEDDING_API_KEY`，因此索引状态显示失败，失败原因为 `Embedding API key is not configured`，重试次数可见；这不是代码异常。
- 2026-07-28 复查：`GITEE_TOKEN` 可读取私有仓库根目录；Gitee 递归路径已改为按路径段编码，避免中文目录触发 `Not Found Project`。需要重启 IDEA 后端后继续验证全量同步、embedding、Qdrant 写入和 rerank。
- 2026-07-28 18:51 复查：使用 `https://gitee.com/fjw08/obsidian` 触发全量同步任务 `8`，RocketMQ 异步消费成功；扫描 Markdown `132` 个，处理 `132` 个，任务状态 `success`。
- 2026-07-28 18:52 复查：知识文档总数 `133`，索引状态 `indexed=133`、`failed=0`；私有 Gitee 文档 `133` 个，默认未自动生成在线草稿。
- 2026-07-28 18:52 复查：运行时配置读取外部 YAML 成功，embedding provider 为 `bailian`、模型 `text-embedding-v4`、维度 `1024`；rerank provider 为 `siliconflow`、模型 `BAAI/bge-reranker-v2-m3`。
- 2026-07-28 18:52 复查：Qdrant Cloud collection `mindora_chunk` 状态 `green`，named vector 使用 `dense`，points_count 从 `901` 增至 `902`，验证重建索引可写入真实向量库。
- 2026-07-28 18:53 复查：`POST /api/v1/admin/knowledge/rerank/preview` 返回真实 rerank 分数，embedding 和 rerank 外部模型链路已打通。
- 2026-07-28 18:56 复查：从非空 Gitee 知识文档 `论文/阅读线路.md` 手动生成在线文章草稿成功；文章状态 `draft`，带 `knowledgeDocumentId` 回链，公开列表 `/api/v1/public/articles` 不展示该草稿。
- 2026-07-28 18:57 复查：浏览器登录后台后 `/api/v1/admin/me` 无代理错误；知识库页面显示 `真实 API`。同步任务页只有存在 queued/running 任务时才 5 秒静默刷新，不会整页刷新。

## Stage 3 现场联调记录

- VM 基础服务（MySQL / Redis / Qdrant / RocketMQ）位于 192.168.222.128；Qdrant 目标已从被回收的 Qdrant Cloud 免费集群切换为 VM 本地 `http://192.168.222.128:6333`。
- 后端 8080、后台前端 5175 均已运行；V6 迁移已应用，`knowledge_chunk` 新增 `heading_path` / `heading_level` / `block_type` / `code_language` / `content_hash` 五列。
- Qdrant 集合 `mindora_chunk` 由后端按需创建，使用 named vector `dense`（1024 维，Cosine）。
- 切片样本 payload 含 chunkId / documentId / versionId / sequence / sourceType / sourceId / sourcePath / visibility / status / knowledgeEnabled / blockType / contentHash / articleId；headingPath 已在 26 个切片上按「A > B > C」层级写入。
- 向量点 ID 已与 chunkId 一致，修正了此前 `vector_point_id` 记录 `documentId:index` 的错配。
- 管理端检索接口 `POST /api/v1/admin/knowledge/retrieval/search` 实测返回 200 与真实召回结果（含 documentTitle、sourcePath、headingPath、blockType、vectorScore、contentPreview）。
- 实测 rerank 因 SiliconFlow 账户余额不足返回 402，服务按 `fallback-enabled=true` 降级为向量相似度顺序，并返回 `fallback=true` 与 `fallbackReason`，验证了 design-04 的降级链路。
- 端到端联调发现并修复两个缺陷：
  1. 其一，Qdrant `points/search` 的命名向量必须使用 `{name, vector}` 形式，此前沿用写入时的 `{name: [...]}` 映射会被 Qdrant 以 400 JSON 格式错误拒绝；
  2. 其二，段落紧邻围栏代码块且中间无空行时，围栏会被并入普通段落，导致 code 块与 blockType 丢失。两者均已补充回归测试。
- 已知限制：超过 maximumSize 的代码块保持原子可能超限；Setext 仅识别紧邻的单行标题文本。
- 切片器缺陷修复：支持 Setext 标题、剔除分隔线噪声切片、实现 FIXED_SIZE 策略，并按 spec/quality 评审意见修正了「FIXED_SIZE 跨章节切片一律套用首个章节元数据」与「连续分隔线被误判为标题」两个问题，均补充回归测试。

### 全量重建与端到端验收（2026-09-19）

- 全量重建：133 篇文档分 7 批执行，全部返回 200，总耗时约 66 秒。
- 一致性：MySQL `index_status='indexed'` 切片 `2011` 条，Qdrant `mindora_chunk` `points_count` 同样为 `2011`，两侧完全对齐。
- 元数据覆盖：`1521` 条切片带 `heading_path`（约 76%）；块类型分布为 paragraph 1344、list 454、code 110、table 91、quote 12。
- 点生命周期：对同一文档连续两次重建，`points_count` 保持 `2011` 不变，确认重建为替换而非追加。
- 权限隔离：语料为 132 篇 private + 1 篇 public；`scope=public` 的命中全部来自该唯一 public 文档，无私有内容泄漏。
- fail-closed：缺少 `scope` 的请求返回 `retrieval_scope_required`（400），未被放行。
- 降级可见：SiliconFlow 账户已充值，rerankEnabled=true 时 fallback=false 并返回真实 rerankScore，且排序相对向量序发生变化（向量分 0.573 的切片被重排到第 1 位 rerank 0.9012，向量分 0.682 的切片降到第 2 位 rerank 0.8242，无关切片 rerank 分接近 0 并沉到末尾）；rerankEnabled=false 时按向量分排序、rerankScore 为 null；降级路径仅在 rerank 不可用时触发。
- 浏览器验证：`http://localhost:5175/#/knowledge/retrieval` 输入查询后返回 8 条真实结果，正确渲染耗时、命中条数、生效范围、重排状态、降级状态，以及文档标题、章节路径、来源路径、块类型、向量分与内容预览。
- 检索质量抽样：查询「Java 项目 幂等 重试 补偿」在 `scope=all` 下命中「众安康项目」「JAVASE」「Redis-黑马点评项目笔记」等语义相关文档，其中 code 块带出 `项目结构 > 异常处理` 章节路径，延迟约 103 ms。

### 切片器修复后复验（2026-09-19）

- 复验方式：后端重启加载切片器修复后，对全部 133 篇文档执行全量重建索引。
- 重建：133 篇分 7 批，全部返回 200，总耗时约 67 秒。
- 切片总数：修复前 2011 → 修复后 1988。
- 带 heading_path 的切片：修复前 1521 → 修复后 1508；覆盖率 75.63% → 75.86%（比例略有提升）。
- 完全无 heading_path 的文档数：37 → 36。
- Setext 修复在真实语料上生效：README 文档（id 前缀 af2e9f82）的 heading_path 覆盖由 0 提升到 5，出现形如「Gitee > 部署」的层级路径。
- 共 2 篇文档覆盖数提升：596f6162（47→55）、af2e9f82（0→5）。
- 5 篇文档覆盖数下降（最大为 8d12718e 由 117 降至 106）。原因是这些文档中的分隔线此前会被当作普通段落参与合并、额外抬高切片数与切片长度；剔除分隔线后切片数减少。逐篇覆盖率比例基本不变（例如 8d12718e 由 117/118=99.2% 变为 106/107=99.1%），不构成元数据质量回退。
- 噪声切片：仅剩 1 条内容为单个「-」的切片。单个连字符不足 3 个，按 CommonMark 也不是分隔线，因此将其作为普通段落是正确行为，仅记录为极小的已知语料噪声。
- 向量库与数据库一致性：Qdrant points_count = 1988，MySQL index_status=indexed 切片数 = 1988，两侧一致，集合状态 green。
- 重建后块类型分布：paragraph 1322、list 453、code 110、table 91、quote 12。
- 重建后检索抽检（rerankEnabled=true）：fallback=false，延迟 441ms，返回真实 rerank 分 0.9003 / 0.8251 / 0.0123，命中切片带形如「负责的核心链路 > 支付后创建合同。」的章节路径。

## Stage 3.2 现场联调记录（2026-09-27）

关键词索引重建与 Hybrid/RRF 端到端验收，语料为真实笔记 158 篇。

- 关键词索引重建：`POST /api/v1/admin/knowledge/keyword-index/rebuild` 写入 **2195** 切片，耗时 3925ms，不调用 embedding。
- 三方一致性：MySQL 有效切片 2195 = Lucene 索引 2195 = Qdrant `points_count` 2195。
- 排查过程中的一处口径纠正：初次统计得到 MySQL 5801 条，与 Qdrant 2195 相差 3606，看似大量切片未写入向量库。核实后发现 `archiveChunks` 采用软删除（`failure_reason='archived'`），先前统计未排除历史代次的归档切片；补上该过滤条件后两侧完全一致，**不存在数据不一致缺陷**。
- 索引持久化：后端重启后关键词召回仍返回 30 条候选，检索结果逐条一致。
- 双路对比（查询「怎么把公司 NAS 挂载成本地盘符」）：
  - 双路：向量候选 30 / 关键词候选 30，出现**仅关键词命中**的结果（`matchedBy=keyword`、`vectorRank=null`）；
  - 关闭关键词：关键词候选 0，该结果消失，其余顺序基本不变。
- RRF 分值核算（抽样，与代码公式逐条吻合）：
  - `both, vRank=5, kRank=1` → `1/65 + 1/61 = 0.031778`；
  - `both, vRank=1, kRank=2` → `1/61 + 1/62 = 0.032522`；
  - `vector, vRank=6` → `1/66 = 0.015152`；
  - `both, vRank=8, kRank=1` → `1/68 + 1/61 = 0.031099`。
- 重排确实在重排：存在 RRF 分更高（0.0325）但因重排分低（0.0752）而排在第 3 的结果，符合「融合定候选、重排定序」。
- 权限隔离（最强证据）：库中仅 1 篇 public 文档。同一查询在 `scope=public` 下只返回该文档且**关键词候选为 0**，在 `scope=all` 下返回私有笔记（重排分 0.99）——证明是过滤生效，而非无匹配。
- 检索耗时：冷启动约 900ms（Lucene 首次打开索引），预热后约 400ms。
- 质量信号可用于拒答：查询语料中不存在的内容时，全部结果重排分落在 0.0001~0.0021；真实命中为 0.80~0.99。
- 前端联调：`http://localhost:5175/#/knowledge/retrieval` 经真实后端返回 8 条结果，正确渲染新增的「命中来源」「两路排名 / RRF」列，以及「召回 → 融合 → 返回：30 → 20 → 8」指标栏。
- 运行约束复现：重启后端时因旧进程未退出，新实例因 Lucene 写锁（`write.lock`）启动失败，确认了单实例约束（详见 Stage 3.2 运行约束）。
- 联调方式说明：前端通过应用自身的 Pinia store 注入会话后整页重载，未修改任何账号凭据；后端使用应用默认 JWT 密钥，既有登录会话保持有效。

