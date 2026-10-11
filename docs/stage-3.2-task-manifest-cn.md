# Stage 3.2 任务清单：关键词检索与 Hybrid/RRF 融合

基线提交：`cf1fd5a`（Stage 3.1 检索底座压缩合入 main 后的干净基线）

分支：

- 后端 `feature/stage-3.2-backend`（`fb416d0` → `10b73c1`）
- 前端 `feature/stage-3.2-frontend`（`3ebc175`）
- 集成 `feature/stage-3.2-integration`（合并 `ac93e3c`、`5cd3b5a`）

契约文档：`docs/design-10-keyword-retrieval-and-hybrid-fusion.md`
教学文档：`docs/rag-guide-cn.md`

## 本轮目标

在 Stage 3.1「向量召回 + 重排」的单路检索之上补齐**关键词召回**，并用 **RRF（Reciprocal Rank Fusion）** 融合两路结果，形成业界标配的 Hybrid Search；同时修正 Stage 3.1 遗留的一处**权限泄漏缺陷**。

## 任务状态

| ID | 所属 | 状态 | 验收 |
| --- | --- | --- | --- |
| H1 | backend | DONE | 引入 Lucene 9.12.3，统一版本属性，仅需 core + analysis-common |
| H2 | backend | DONE | 关键词索引端口 + Lucene 适配器（分析链 / 字段 / 原子替换 / 权限过滤） |
| H3 | backend | DONE | 关键词检索（查询构建与按字段精确过滤）— 合并入 H2 交付 |
| H4 | backend | DONE | 双路召回 + RRF 融合 + 融合后截断 + 命中来源标记 |
| H5 | backend | DONE | 索引生命周期接入（切片写入 / 归档移除 / 全量重建）与权限字段同步 |
| H6 | backend | DONE | 接口返回扩展与 OpenAPI 契约同步 |
| H7 | integration | DONE | design-10 契约、任务清单与项目状态更新 |
| L1 | frontend | DONE | 检索调试页展示命中来源、两路排名、RRF 分与降级状态 |
| I1 | integration | DONE | 集成、真实联调、压缩合回 main |

## 约束

- 不回滚未确认来源的工作区改动；禁止批量删除文件或目录。
- 每个小功能单独「编码 → 定向测试通过 → 提交」，不做跨功能大爆炸改动。
- 签名变更（`VectorStorePort`、`KnowledgeRetrievalService.RetrievalHit/Outcome/Settings`、`KeywordIndexPort`）必须在同一次提交内改齐全部调用方、测试替身与前端契约，保持编译与测试常绿。
- 外部服务（Gitee、Embedding、Rerank、向量库）只能经过 adapter/port 边界。
- 后台页面只写入 `frontend/apps/admin-art`。
- 本轮不新增 Flyway 迁移（关键词索引是派生数据，不落库）。
- `mindora-rag` 本轮不新增业务代码。
- 不实现对话、SSE、引用展示与公开问答入口。
- 前后端分属不同分支，跨端集成只落在 integration 分支。

## 已确认行为

- 关键词索引为**进程内嵌入式 Lucene**，索引目录 `<存储根>/lucene/keyword`，是 MySQL 切片的派生数据，可全量重建且**不消耗 embedding 额度**。
- 中文采用 **CJK 二元分词**（零未登录词），并对 `headingPath`/`chunk_id` 类标识符做拆分；索引侧展平 token 图形，查询侧不展平。
- 索引写入**以文档为粒度原子替换**，避免重新切片产生孤儿切片。
- 融合采用 **RRF（k=60）**：只用排名不用分数，BM25 与余弦相似度无需归一化。
- 默认参数：向量召回 30、关键词召回 30、融合后 20 进入重排、最终返回 8。
- 结果携带 `matchedBy`（`vector`/`keyword`/`both`）与两路排名，用于解释「为何被召回」；仅关键词命中时 `vectorScore` 为空。
- 两路召回**共用同一套权限过滤**；`scope` 必填，fail-closed。
- **MySQL 是权限权威来源**：索引权限字段仅用于缩小召回范围，最终按数据库当前状态复核。
- 关键词检索失败按质量特性降级为纯向量检索；嵌入/向量库不可用属能力缺失，直接报错。

## 验收证据

### 单元与集成测试

| 范围 | 结果 |
| --- | --- |
| `mindora-knowledge` | 70 个用例通过（检索用例 12 → 20，新增关键词适配器 6 个） |
| `mindora-app` | 23 个用例通过 |
| 全量（11 模块） | `BUILD SUCCESS`（后端分支与集成分支各执行一次） |
| 前端类型检查 | `vue-tsc --noEmit` 通过 |
| 契约一致性 | `npm run api:check` 退出码 0 |

