# Stage 3.4 任务清单：公开问答闭环

> **用途**：Stage 3.4 的任务拆分、执行顺序、验收口径与进度台账。
> 本文档**同时承担「计划」与「交付台账」两个角色**，阶段内不再另建计划文档（避免同一阶段两份文档重复叙述）；阶段完成后把交付证据补进本文档即可，无需归档。
> 维护日期：2026-10-11　状态：**后端闭环已交付**（T1 待本人处置凭据、T5/T6 顺延 Stage 3.5，见 §10 交付记录）

## 1. 目标与范围

对照 `docs/design-07-implementation-roadmap.md` 的 **Stage 3 出口条件**：

> 公开问答页能基于已发布内容作答、SSE 流式输出、引用回链原文、登录用户保存会话历史。

当前差距（逐项核对过）：

| 出口条件 | 现状 | 本轮任务 |
| --- | --- | --- |
| 基于公开内容作答 | 生成层（`mindora-rag`）已交付，但**只有管理端端点** `POST /api/v1/admin/rag/answer` | T2 |
| SSE 流式输出 | 未做（`ChatOptions.streamConsumer` 槽位已预留） | T4 |
| 引用回链原文 | 契约已有（32 位 ID + `sequence` + `articleSlug`），但无前端页面 | T5 |
| 登录用户会话历史 | 未做（请求契约已含 `conversationId`，当前忽略） | T6 |
| 公开前的安全前置 | 语料存在疑似明文凭据，**未处置** | T1（硬门禁） |

**范围外**：查询改写、Contextual Retrieval、父子块、RAPTOR、答案相关性独立校验（NLI）、多实例部署——理由见第 6 节。

## 2. 依据与现状（不要重新调研）

- 生成层契约与配置：`docs/design-11-rag-answer-generation-layer.md`
- 公开问答的**权限模型**与多轮/流式需求来源：`docs/design-04-rag-and-chat.md`（其中公权模型、多轮记忆、流式输出**仍以该文为准**）
- 检索侧契约：`docs/design-09-*.md`、`docs/design-10-*.md`
- 安全结论：明文正文只落在 MySQL（`knowledge_document_version.content_snapshot` / `knowledge_chunk.content`）；Qdrant payload 不含正文、Lucene 正文字段 `Store.NO`
- 实测基线：正例 15/15、负例 28/30（各 3 轮，30B 模型）、降级路径 `degraded=true`、延迟中位数 2312ms
- 已确认决策（2026-10-10）：**不引入入库门禁**，「检索时不可见」即被接受为控制粒度；可选增强为「回答可指出密钥/配置项的名称但不输出其值」

## 3. 任务拆分

### T1 语料凭据处置（**硬门禁：未完成不得开放公开问答**）

> 2026-10-11 更新：**扫描脚本已交付**（`scripts/security/scan-corpus-secrets.mjs`，规则与后端 SecretGuard 同源）；
> 全量基线与 public 暴露面已实测（见 §10）。**轮换密钥 / 源文件脱敏 / 全量重建 / 复扫仍是本人操作。**

- **交付物**：扫描脚本（可重复执行）✅、脱敏后的语料、全量重建后的三处索引、复扫报告
- **执行顺序（不可颠倒）**：核实 → **轮换密钥**（本人操作）→ 源文件脱敏 → 全量重建索引 → 复扫验收
- **为什么先轮换**：只要密钥仍有效，改文本毫无意义；且已入库的明文不会因源文件修改而消失
- **验收口径**：① 涉及的密钥在对应平台已轮换；② 复核脚本对**重建后的切片**扫描为 0 命中；③ MySQL 有效切片 = Qdrant `points_count` = Lucene 文档数
- **验证方式**：
  ```powershell
  node scripts/security/scan-corpus-secrets.mjs --baseline          # 全量基线（改前）
  node scripts/security/scan-corpus-secrets.mjs --visibility=public # 公开暴露面（应长期为 0）
  # 全量重建（25 篇一批，显式传 id）：POST /api/v1/admin/knowledge/documents/reindex
  node scripts/security/scan-corpus-secrets.mjs                     # 改后复扫（应为 0 命中，退出码 0 可作门禁）
  ```
