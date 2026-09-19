# Stage 3 任务清单：Retrieval Foundation（检索底座）

基线提交：`34e7697`（Stage 2 收口后的干净基线）

主线：`feature/stage-3-retrieval-foundation`

上游分支：`feature/stage-2-knowledge-base-loop`

## 本轮目标

在 Stage 2 已打通的「Gitee/Obsidian → 知识文档 → 切片 → Embedding → 向量库」写入链路之上补齐**检索读取链路**，并修正 Stage 2 遗留的「切片无语义元数据 + 向量 payload 无权限字段」问题。

## 任务状态

| ID | 所属 | 状态 | 验收 |
| --- | --- | --- | --- |
| F0a | integration | DONE | Stage 2 遗留工作区按主题分组提交，形成干净基线 |
| F0b | config | DONE | 向量库切换到 VM 本地 Qdrant，集合名默认值对齐 |
| F1 | backend | DONE | 切片元数据模型与 MarkdownChunker 块级重写 |
| F2 | backend | DONE | V6 迁移持久化切片标题路径、块类型与语言元数据 |
| F3 | backend | DONE | Embedding 与 Rerank 适配器可配置超时 |
| F4 | backend | DONE | 向量 payload 富化，修复点堆积与点 ID 不一致 |
| F5 | backend | DONE | 向量检索端口与 Qdrant search |
| F6 | backend | DONE | 检索编排服务与 fail-closed 权限过滤 |
| F7 | backend | DONE | 管理端检索接口与 OpenAPI 契约 |
| F8 | backend | DONE | 后台菜单新增检索调试入口 |
| F9 | frontend | DONE | 后台检索调试页 |
| F10 | integration | DONE | 全量重建、端到端验证与文档 |

## 约束

- 不回滚未确认来源的工作区改动；禁止批量删除文件或目录。
- 每个小功能单独「编码 → 定向测试通过 → 提交」，不做跨功能大爆炸改动。
- 签名变更（如 `VectorStorePort`、`KnowledgeChunk`）必须在同一次提交内改齐全部调用方与测试替身，保持编译与测试常绿。
- 外部 Gitee、embedding、rerank、向量库调用只能经过 adapter/port 边界。
- 后台页面只写入 `frontend/apps/admin-art`。
- Flyway 只新增 `V6__*.sql`，不改动 V1–V5。
- `mindora-rag` 本轮不新增业务代码。
- 不实现 Stage 3 的对话、SSE、引用展示与公开问答入口。
- 后端与向量库目标变更后必须重新加载后端进程并执行全量重建索引。

## 已确认行为

- 向量库目标为 VM 本地 Qdrant（`http://192.168.222.128:6333`），不再使用会因闲置被回收的 Qdrant Cloud 免费集群。
- 切片按 Markdown 块级结构切分，代码块与表格作为原子块不切断，并携带 `headingPath`/`headingLevel`/`blockType`/`codeLanguage`。
- 向量 payload 只存最小检索与权限字段，正文由 MySQL 按 `chunkId` 回查。
- 公开检索必须带 `visibility=public` + `status=active` + `knowledgeEnabled=true` 过滤；过滤无法确定时 fail-closed，不放行。
- Gitee 笔记仓库仍为只读知识源，同步、解析、切片与索引不回写仓库。

## 验证证据（F10 收口）

- VM 基础服务（MySQL / Redis / Qdrant / RocketMQ）位于 192.168.222.128；Qdrant 目标已从被回收的 Qdrant Cloud 免费集群切换为 VM 本地 http://192.168.222.128:6333。
- 后端 8080、后台前端 5175 均已运行；V6 迁移已应用，knowledge_chunk 新增 heading_path / heading_level / block_type / code_language / content_hash 五列。
- Qdrant 集合 mindora_chunk 由后端按需创建，使用 named vector dense（1024 维，Cosine）。
- 切片样本 payload 含 chunkId / documentId / versionId / sequence / sourceType / sourceId / sourcePath / visibility / status / knowledgeEnabled / blockType / contentHash / articleId；headingPath 已在 26 个切片上按「A > B > C」层级写入。
- 向量点 ID 已与 chunkId 一致，修正了此前 vector_point_id 记录 documentId:index 的错配。
- 管理端检索接口 POST /api/v1/admin/knowledge/retrieval/search 实测返回 200 与真实召回结果（含 documentTitle、sourcePath、headingPath、blockType、vectorScore、contentPreview）。
- 实测 rerank 因 SiliconFlow 账户余额不足返回 402，服务按 fallback-enabled=true 降级为向量相似度顺序，并返回 fallback=true 与 fallbackReason，验证了 design-04 的降级链路。
- 端到端联调发现并修复两个缺陷：其一，Qdrant points/search 的命名向量必须使用 {name, vector} 形式，此前沿用写入时的 {name: [...]} 映射会被 Qdrant 以 400 JSON 格式错误拒绝；其二，段落紧邻围栏代码块且中间无空行时，围栏会被并入普通段落，导致 code 块与 blockType 丢失。两者均已补充回归测试。
- 已知限制与切片器现状：Setext 标题已支持（含 === 与 ---，仅限紧邻单行标题文本）；分隔线（--- / *** / ___）不再产生噪声切片；ChunkStrategy.FIXED_SIZE 策略已实现且每个切片按起始偏移归属真实章节。仍然存在的限制：超过 maximumSize 的代码块保持原子可能超限；多行文本加 Setext 下划线会退化为普通段落 + 二级标题。

