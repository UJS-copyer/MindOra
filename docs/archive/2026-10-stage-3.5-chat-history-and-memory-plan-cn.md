# Stage 3.5 需求实现方案：T6 会话历史与多轮记忆

> **一句话用途**：给出 Stage 3.5（T6 会话历史 + 多轮记忆）的实现方案与决策依据，作为实施阶段的契约基准与任务拆解输入；验收证据落在 `stage-3.5-task-manifest-cn.md`（实施启动时新建）。
>
> 最近维护：2026-10-16　状态：**生效**（方案待 controller 与本人确认后进入实施）
>
> **2026-10-17 更新**：§5「第一版不做查询改写」已被后续裁决取代——**3.7 将做条件化改写**（有历史才触发、失败兜底原 query、三问对照验证），§5 保留为当时的决策记录。

## 1. 结论先行

1. **记忆策略：滑动窗口 + Token 截断双约束（最近 N 轮、每轮成对保留、`max_history_tokens=3000` 硬上限）**，与 design-04 既定参数一致；摘要压缩不做（Stage 3.5 非目标，见 §7）。
2. **查询改写：第一版不做 LLM 改写**。理由见 §5——成本/复杂度收益比不划算，且与严格 grounding 的引用核验链路有冲突风险；用「轻量规则 + 明确引导用户」兜底，评测数据（§6 多轮黄金集）出来后再评估。
3. **存储：MySQL 持久化（唯一真相），无 Redis 会话缓存**。本项目单实例部署、每轮问答本来就要读写 MySQL，且 design-04 明确要求对话可审计；等出现多实例或高频会话再做缓存层（§3.4）。
4. **conversationId 全链路打通**：`RagAnswerRequest.conversationId` 已预留（[RagDomain.java:76](backend/mindora-rag/src/main/java/com/mindora/rag/domain/RagDomain.java#L76)），本阶段启用。
5. **前端登录态是本阶段新增面**：site 目前完全匿名（无 auth 调用），需要新增轻量登录（复用后端 `/api/v1/auth/login`，JWT 已就绪）。

## 2. 现状盘点（实施前的代码事实）

| 事实 | 位置 | 对方案的影响 |
| --- | --- | --- |
| `RagAnswerRequest.conversationId` 已预留、被忽略 | [RagDomain.java:66-77](backend/mindora-rag/src/main/java/com/mindora/rag/domain/RagDomain.java#L66-L77) | 契约位已留好，启用即可，无破坏性变更 |
| `CallerIdentityPort.currentUserId()` 已存在（JWT sub），匿名返回 empty | [RagAdapters.java:142-145](backend/mindora-rag/src/main/java/com/mindora/rag/application/port/RagAdapters.java#L142-L145)、[RagModuleConfiguration.java:131-145](backend/mindora-app/src/main/java/com/mindora/app/config/RagModuleConfiguration.java#L131-L145) | 登录判定不用新做；限流已按 `u:{userId}` / `ip:{addr}` 分桶（[PublicRagController.java:247-258](backend/mindora-rag/src/main/java/com/mindora/rag/api/web/PublicRagController.java#L247-L258)） |
| 公开端点在 `permitAll` 下，`BearerTokenAuthenticationFilter` 全局生效 | [SecurityConfiguration.java:27-41](backend/mindora-app/src/main/java/com/mindora/app/config/SecurityConfiguration.java#L27-L41) | 「匿名可访问 + 登录增强」天然支持：带合法 Bearer 即为登录态，无需改过滤链 |
| `/api/v1/auth/login|register` 已有，返回 `{userId, email, accessToken}` | [AuthController.java](backend/mindora-user/src/main/java/com/mindora/user/api/web/AuthController.java) | 前端登录直接复用，user_account 表已存在（V2 迁移） |
| 模型调用是 `List<ChatMessage>(system, user)` 两条 | [RagAnswerService.java:145-149](backend/mindora-rag/src/main/java/com/mindora/rag/application/RagAnswerService.java#L145-L149) | 多轮 = 在这两条之间插入历史消息，`ChatClientPort` 签名不变 |
| 严格引用核验基于**本轮**候选切片逐字校验 | [RagAnswerService.java:159-172](backend/mindora-rag/src/main/java/com/mindora/rag/application/RagAnswerService.java#L159-L172) | 历史轮答案里的 `[n]` 不能进本轮核验域 → 历史进 prompt 的方式必须隔离（§4.2） |
| 测试 profile 关闭 chat、memory 持久化、连不上 Redis | [application.yml (test)](backend/mindora-app/src/test/resources/application.yml) | 会话仓储要有 memory 实现供测试；新表迁移不影响测试 profile |
| site 无任何 auth/请求封装，聊天状态集中在 `useRagChat`（头部注释已预留 T6 扩展位） | [useRagChat.ts:1-11](frontend/apps/site/src/composables/useRagChat.ts#L1-L11) | 前端改动集中且可控 |
| jtokkit 已在 classpath（Spring AI 传递依赖） | `com.knuddels:jtokkit` | Token 估算可用真实 tokenizer（cl100k_base），比字符估算准确 |

## 3. 会话持久化设计（后端）

### 3.1 数据模型（V8 迁移，只追加）

```sql
CREATE TABLE chat_conversation (
    id CHAR(32) NOT NULL,
    user_id CHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,            -- 首问截断 60 字（展示用，可后改）
    message_count INT NOT NULL DEFAULT 0,   -- 冗余计数，列表页免 join
    last_message_at TIMESTAMP(6) NULL,      -- 排序键
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 软删，与 user_account/blog_article 约定一致
    PRIMARY KEY (id),
    KEY idx_chat_conversation_user (user_id, last_message_at),
    CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
    id CHAR(32) NOT NULL,
    conversation_id CHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL,              -- user / assistant
    content MEDIUMTEXT NOT NULL,
    citations_json MEDIUMTEXT NULL,         -- assistant 消息的引用（压缩后的 JSON，见 §3.3）
    status VARCHAR(32) NOT NULL,            -- done / refused / degraded（只落终态）
    meta_json VARCHAR(1024) NULL,           -- chatModel/latencyMs/degradedReason 等诊断字段
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
```

### 3.2 归属模块与包边界

- 表归属 **mindora-rag**（新 `infrastructure/persistence/jdbc/JdbcChatConversationRepository`），不进 user 模块——会话是 RAG 的业务数据，user 只负责身份。RAG 已依赖 knowledge 的检索服务，但**不新增对 user 模块的依赖**：用户归属校验只用 `CallerIdentityPort` 的 userId 做行级比对。
- 端口定义在 `RagAdapters`（与既有端口风格一致）：`ChatConversationPort`（create/list/get/append/delete，全部带 userId 条件）。memory 实现（`InMemoryChatConversations`）供单测与测试 profile。

### 3.3 写入语义（关键决策）

- **只落终态**：`done / refused / degraded` 三种 assistant 消息 + 对应 user 消息落库；流式中断（cancelled/error）**不落 assistant 消息**，但 user 消息已落（与前端「错误态不入 localStorage 历史」对齐，避免恢复出半截对话）。
- **写入时机**：SSE `done`/`error` 收尾时在 streamExecutor 线程同步写入（量小：2 行 INSERT + 1 UPDATE，无外部调用）。不引入异步化——单实例下复杂度不值。
- **citations 压缩存储**：只存 `{index, chunkId, documentTitle, articleSlug, sequence, quote}` 六字段（`headingPath/sourcePath` 可由 chunkId 回查，`quoteVerified` 只对当轮有意义）。回显时反序列化为 `CitationView` 同构 JSON。
- **首问即标题**：`createConversation` 以首问截断生成 title；后续不改（避免隐性交互）。

### 3.4 不做 Redis 会话缓存的理由（决策记录）

业界生产推荐「Redis 主存 + MySQL 归档」（来源：[nageoffer 会话记忆设计](https://nageoffer.com/ai/basics/rag/advanced-retrieval-and-conversation/conversation-memory/)），其前提是高并发写 + 多实例共享。本项目现状：**单实例**（Lucene 单写者硬约束，见 project-status 运行约束）、每轮问答写 2 行、读取只发生在「打开会话」与「组装记忆」（后者本阶段直接查 MySQL，量 = 最近 8 条消息的索引扫描）。收益不抵新增一套缓存失效语义。触发重估条件：① 出现第二个后端实例；② 会话消息 QPS 让 MySQL 慢查询日志出现该表。

## 4. 多轮记忆设计（生成层）

### 4.1 组装算法（滑动窗口 + Token 双约束）

```
输入：conversation 最近消息（新→旧遍历）
约束：chat_context_turns=4（轮，可配 1..8）且 max_history_tokens=3000
规则：
  1. 按「user+assistant 成对」取轮；任一条超 1500 token 的轮整轮丢弃（异常长文轮不进 prompt）
  2. 从最近一轮向前累加，token 超预算即停（成对丢弃，来源：同上 nageoffer 文档「截断要成对」）
  3. 输出按时间正序插入 messages
```

Token 计数用 jtokkit `cl100k_base` 估算（对 Qwen 系是近似值，用作**预算上限**而非计费，误差可接受；在代码注释标注口径）。

### 4.2 注入方式（与严格引用核验兼容——本方案的关键点）

历史消息**以原生 `user`/`assistant` 消息插入**（system 之后、本轮 user 之前），且 assistant 历史消息**剥离 `[n]` 编号引用**（替换为空或自然语言）：

```
[system]  现有 RagPrompt.systemPrompt()（不改）
[user]    第 n-2 轮用户问题（原样）
[assistant] 第 n-2 轮答案（纯文本，无 [n]）
[user]    第 n-1 轮用户问题
[assistant] 第 n-1 轮答案（纯文本）
[user]    【资料片段 + 本轮问题】（现有 RagPrompt.userMessage 不改）
```

理由：
- 引用核验（`AnswerCitationValidator`）只解析**本轮 user 消息**产出的 JSON 回复，历史消息不进入核验域，`[n]` 语义不会跨轮污染；
- 历史答案保留纯文本即可支撑指代理解，引用卡片本就不跨轮复用；
- system prompt 与输出契约零改动 → Stage 3.3 的 15/15 管理端回归与 323 条单轮评测基线不受影响（实施后跑一轮回归证明）。

### 4.3 会话归属与边界

- 带 `conversationId` 的请求：`chat_conversation.user_id` 必须等于 `currentUserId()`，否则 `chat_conversation_not_found`（不区分「不存在/不是你的」，防枚举）。**匿名带 conversationId 一律按无会话处理**（现状契约，design-11 §10），不报错。
- 拒答/降级轮是否计入记忆：**计入 user 问句，拒答轮的 assistant 消息以拒答文案落库并进历史**——「刚才没答上来」本身是对话状态。降级轮同理（内容是服务端摘录列表，纯文本安全）。
- SecretGuard 对历史消息不再扫描：落库前已过答案层扫描，且历史是已核实内容；避免双重扫描的误判放大。

## 5. 查询改写：第一版不做（决策记录）

**候选方案与不选理由**（依据 [nageoffer 查询改写](https://nageoffer.com/ai/basics/rag/advanced-retrieval-and-conversation/query-rewriting/)、[Multi-Query 代价分析（cnblogs 全景文）](https://www.cnblogs.com/yupi/p/19914426)、以及本仓库 survey 已有结论）：

| 候选 | 代价 | 结论 |
| --- | --- | --- |
| A. LLM 改写（condense question） | 每轮 +1 次模型调用（200~500ms + token 费）；SSE 首字延迟被拉长；改写跑偏会把无关文档带进候选，而候选变化会**改变引用核验域**——3.3 的 quote 校验是对着本轮候选做的，改写引入的召回漂移会让「改写前能核验的答案」变得不可核验 | **3.5 不做**。它属于检索质量增强（survey 的「暂不引入」清单本就包含 Multi-Query/改写），不是会话历史的必需件 |
| B. 规则改写（代词表替换） | 覆盖率低、误替换风险，规则维护是无底洞 | 不做 |
| C. 不改写，靠「历史进 prompt + 前端引导」 | 追问含指代时检索召回可能偏差；但**答案生成层能看到历史**，模型可在 JSON answer 里基于上下文作答——只是候选可能不全，最坏走 refused（安全侧失败） | **3.5 采用**。失败模式是「拒答」而不是「错误答案」，与严格 grounding 的取舍一致 |
| D. 混合检索已有 RRF 兜底 | 现成 | 无需动作 |

**配套缓解**（写进 prompt 与前端）：
1. system prompt 追加一条：「若用户问题依赖上文指代，结合对话历史理解其真实意图，并**优先在既有片段中寻找**对应内容」（零成本，利用历史回答理解问题，不改变检索）；
2. 前端预设问题与空态文案加一句引导：「追问时尽量带上主题词，例如『那 DDIM 呢』→『DDIM 和 DDPM 的区别』」。
3. 评测（§6）量化「指代追问召回不足导致 refused」的占比，作为后续是否上 A 方案的决策依据——写进任务清单验收。

## 6. 评测与验收

### 6.1 多轮黄金集（新增，与 323 条单轮基线分开）
- 规模 ≥ 30 组，每组 2~3 轮，覆盖：指代追问（「它/这个/上面说的」）、省略补全（「价格呢？」）、话题延续、无依赖新问题（对照组）。
- 标注口径沿用 `scripts/rag-eval`（DocHit/SpanHit/MRR），记录：第 2/3 轮相对第 1 轮的召回衰减、refused 占比。**阈值在实测后定**（两轮测得不同值写区间，不预设拍脑袋数字——遵守 AGENTS.md 数字来源规则）。

### 6.2 单元/集成测试清单
- 记忆组装：轮数上限、token 截断成对性、超长轮丢弃、时间正序还原、`[n]` 剥离；
- 会话仓储：memory 与 jdbc 双实现同契约（CRUD、user 归属、软删、message_count/last_message_at 维护）；
- 端点：匿名带 conversationId（忽略不报错）、他人 conversationId（not_found）、conversationId 指向已删会话（not_found）、登录/匿名限流分桶回归；
- 流式：done/refused/degraded 三种终态的落库断言；中断不落 assistant 消息。

### 6.3 实机验收
登录 → 首问（正常作答）→ 指代追问（验证模型利用历史，接受「拒答」为合法结果并记录）→ 刷新页面 → 从服务端恢复会话列表与消息 → 匿名行为与现在一致（localStorage，不落库）。

## 7. 明确不做（Stage 3.5 非目标）

- 摘要压缩（summary + recent N turns）：轮数+token 双约束已把历史控制 ≤3000 token，摘要的额外模型调用与质量风险无必要；触发条件（黄金集显示 4 轮窗口不够用）出现再评估。
- LLM 查询改写 / Multi-Query / HyDE：见 §5，属检索增强主线，等评测数据。
- Admin 端私有内容问答：design-04 权限模型的另一分支，独立阶段。
- `rag_chat_log` 全量请求落库 / 额度报表 / 降级告警：Stage 4 运维增强（`chat_message` 已覆盖对话内容审计，运营分析字段 Stage 4 再加）。
- 会话标题自动生成 / 重命名：title=首问截断，够用。

## 8. 任务拆解建议（实施时的依赖序）

1. V8 迁移 + `ChatConversationPort` + memory 实现（纯后端，可先行）
2. 记忆组装器（`ConversationMemoryAssembler`，纯函数可单测）+ `RagAnswerService` 注入历史
3. `PublicRagController` 接线：conversationId 解析、终态落库、会话 CRUD 端点
4. site 登录（复用 `/api/v1/auth`）+ `useRagChat` 扩展（conversationId 贯传、会话列表/切换/删除 UI）
5. 多轮黄金集标注 + 评测跑分 + 单轮回归（323 条基线不回退）
6. 文档：design-04 增补实现章节、design-11 §10 增补 conversationId 语义、任务清单落验收证据

依赖关系：1 → 2 → 3 →（4 ∥ 5）→ 6。前后端分属 `feature/stage-3.5-backend` / `feature/stage-3.5-frontend`，集成项走 `feature/stage-3.5-integration`（沿用分支策略）。

## 9. 参考来源

- [多轮对话为什么会失忆？记忆设计（nageoffer）](https://nageoffer.com/ai/basics/rag/advanced-retrieval-and-conversation/conversation-memory/)——滑动窗口/token 截断成对丢弃/token 估算公式/存储选型对比
- [用户问得模糊？查询重写来兜底（nageoffer）](https://nageoffer.com/ai/basics/rag/advanced-retrieval-and-conversation/query-rewriting/)——改写策略分类、过度改写风险、跳过改写的时机判断
- [RAG 是什么？16 种 RAG 方案一次讲清（cnblogs）](https://www.cnblogs.com/yupi/p/19914426)——Multi-Query 的延迟/成本/方向跑偏代价
- [LangChain 记忆与会话管理（juejin）](https://juejin.cn/post/7627035938670886947)——BufferMemory 语义参照
- 本仓库：`docs/design-04-rag-and-chat.md`（参数与权限模型 SSOT）、`docs/design-11-rag-answer-generation-layer.md`（生成层契约）、`docs/rag-retrieval-improvement-survey-cn.md`（检索增强暂缓清单）