- **依赖**：无（阻塞公开暴露）

### T2 公开问答端点与限流（✅ 已交付）

- **交付物**：`POST /api/v1/public/rag/answer`（同步）、`POST /api/v1/public/rag/answer/stream`（SSE，见 T4）、`PublicRagController`、限流
- **硬性约束**：`scope=public` **由服务端硬编码**，且请求体**根本没有** scope 字段（比「接受但忽略」更难被误用）；沿用既有过滤（`visibility=public` + `knowledgeEnabled=true` + `status=active`，且 MySQL 权威复核）
- **限流实现（与计划的偏差，已确认）**：**固定窗口**（Redis `INCR` + 首次 `EXPIRE`）而非令牌桶——个人项目不做突发整形，零新依赖；升级路径写在适配器注释里（Lua 或 bucket4j，端口不变）。登录用户按 userId（20/分）、匿名按来源 IP（6/分）分桶；Redis 不可用时 **fail-open + warn**（限流是成本护栏而非权限边界）
- **验收口径（按语料现状校准）**：① 匿名可调用 ✅；② 请求里传 `scope=all` 仍只召回公开内容 ✅（单测断言控制器入参 + 实机私有内容零召回）；③ 超限 429（`rag_rate_limited`）而非 500 ✅；④ ~~5 条正例的引用可定位~~ → **已按公开语料现状校准**：当前仅 1 篇 public 文档（《阅读线路》），故公开端点验「该文档可作答且引用只来自 public」+「10 条负例拒答」+「私有内容零召回」；正例可定位的完整回归在管理端点进行（15/15）
- **验证方式**：`mindora-app` 定向测试（`PublicRagControllerTest` 5 + `PublicRagRateLimitTest` 4 + `PublicRagStreamControllerTest` 6）+ 实机 `logs/public-rag-check.mjs`
- **依赖**：T1（**公开暴露**前置；开发与内部验证不受阻）

### T2b 密钥兜底 SecretGuard（✅ 已交付，本轮新增）

- **交付物**：`SecretGuard`（规则集）、`StreamingSecretFilter`（流式滑动窗口）、`SecretGuardSignal`，接入公开问答链路
- **分层规则**：上下文层（检索后、调模型前）只用**前缀型**高置信规则，命中即拒答、密钥不进 prompt；答案层用**全部规则**（含赋值型 + 占位符白名单），并覆盖**引用摘录**（降级路径回传的是服务端摘录，是独立泄露路径）
- **规则来源**：gitleaks 默认规则集的高置信子集（`sk-` 风格、AWS `AKIA/ASIA`、阿里云 `LTAI`、GitHub `ghp_`、Google `AIza`、Slack `xox`、`PRIVATE KEY` 块、赋值型），不做熵检测
- **流式不变式**：滑动窗口保留尾部 96 字符不转发（≥ 最长规则长度），密钥被 delta 切断也能命中；修改规则表必须同步调大窗口
- **开关**：`mindora.rag.security.secret-guard.enabled`（默认 true）；**管理端不启用**（管理员看自己的库，拒答反而妨碍排查）
- **验收口径**：规则正反例单测全绿；流式跨 delta 切断必命中；命中后密钥不出现在已转发文本中
- **依赖**：T2

### T3 契约同步（✅ 已交付，小，但阻塞前端）