## F10 全量重建与端到端验收结果

- 全量重建：133 篇文档分 7 批执行，全部返回 200，总耗时约 66 秒。
- 一致性：MySQL `index_status='indexed'` 切片 `2011` 条，Qdrant `mindora_chunk` `points_count` 同样为 `2011`，两侧完全对齐。
- 元数据覆盖：`1521` 条切片带 `heading_path`（约 76%）；块类型分布为 paragraph 1344、list 454、code 110、table 91、quote 12，说明代码块与表格的原子切分在真实语料上生效。
- 点生命周期：对同一文档连续两次重建，`points_count` 保持 `2011` 不变，确认重建是替换而非追加。
- 权限隔离：语料为 132 篇 private + 1 篇 public；`scope=public` 检索返回的命中全部来自该唯一 public 文档，无私有内容泄漏；`scope=all` 与 `scope=public` 均正常返回。
- fail-closed：缺少 `scope` 的请求返回 `retrieval_scope_required`（400），未被放行。
- 降级：rerank 因 SiliconFlow 余额不足返回 402，接口按 `fallback-enabled=true` 降级为向量序，响应 `fallback=true` 并携带 `fallbackReason`，后台页面同步显示降级提示条。
- 浏览器验证：`http://localhost:5175/#/knowledge/retrieval` 输入查询后返回 8 条真实结果，页面正确渲染耗时、命中条数、生效范围、重排状态、降级状态，以及文档标题、章节路径、来源路径、块类型、向量分与内容预览。
- 检索质量抽样：查询「Java 项目 幂等 重试 补偿」在 `scope=all` 下命中「众安康项目」「JAVASE」「Redis-黑马点评项目笔记」等语义相关文档，其中 code 块带出 `项目结构 > 异常处理` 章节路径，延迟约 103 ms。

## 缺陷修复与评审记录（Stage 3 收尾）

- **本轮修复的三个问题**：
  - **Setext 标题支持**：支持 Setext 标题（`===` 为一级、`---` 为二级），与 ATX 标题（`#` 前缀）具有一致的层级树与 `headingPath` 行为（仅限紧邻单行标题文本）。
  - **分隔线噪声切片剔除**：分隔线（`---` / `***` / `___`）本身不再生成只含符号的无意义噪声切片；由于分隔线判定置于 Setext 判定之后，确保「文本 + `---`」仍被正确识别为二级标题。
  - **FIXED_SIZE 策略生效**：`ChunkStrategy.FIXED_SIZE` 真正生效，忽略章节边界按 `chunkSize` 连续切分，且代码块与表格保持原子性不被切断。
- **经 Spec 评审发现并修复的两个问题**：
  - **跨章节切片元数据归属**：修正了 FIXED_SIZE 策略下跨章节切片一律套用首个章节元数据的缺陷，改为按切片起始偏移（跳过前置空白）反查并归属真实所属章节，而不是一律套用第一个章节。
  - **连续分隔线误判**：修正了连续多行分隔线时后一行被误判为前一行 Setext 下划线的缺陷，确保连续分隔线全部被安全过滤。
- **评审流程与结论**：
  - **Backend Spec Review**：返回 `CHANGES_REQUIRED`（2 条，即上述跨章节切片元数据归属与连续分隔线误判问题），完成定向修复。
  - **Quality Review**：修复后返回 `APPROVED`（提示 1 条 LOW 级别问题：类注释描述陈旧，已在后续提交中更新类注释修复）。
- **重排链路真实验证（SiliconFlow 已充值）**：
  - `rerankEnabled=true` 时 `fallback=false`，返回真实 `rerankScore`，并且排序相对向量序发生了变化：向量分 0.573 的切片被重排到第 1 位（rerank 0.9012），向量分 0.682 的切片降到第 2 位（rerank 0.8242），而与查询无关的切片虽然向量分较高（0.643 / 0.575）却得到接近 0 的 rerank 分（0.0125 / 0.0096）并沉到末尾。
  - `rerankEnabled=false` 时按向量分排序、`rerankScore` 为 `null`。
  - 此前文档中「因账户余额不足一直走降级路径」的状态已不再成立，正常重排与降级容灾双路径均已完成实实验证。
- **回归测试**：
  - `MarkdownChunkerTest` 测试用例数由 18 个增至 20 个。

### 线上复验

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
