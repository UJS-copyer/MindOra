# MindOra 项目状态总览

本文用于记录当前项目迁移结果、启动方式、启动地址，以及前后端各阶段的开发状态，便于后续继续开发和回滚检查。

最近维护：2026-07-28

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
| Stage 3 | 未开始 | RAG 问答与对话链路待实现 |
| Stage 4 | 未开始 | 运维与可观测增强待实现 |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工作台壳、路由、请求层、权限基础、主题与布局能力已接入 |
| Stage 1 | 已完成 | 已迁移到 Art Design Pro 方案，并完成主要后台业务页面迁移与验证 |
| Stage 2 | 第一轮闭环已完成 | `frontend/apps/admin-art` 已增加知识库数据源、同步任务、文档、索引状态、失败重试、切片配置和模型配置页面；知识库 mock 默认关闭，联调默认请求真实接口 |
| Stage 3 | 未开始 | RAG 交互与问答页面待扩展 |
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

## Stage 2 数据边界

- 本地 Gitee 笔记仓库是只读知识源，外部访问通过 `GiteeRepositoryPort` 适配器封装。
- Markdown frontmatter 会被解析为元数据，不作为正文内容进入版本快照和切片。
- Markdown 图片引用会记录为资源关联入口，当前不批量上传未引用附件。
- 从 Gitee 知识文档生成博客文章时默认生成草稿并保留原知识文档关联；文章人工发布后才按文章可见性更新知识库公开状态。
- 在线创建的博客文章默认 `knowledgeEnabled=true`，发布后进入 `source_type=BLOG` 的知识文档；下线后改稿再发布会更新同一个 BLOG 知识文档并生成新版本，该流程不会写回 Gitee。
- 向量库当前实现为 Qdrant adapter，设计文档中的 Milvus 仍作为后续可替换目标，边界是 `VectorStorePort`。
- admin-art 知识库 mock 默认关闭，只有显式设置 `VITE_KNOWLEDGE_USE_MOCK=true` 才启用 mock。
- 后端 `application.yml` 已导入本地运行配置 `backend/mindora-app/config/mindora-local.yml`；该文件由 `docs/project-env-local.md` 生成并已加入 `.gitignore`，用于本机动态读取 Gitee、RocketMQ、Embedding、Rerank 和 Qdrant 配置。

## 前台流程

前台站点当前主流程如下：

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表
3. 从文章列表进入文章详情
4. 按分类或标签筛选文章
5. 从详情页返回列表或首页

当前前台站点的目标是提供博客展示与阅读入口，后续可继续补充搜索、标签页、归档和更多内容页。

## 下一阶段建议

Stage 2 后续应优先做现场联调和体验收口：

1. 在 IDEA 后端和 `http://localhost:5175/` 后台中配置真实 Gitee 笔记仓库并触发一次增量同步。
2. 检查同步任务、知识文档、版本、切片、索引状态和失败重试是否按真实数据展示。
3. 从知识文档生成草稿文章，在文章管理中确认内容后人工发布，再检查前台 `http://localhost:5173/` 可阅读。
4. 确认 RocketMQ、Embedding、Qdrant 在虚拟机/云端环境中可用后，再进入 Stage 3 RAG 对话和检索质量开发。

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