### 现场联调（2026-09-27，真实数据 158 篇文档）

| 项目 | 结果 |
| --- | --- |
| 关键词索引重建 | 写入 **2195** 切片 / 3925ms（与 MySQL 有效切片数、Qdrant 点数三者一致） |
| 重启后端后 | 索引仍可用，检索结果逐条一致（确认持久化） |
| 双路候选 | 向量 30 / 关键词 30 |
| 命中来源 | 出现 `both` 与 `keyword`（**仅关键词命中**，`vectorRank` 为空）两类 |
| RRF 校验 | 抽样核算与公式逐条吻合，例：vRank=8、kRank=1 → `1/68 + 1/61 = 0.031099` |
| 重排生效 | 存在 RRF 分更高但重排分低而被降序的结果，符合「融合定候选、重排定序」 |
| 检索耗时 | 冷启动约 900ms（Lucene 首次打开），预热后约 400ms |
| 质量信号 | 无对应内容时全量结果 rerank 分 0.0001~0.0021；真实命中 0.80~0.99 |
| 权限隔离 | `scope=public` 仅返回唯一 public 文档且关键词候选为 0；同查询 `scope=all` 返回私有内容 |
| 前端联调 | 经真实后端渲染出新列（命中来源、两路排名 / RRF）与融合指标栏，8 条结果 |

## 缺陷修复记录

### 权限字段不同步导致私有内容泄漏（Stage 3.1 遗留）

- **现象**：索引中的 `visibility`/`knowledgeEnabled` 是写入时固化的。文档在索引后被改为私密时，公开检索**仍会命中它**。
- **根因**：`updateDocumentSettings` 只更新 MySQL，未回写派生索引。
- **修复**：① 新增 `VectorStorePort.updatePayload`，按版本回写 Qdrant payload（不重算 embedding），并在设置变更时同步两个索引；② 检索时按 MySQL 当前状态**再次校验**访问许可。
- **为什么第②层才是根本**：权限隔离不再依赖派生索引的新鲜度，因此回写可以尽力而为，文章发布/下线流程不再依赖索引可用性。
- **测试**：`KnowledgeServiceTest#syncsPermissionFieldsToBothIndexesWhenSettingsChange`、`KnowledgeRetrievalServiceTest#dropsDocumentsThatAreNoLongerPermittedAccordingToDatabase`。

### 关键词索引未启用时阻断写入流程（本轮自查发现）

- **现象**：测试环境关闭关键词索引后，文章发布会因 `keyword_adapter_unavailable` 失败。
- **根因**：占位适配器的写入路径抛错，与「关键词索引是可选派生索引」的定位矛盾。
- **修复**：占位实现**写入一律跳过**、检索抛错（由检索层降级）；`KnowledgeService` 内置默认实现与配置装配实现语义对齐。

## 运行约束（新增）

**Lucene 单写者锁**：同一索引目录同一时刻只能被一个进程打开。本地不能同时运行两个后端实例，否则第二个实例启动失败（`Lock held by another program: .../lucene/keyword/write.lock`）。多实例部署前必须改为远程/共享索引或调整写入模型。

## 未做项（明确排除）

- 生成层（LLM 作答）、引用标注、SSE 流式输出、公开问答入口与限流。
- 检索评测集（`Hit@K` / `MRR`）——列为下一阶段优先项。
- 查询改写（HyDE / Multi-Query / 拆解）、Contextual Retrieval、父子块检索、Agentic RAG / GraphRAG（理由见 `rag-guide-cn.md` 决策矩阵）。
- 线上语料残留的极少数单字符 `-` 切片（已记录，暂不修）。

---

## 接口与行为（2026-10-10 自 `project-status-cn.md` 迁入）