- **交付物**：更新 `docs/openapi/openapi.json`、`frontend/packages/types/src/generated/openapi.d.ts`；`OpenApiContractTest` 增加新端点路径断言（原先只断言 6 条固定路径，因此 Stage 3.3 的 admin 问答端点都没进契约）
- **已断言路径**：`/api/v1/public/rag/answer`、`/api/v1/public/rag/answer/stream`、`/api/v1/admin/rag/answer`
- **验收口径**：`npm run api:check` 通过；生成的 TS 类型里含公开问答端点与响应结构（`PublicRagRequestView` / `RagAnswerView` / `CitationView`）✅
- **验证方式**：
  ```powershell
  npm run api:sync     # 跑 OpenApiContractTest 导出 spec
  npm run api:generate # 重生成类型
  npm run api:check
  ```
- **依赖**：无（但必须在 T5 之前）

### T4 SSE 流式输出（✅ 已交付）

- **交付物**：对话适配器流式实现（OpenAI 兼容 `stream:true`）、`StreamingAnswerExtractor`、SSE 端点、事件契约
- **事件契约（终版）**：`delta`（增量文本）→ `citation`（逐条引用）→ `done`（含 `refused`/`refusalReason`/`refusalDetail`/`degraded`/`degradedReason`/`chatModel`/`latencyMs`/`firstDeltaMs`）；意外失败为 `error`（含 `code`/`message`）。**`done` 或 `error` 恰一个为终态**
- **模型契约**：**未改**——仍是我方 JSON 信封；流式只增量抽出 `answer` 字段（否则会看到 JSON 语法），完整累积文本仍交给既有校验器解析 citations。这样 Stage 3.3 的 15/15 基线不受影响（实测复跑仍 15/15）
- **必须解决的矛盾**：引用**逐字核验需要完整文本**，而流式是「先吐字后收尾」。约定：流中先给答案，`done` 事件给**校验后**的引用；若校验后零条存活，`done` 标记 `refused=true`——**前端必须处理「已显示的文字随后被判定为拒答」**（接口收到 `refused=true` 时用标准拒答文案替换），这是设计取舍而非缺陷
- **预检与流内错误**：总开关/参数/限流三项预检失败返回**普通 JSON 4xx**（此时还没写 `text/event-stream` 头）；流开始后的失败只能以事件表达（成熟做法，见 Dify 流式文档）
- **中断机制**：消费端异常（客户端断开、密钥熔断）包装成 `StreamConsumerAbort` 冒泡出读取循环 → try-with-resources 关闭响应流即停止读取上游；上层必须**原样上抛**，不得当成上游故障
- **线程模型**：`RagStreamTaskExecutor`（core 2 / max 16 / 队列 0），容量满回 `server_busy` 事件而不是静默断流。注意它被包成独立类型：直接注册 `ThreadPoolTaskExecutor` Bean 会让 knowledge 装配里未加限定的 `Executor` 注入点出现「2 个候选」歧义
- **验收口径**：① 首字节延迟实测记录 ✅（`firstDeltaMs=9125ms`）；② 拒答/降级在流式下有明确事件 ✅；③ 客户端断开不残留线程（消费端异常路径有单测；实机未见残留）
- **依赖**：T2

### T5 前台问答页（独立前端分支）

- **交付物**：`frontend/apps/site` 问答页（路由、组件、流式消费、引用渲染与跳转、拒答/降级态、移动端适配）
- **起点**：先收敛现有前端原型资产——`feature/stage-4-frontend` 的 `AiChatView.vue`（317 行）、`HomeView.vue`（209 行）、`MindOra.personal-studio.html`（1229 行），以及 `stash@{0}`（`wip: site home, ai chat and personal studio prototype`）。三者内容重叠，**先 rebase 到 main 再挑一份为准**，不要在三处各改一份
- **验收口径**：① 真实后端端到端可用；② 引用可点击并定位到原文（`chunkId` + `sequence`）；③ 拒答与降级有明确 UI（不能显示空白或 500）；④ `npm run typecheck` 与 `build` 通过
- **分支**：`feature/stage-3.4-frontend`，独立 worktree；**不与后端改动同工作区暂存**
- **依赖**：T2、T3、T4

### T6 会话历史（登录用户）

