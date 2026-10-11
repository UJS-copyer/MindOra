# Stage 2 任务清单：Knowledge Base Loop

基线提交：`b341a77`

主线：`feature/stage-2-knowledge-base-loop`

并行实现线：

- `feature/stage-2-backend-knowledge`：后端表、服务、适配器、任务和 HTTP 契约
- `feature/stage-2-frontend-knowledge`：`frontend/apps/admin-art` 知识库管理页面与 mock
- 主线 integration：OpenAPI 生成、前后端联调、项目状态、最终验证

## 任务状态

| ID | 所属 | 状态 | 验收 |
| --- | --- | --- | --- |
| KB-BE-01 | backend | DONE | 数据源、同步、文档、版本、切片、索引持久化与服务测试 |
| KB-BE-02 | backend | DONE | Gitee、embedding、Qdrant adapter 及失败重试 |
| KB-BE-03 | backend | DONE | 管理 HTTP API、OpenAPI、RocketMQ 异步边界 |
| KB-BE-04 | backend | DONE | 知识文档生成博客草稿、在线文章发布后进入知识库且不回写 Gitee |
| KB-FE-01 | frontend | DONE | 数据源和同步任务页面 |
| KB-FE-02 | frontend | DONE | 文档、索引、失败重试页面 |
| KB-FE-03 | frontend | DONE | 切片和模型配置页面 |
| KB-FE-04 | frontend | DONE | 文章编辑/列表增加知识库开关，知识文档生成草稿文章入口 |
| KB-INT-01 | integration | DONE | OpenAPI 同步到 `frontend/packages/types/src/generated/openapi.d.ts` |
| KB-INT-02 | integration | DONE_WITH_CONCERNS | 真实 Gitee + RocketMQ + MySQL + 后台页面联调通过；当前 IDEA 进程未注入 EMBEDDING_API_KEY，索引失败重试路径已验证，Embedding/Qdrant 成功写入需补齐运行环境配置 |

## 约束

- 不修改 `feature/stage-1-content-admin` 的提交和回滚点。
- 外部 Gitee、embedding、向量库调用只能经过 adapter/port 边界。
- 后台页面只写入 `frontend/apps/admin-art`。
- 不实现 Stage 3 RAG 对话页面。
- 禁止批量删除命令；删除操作只能针对单个明确文件路径。

## 已确认行为

- Gitee 笔记仓库作为私有只读知识源，同步、解析、切片和索引不回写仓库。
- 知识文档可以生成关联博客草稿文章，默认不自动发布；人工发布后才成为前台可阅读文章。
- 在线创建文章保持原有草稿、发布、下线流程；默认勾选进入知识库，发布后创建 `BLOG` 来源知识文档。
- 从知识文档生成的文章再次发布时复用原知识文档关联，避免在知识库中重复生成 `BLOG` 文档。

---

## 接口与行为（2026-10-10 自 `project-status-cn.md` 迁入）

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

## 数据边界（同上迁入）

- 本地 Gitee 笔记仓库是只读知识源，外部访问通过 `GiteeRepositoryPort` 适配器封装。
- Markdown frontmatter 会被解析为元数据，不作为正文内容进入版本快照和切片。
- Markdown 图片引用会记录为资源关联入口，当前不批量上传未引用附件。
- 从 Gitee 知识文档生成博客文章时默认生成草稿并保留原知识文档关联；文章人工发布后才按文章可见性更新知识库公开状态。
- 在线创建的博客文章默认 `knowledgeEnabled=true`，发布后进入 `source_type=BLOG` 的知识文档；下线后改稿再发布会更新同一个 BLOG 知识文档并生成新版本，该流程不会写回 Gitee。
- 向量库当前实现为 Qdrant adapter，设计文档中的 Milvus 仍作为后续可替换目标，边界是 `VectorStorePort`。
- admin-art 知识库 mock 默认关闭，只有显式设置 `VITE_KNOWLEDGE_USE_MOCK=true` 才启用 mock。
- 后端 `application.yml` 已导入本地运行配置 `backend/mindora-app/config/mindora-local.yml`；该文件由 `docs/project-env-local.md` 生成并已加入 `.gitignore`，用于本机动态读取 Gitee、RocketMQ、Embedding、Rerank 和 Qdrant 配置。

## 现场联调记录（同上迁入）

- 使用公开 Gitee `https://gitee.com/oschina/git-osc` 的 `README.md` 验证：RocketMQ 异步任务成功，MySQL 中生成知识文档、版本和 chunk。
- 使用本地笔记源 `https://gitee.com/fjw08/obsidian` 验证：无 token 时任务失败并记录 `Not Found Project`，符合私有源失败路径；配置真实访问令牌后复用同一数据源即可同步。
- 生成文章草稿验证通过：文章状态为 `draft`，保留 `knowledgeDocumentId` 回链，不自动发布。
- 浏览器验证通过：`/#/knowledge/sources`、`sync-tasks`、`documents`、`indexes`、`rag-config` 均展示真实接口数据；知识文档可打开关联文章编辑页，README 标题、slug 和正文正确回填。
- 2026-07-28 复查：刷新后台后 `/#/knowledge/documents` 可见知识库菜单和真实 `README` 文档，显示 Gitee 笔记来源、私有可见性、知识库开关、草稿已生成和打开文章入口。
- 同步任务页在无 queued/running 任务时 7 秒内无轮询请求；有活动任务时只做 5 秒静默状态刷新，不触发整页刷新。
- 当前运行环境未向 IDEA 后端注入 `EMBEDDING_API_KEY`，因此索引状态显示失败，失败原因为 `Embedding API key is not configured`，重试次数可见；这不是代码异常。
- 2026-07-28 复查：`GITEE_TOKEN` 可读取私有仓库根目录；Gitee 递归路径已改为按路径段编码，避免中文目录触发 `Not Found Project`。
- 2026-07-28 18:51 复查：使用 `https://gitee.com/fjw08/obsidian` 触发全量同步任务 `8`，RocketMQ 异步消费成功；扫描 Markdown `132` 个，处理 `132` 个，任务状态 `success`。
- 2026-07-28 18:52 复查：知识文档总数 `133`，索引状态 `indexed=133`、`failed=0`；私有 Gitee 文档 `133` 个，默认未自动生成在线草稿。
- 2026-07-28 18:52 复查：运行时配置读取外部 YAML 成功，embedding provider 为 `bailian`、模型 `text-embedding-v4`、维度 `1024`；rerank provider 为 `siliconflow`、模型 `BAAI/bge-reranker-v2-m3`。
- 2026-07-28 18:52 复查：Qdrant Cloud collection `mindora_chunk` 状态 `green`，named vector 使用 `dense`，points_count 从 `901` 增至 `902`，验证重建索引可写入真实向量库。
- 2026-07-28 18:53 复查：`POST /api/v1/admin/knowledge/rerank/preview` 返回真实 rerank 分数，embedding 和 rerank 外部模型链路已打通。
- 2026-07-28 18:56 复查：从非空 Gitee 知识文档 `论文/阅读线路.md` 手动生成在线文章草稿成功；文章状态 `draft`，带 `knowledgeDocumentId` 回链，公开列表 `/api/v1/public/articles` 不展示该草稿。
- 2026-07-28 18:57 复查：浏览器登录后台后 `/api/v1/admin/me` 无代理错误；知识库页面显示 `真实 API`。同步任务页只有存在 queued/running 任务时才 5 秒静默刷新，不会整页刷新。