关键词检索与 Hybrid/RRF 融合。完整契约见 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`。

- `POST /api/v1/admin/knowledge/retrieval/search` 请求新增 `keywordEnabled`：关闭后退化为纯向量检索，用于对比两路各自贡献了哪些结果。
- 同接口响应新增 `keywordTopN`、`fusionTopM`、`keywordEnabled`、`vectorCandidates`、`keywordCandidates`、`keywordFallback`、`keywordFallbackReason`；`results[]` 新增 `rrfScore`、`vectorRank`、`keywordRank`、`matchedBy`（`vector` / `keyword` / `both`），且 `vectorScore` 变为**可空**（仅关键词命中时为 `null`）。
- 新增 `POST /api/v1/admin/knowledge/keyword-index/rebuild`：以 MySQL 当前切片全量重建关键词索引，返回写入切片数，**不调用 embedding**，因此不消耗模型额度。
- 检索链路：向量召回 30 + 关键词召回 30 → RRF（k=60）融合 → 前 20 进入重排 → 返回 8。
- 关键词索引为进程内嵌入式 Lucene（9.12.3），目录 `<存储根>/lucene/keyword`，中文采用 CJK 二元分词并对代码标识符做拆分。
- 新增配置：`mindora.knowledge.keyword.enabled`、`mindora.knowledge.keyword.index-path`、`mindora.knowledge.retrieval.keyword-enabled`、`mindora.knowledge.retrieval.keyword-top-n`、`mindora.knowledge.retrieval.fusion-top-m`、`mindora.knowledge.retrieval.rrf-k`。

### 权限修复（重要）

- **问题**：索引中的 `visibility`/`knowledgeEnabled` 是写入时固化的。文档在索引后被改为私密时，公开检索**仍会命中它**，构成私有内容泄漏（Stage 3.1 遗留缺陷）。
- **修复**：① 设置变更时回写两个派生索引的权限字段（Qdrant 经 payload 更新，不需重算 embedding）；② 检索时按 **MySQL 中的文档当前状态**复核访问许可。
- **结论**：**MySQL 是权限的权威来源，派生索引只用于缩小召回范围**。因此回写可以尽力而为，权限隔离不依赖索引新鲜度，文章发布/下线流程也不依赖索引可用性。

## 现场联调记录（2026-09-27，同上迁入）

关键词索引重建与 Hybrid/RRF 端到端验收，语料为真实笔记 158 篇。

- 关键词索引重建：`POST /api/v1/admin/knowledge/keyword-index/rebuild` 写入 **2195** 切片，耗时 3925ms，不调用 embedding。
- 三方一致性：MySQL 有效切片 2195 = Lucene 索引 2195 = Qdrant `points_count` 2195。
- 排查过程中的一处口径纠正：初次统计得到 MySQL 5801 条，与 Qdrant 2195 相差 3606，看似大量切片未写入向量库。核实后发现 `archiveChunks` 采用软删除（`failure_reason='archived'`），先前统计未排除历史代次的归档切片；补上该过滤条件后两侧完全一致，**不存在数据不一致缺陷**。
- 索引持久化：后端重启后关键词召回仍返回 30 条候选，检索结果逐条一致。
- 双路对比（查询「怎么把公司 NAS 挂载成本地盘符」）：
  - 双路：向量候选 30 / 关键词候选 30，出现**仅关键词命中**的结果（`matchedBy=keyword`、`vectorRank=null`）；
  - 关闭关键词：关键词候选 0，该结果消失，其余顺序基本不变。
- RRF 分值核算（抽样，与代码公式逐条吻合）：`both, vRank=5, kRank=1` → `1/65 + 1/61 = 0.031778`；`both, vRank=1, kRank=2` → `1/61 + 1/62 = 0.032522`；`vector, vRank=6` → `1/66 = 0.015152`；`both, vRank=8, kRank=1` → `1/68 + 1/61 = 0.031099`。
- 重排确实在重排：存在 RRF 分更高（0.0325）但因重排分低（0.0752）而排在第 3 的结果，符合「融合定候选、重排定序」。
- 权限隔离（最强证据）：库中仅 1 篇 public 文档。同一查询在 `scope=public` 下只返回该文档且**关键词候选为 0**，在 `scope=all` 下返回私有笔记（重排分 0.99）——证明是过滤生效，而非无匹配。
- 检索耗时：冷启动约 900ms（Lucene 首次打开索引），预热后约 400ms。
- 质量信号可用于拒答（**该推论已于 Stage 3.3 被数据证伪，见 `docs/stage-3.3-task-manifest-cn.md`**）：查询语料中不存在的内容时，全部结果重排分落在 0.0001~0.0021；真实命中为 0.80~0.99。
- 前端联调：`http://localhost:5175/#/knowledge/retrieval` 经真实后端返回 8 条结果，正确渲染新增的「命中来源」「两路排名 / RRF」列，以及「召回 → 融合 → 返回：30 → 20 → 8」指标栏。
- 运行约束复现：重启后端时因旧进程未退出，新实例因 Lucene 写锁（`write.lock`）启动失败，确认了单实例约束。
- 联调方式说明：前端通过应用自身的 Pinia store 注入会话后整页重载，未修改任何账号凭据；后端使用应用默认 JWT 密钥，既有登录会话保持有效。