- **交付物**：Flyway 迁移（会话表 + 消息表）、`conversationId` 落库与回填、仅登录用户生效
- **验收口径**：① 多轮上下文可复现；② 匿名请求不落库；③ 非法 `conversationId` 按「无会话」处理而不是报错
- **依赖**：T2

### T7 质量收尾

- **交付物**：323 条黄金集人工复核定稿（`docs/stage-3.3-candidate-questions-cn.md`，迁出 `docs/` 根目录后见 `docs/generated/`）、RAG 请求日志（查询 / 延迟 / 拒答原因 / 模型）、降级告警
- **验收口径**：① 黄金集定稿并作为后续回归基线；② 日志字段可查；③ 降级事件可观测
- **依赖**：无

## 4. 依赖顺序

```text
T1（硬门禁，本人轮换密钥）─┐
T3（契约同步）────────────┼─→ T5（前台问答页）─→ 出口验收
T2（公开端点+限流）─→ T4（SSE）┘
                    └─→ T6（会话历史）
T7（质量收尾）独立，可并行
```

- **可立即开工**：T3（最小）、T7；T2 需在 T1 完成前只做开发与测试，**不得对外开放**
- **开工顺序建议**：T1 ∥ T3 → T2 → T4 → T5 → T6
- 每个任务仍遵循「编码 → 定向测试通过 → 独立提交」

## 5. 出口验收（Stage 3.4 完成的定义）

1. 匿名访客可在前台问答页提问，答案**只**来自公开内容；
2. 流式输出可用，且拒答/降级有明确事件与 UI；
3. 每条引用可点击定位回原文（并可按 `articleSlug` 回链博客）；
4. 登录用户会话历史可用，匿名不落库；
5. 语料凭据扫描 0 命中，三处索引一致；
6. 回归：Stage 3.3 的用例在公开端点上仍满足正例引用可定位、负例拒答；
7. 全量后端测试 `BUILD SUCCESS`，前端 `typecheck` / `build` 通过，`api:check` 通过。

## 6. 不做（有依据的排除）

| 不做的事 | 依据 |
| --- | --- |
| 入库门禁（切片前排除敏感笔记） | 已决策：检索时不可见即够用（三层过滤 + MySQL 权威复核） |
| 相似度阈值熔断拒答 | 已实测证伪：正例最低 0.0008 vs 负例最高 0.8324 完全重叠 |
| 句级重切分 / Late Chunking / RAPTOR | 官方评测无显著收益或本项目 API 不支持，见调研文档 |
| Contextual Retrieval 上下文前缀 | 需重建向量与 BM25，留待检索侧仍不达标时再评估 |
| 答案相关性独立校验（NLI/verifier） | 先量化「引用真实但答非所问」的占比（Kafka 类边界），再决定是否引入 |
| 多实例部署 | Lucene 单写者约束未解除 |

## 7. 风险

| 风险 | 应对 |
| --- | --- |
| 流式与引用校验的顺序矛盾导致前端体验割裂 | 在 T4 前先把事件契约定下来并让前端确认（T5 依赖 T4 的契约，不是实现） |
| 限流参数误伤正常用户 | 先记录真实调用分布再定阈值；限流错误要有明确错误码与提示 |
| T1 的密钥轮换需本人操作，可能长时间阻塞上线 | 开发与测试可先行（T2 只做内部验证），但**公开开关必须等 T1 完成** |
| 前端原型散在三处（分支 / 工作区未跟踪文件 / stash） | T5 第一步先收敛到一条分支，禁止多头修改 |
| 公开端点被爬取消耗模型额度 | T2 的限流 + 配额；T7 的请求日志用于发现异常 |

## 8. 进度台账

