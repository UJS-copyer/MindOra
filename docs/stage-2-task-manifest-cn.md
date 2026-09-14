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