| ID | 任务 | 状态 | 提交 | 备注 |
| --- | --- | --- | --- | --- |
| T1 | 语料凭据处置 | **部分** | `17356f1` | 扫描脚本 + 全量基线与 public 暴露面已交付；轮换/脱敏/重建/复扫待本人操作 |
| T2 | 公开端点与限流 | **DONE** | `b89e6bb`、`13adbd5` | 固定窗口（非令牌桶）；429 与 fail-open 已实机验证 |
| T2b | 密钥兜底 SecretGuard | **DONE** | `ee50bbb` | 上下文/答案双层 + 流式滑动窗口 |
| T3 | 契约同步 | **DONE** | `a25b5ba` | 三个端点进契约，`api:check` 通过 |
| T4 | SSE 流式 | **DONE** | `442e2c7`、`2722edc`、`7d4958f` | 事件契约 delta→citation→done；实机事件序验证通过 |
| T7 | 质量收尾（日志部分） | **部分** | `7d4958f` | `RagAccessLog` 结构化日志已上线；**DB 日志表与告警归 Stage 4**（design-07 的运维范围）；黄金集人工复审待本人 |
| T5 | 前台问答页 | **顺延 Stage 3.5** | — | 依赖 T2/T3/T4 已就绪；原型三处收敛待本人指定基准 |
| T6 | 会话历史 | **顺延 Stage 3.5** | — | 多轮记忆需独立设计与评测（design-04：`chat_context_turns=4`、`max_history_tokens=3000`） |

## 9. 约束

- 外部服务只能经过 adapter / port 边界；后台页面只写入 `frontend/apps/admin-art`。
- Flyway 迁移只追加，不修改历史迁移文件。
- 前后端改动分属不同分支；不在同一工作区同时暂存前后端改动。
- 不回滚、不丢弃来源不明的工作区改动；禁止批量删除。
- **未完成 T1 之前，不得把公开问答端点暴露到可被外部访问的环境。**

---

## 10. 交付记录（2026-10-11，后端闭环）

分支 `feature/stage-3.4-backend`（自 `main` 2f56211）。提交序列（每提交 = 代码 + 定向测试绿）：

| 提交 | 内容 |
| --- | --- |
| `b89e6bb` | 公开问答端点（scope 服务端硬编码 + 请求收窄 + 视图抽取共用） |
| `13adbd5` | 固定窗口限流（Redis/内存双后端 + 429 + 身份端口 `CallerIdentityPort`） |
| `ee50bbb` | 密钥兜底 SecretGuard（上下文/答案双层 + 流式滑动窗口） |
| `442e2c7` | 对话适配器流式调用（`stream:true` 帧解析、累积、消费端中断） |
| `2722edc` | 流式 answer 抽取器（JSON 状态机 + 转义解码 + 随机切分性质测试） |
| `7d4958f` | 公开 SSE 端点（事件契约 + 有界线程池 + 访问日志） |
| `a25b5ba` | OpenAPI 契约纳入三端点并重生成前端类型 |
| `17356f1` | 语料凭据扫描脚本（可重复执行，报告不含命中文本） |

### 10.1 接口与行为（新增）

| 端点 | 鉴权 | 说明 |
| --- | --- | --- |
| `POST /api/v1/public/rag/answer` | 匿名 | 请求体只有 `query`（2~500 字）；`scope` 服务端硬编码 public；固定窗口限流；启用密钥兜底 |
| `POST /api/v1/public/rag/answer/stream` | 匿名 | SSE：`delta`* → `citation`* → `done`（或 `error`）；预检失败返回普通 JSON 4xx；与 JSON 端点共用同一限流桶 |

错误码：`validation_error`(400)、`rag_rate_limited`(429)、`rag_public_disabled`(404，总开关关闭)。

配置（`mindora.rag.public.*` / `mindora.rag.security.secret-guard.enabled`，见 `design-11` §4）：
`enabled`、`rate-limit.backend`(redis|memory)、`window-seconds`、`anonymous-per-minute`(6)、`logged-in-per-minute`(20)、`stream.executor-core/max`。

### 10.2 实机联调记录

环境：VM `192.168.222.128` 的 MySQL/Redis/Qdrant（经 SSH 隧道 13306/16333/16379）+ SiliconFlow 对话模型；语料 158 篇 / 1 篇 public（《阅读线路》，30 切片）。

| 项 | 结果 |
| --- | --- |
| 公开端点正例（2 条，来自唯一 public 文档） | 2/2 作答，`citations=1`，**引用文档只来自 public**，4.0~6.0s |
| 公开端点负例（2 条） | 2/2 拒答（`model_refused`），零引用 |
| **权限隔离**（2 条只在私有文档的内容） | 2/2 拒答，**零引用**（未召回任何私有内容） |
| SSE 事件契约 | 14 个事件：`delta`×12 → `citation` → `done`；`firstDeltaMs=9125ms`、`latencyMs=11108ms`；`Content-Type: text/event-stream` |
| 限流（额度压到 2，连打 6 次） | `200,200,429,429,429,429`，错误码 `rag_rate_limited`；**SSE 端点预检同样返回 JSON 429**（不是事件流） |
| 降级（对话模型指向死端口） | JSON：HTTP 200 + `degraded=true` + `degradedReason=rag_chat_unavailable` + 11 条服务端摘录引用（**不是 500**）；SSE：`citation`×11 → `done{degraded:true, firstDeltaMs=-1}` |
| 访问日志 | `RAG_ACCESS - endpoint=public|public-stream scope=public status=degraded latencyMs=… citations=11 query=…` 实测落盘 |
| 管理端回归（REPEAT=3） | **正例 15/15（流式重构无回归）**；负例 27/30（唯一失败仍是 Kafka 边界用例，3/3 被作答且引用来自真实可核验语料） |

**语料凭据扫描**（`scripts/security/scan-corpus-secrets.mjs`）：

- 全量 158 篇：**6 处命中 / 5 篇文档**（`openai_style_key` 4、`alibaba_access_key` 1、`credential_assignment` 1），涉及《阿里云OSS文件存储》《agent笔记》《个人网站开发》《高速公路养护管理移动端应用》《DeepSeek Harness试用》——**全部为 private**。
- **public 暴露面：0 命中**（唯一公开文档不含凭据）。
- 与 Stage 3.3 那次宽松 grep（`sk-` 20 行等）数量差异来自规则收紧（这次要求 32+ 位密钥、带占位符白名单），不是数据变化。

### 10.3 本轮踩到的两个环境/工程坑（已解决，供下次参考）

1. **VM Docker 网桥再次丢失 IPv4**：`br-ff83ebfa1109` 只剩 IPv6 link-local，表现为「VM 上 3306 显示 OPEN 但 `docker-proxy` 到容器不通」→ 后端 `Communications link failure`。修复：`systemctl restart docker`（容器 `unless-stopped` 自动恢复，`172.19.0.1/16` 回归）。这是该故障第二次复现，处置过程与个人笔记库《经验01》一致。
2. **两个 Spring 装配坑**：① rag 模块缺 `spring-webmvc`（`SseEmitter` 所在），已按最小改动补依赖；② 直接注册 `ThreadPoolTaskExecutor` Bean 会让 knowledge 装配中未加限定的 `Executor` 注入点出现「2 个候选」，故包成 `RagStreamTaskExecutor` 独立类型。

### 10.4 已知限制（本轮未做）

- **密钥兜底的实机触发未验证**：唯一 public 文档不含凭据，因此答案层兜底只能由单测覆盖（12 个用例，含跨 delta 切断）；实机验证需要一篇「含凭据且公开」的文档，而那正是应当避免的状态。
- **无 SSE ping/重连**：个人项目从简，靠 emitter 超时（chat 超时 + 30s）兜底；若将来置于有 idle 超时的反代后需补 ping。
- **访问日志只落文件**：`rag_chat_log` 表、额度报表与降级告警归 Stage 4（design-07 运维范围）。
- **限流阈值（6/20 每分钟）是初始保守值**：需按 T7 日志观察到的真实分布再调；`getRemoteAddr()` 是直连地址，置于反代后需按可信代理解析 `X-Forwarded-For`。

