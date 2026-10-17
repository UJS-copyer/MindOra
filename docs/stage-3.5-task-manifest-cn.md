# Stage 3.5 任务清单：T6 会话历史与多轮记忆

> **一句话用途**：Stage 3.5 的执行计划与验收台账（合一）。方案与取舍见 `docs/stage-3.5-implementation-plan-cn.md`（SSOT，本文不重复）。
>
> 最近维护：2026-10-17　状态：**代码交付，浏览器 UI 验收待补**

## 1. 交付范围

登录用户的多轮问答持久化 + 滑动窗口记忆（4 轮 / 3000 token）；匿名行为与 Stage 3.4 完全一致。

## 2. 任务状态

| # | 任务 | 状态 | 提交 |
| --- | --- | --- | --- |
| 1 | V8 迁移（chat_conversation + chat_message）+ ChatConversationRepository 端口 + memory/jdbc 双实现 | ✅ | `588654e` |
| 2 | ConversationMemoryAssembler（滑动窗口+token 双约束、成对取舍、[n] 剥离）+ ChatConversationService + RagAnswerService 历史注入 | ✅ | `588654e` |
| 3 | PublicRagController 接线（conversationId 贯传、终态落库、会话 CRUD 端点）+ RagAnswerViews/DoneEvent 契约字段 | ✅ | `2164447` |
| 3b | 缺陷修复：yaml 重复 chat 键（`4330196`）、会话仓储 userId 归一 32 位（`5d5b46b`，实机发现 JWT sub 为 36 位） | ✅ | 见左 |
| 4 | site 登录（api/auth + LoginDialog）+ rag.ts/useRagChat conversationId 贯传 + AiChatView 会话 UI | ✅ | `b45400d` |
| 5 | OpenAPI 契约同步（conversations 端点 + conversationId 字段进契约与生成类型） | ✅ | `b4e97b4` |
| 6 | **浏览器 UI 全流程验收**（登录→多轮→刷新恢复→删除） | ⬜ **待补**（用户执行或下次会话） | — |
| 7 | 多轮黄金集评测（≥30 组，指代追问/省略补全/话题延续/对照）与阈值实测 | ⬜ 待做 | — |
| 8 | design-04 增补「多轮实现」小节、design-11 增补 conversationId 语义 | ✅（§5 已登记） | `e2a9ba1`（见 §5） |

## 3. 后端验收证据（API 层实机，2026-10-17）

- Flyway V8 自动执行成功（`flyway_schema_history` version=8 success=1）。
- 真实链路（注册→登录→`POST /answer` 带 Bearer）：首问创建会话并回传 `conversationId`；追问带同 id，`message_count=4`（user+assistant ×2）。
- 记忆注入：`RagAnswerService` 消息序列为 system → 历史（成对、[n] 已剥离）→ 本轮 user（单测 `ConversationMemoryAssemblerTest` 8 例 + `RagAnswerServiceTest` 既有 16 例全绿）。
- 归属隔离：匿名带他人 conversationId → 按无会话处理（不报错、不落库、数据未污染）；他人会话 404 语义（`chat_conversation_not_found`）。
- 软删：DELETE 后列表为空、history 解析为空。
- 测试：全量 11 模块 `BUILD SUCCESS`（**252 tests**：common 2 / user 3 / blog 12 / knowledge 75 / asset 4 / rag 100 / app 45）；site 38 单测 + typecheck + build 全绿。

## 4. 实机发现并修复的缺陷

| 缺陷 | 现象 | 修复 |
| --- | --- | --- |
| yaml 重复键 | 启动即 `DuplicateKeyException: found duplicate key chat` | memory/history 并入既有 `mindora.rag.chat` 块（`4330196`） |
| userId 长度不匹配 | 落库 WARN `Data too long for column 'user_id'`，conversationId 恒 null | JWT sub 是 36 位带连字符，`user_account` 是 CHAR(32)；仓储入口统一 `replace("-")` 归一（`5d5b46b`） |
| 环境故障（非代码） | VM Docker 网桥再次丢 IPv4（本阶段第 4 次复现），MySQL 连接超时 | `ip addr add 172.19.0.1/16 dev br-ff83ebfa1109` 修复（首选修法再次验证有效）；根治看门狗仍欠 |

## 5. 契约变更登记

- **OpenAPI**（`docs/openapi/openapi.json`，`b4e97b4`）：`PublicRagRequestView` 增 `conversationId`；`RagAnswerView`/done 事件增 `conversationId`（登录新会话首次下发）；新增 `GET /api/v1/public/rag/conversations`、`GET .../conversations/{id}/messages`、`DELETE .../conversations/{id}`。
- **design-11 §10**：公开请求体与响应字段增补 conversationId 语义（匿名恒 null、非法值按无会话处理）。
- **design-04「Multi-Turn Memory」**：实现注记——滑动窗口+token 双约束、历史消息剥离 [n] 注入、会话表结构指向 V8。
- **配置项**：`mindora.rag.chat.memory.context-turns`（1..8，默认 4）、`mindora.rag.chat.memory.max-history-tokens`（默认 3000）、`mindora.rag.chat.history.backend`（jdbc|memory）。

## 6. 已知限制与遗留

1. **浏览器 UI 验收待补**（任务 6）：API 层已全部验证；UI 流程（登录弹窗、会话下拉恢复、删除）需前后端起服务后人工或下次会话执行。步骤：起后端 + `npm run dev:site` → `/chat` 登录（任意新邮箱/密码≥6 位）→ 提问 → 追问 → 刷新页面 → 会话下拉选回 → 删除。
2. **多轮黄金集评测未做**（任务 7）：指代追问的召回衰减与 refused 占比未量化——这是后续是否上查询改写的决策依据（方案 §5）。
3. 并发写同会话：后写覆盖（单人项目取舍，代码注释声明）。
4. 拒答/降级轮进历史：可能诱导模型保守，system prompt 已加「历史轮未获答案，本轮仍按既有规则独立作答」缓解。
5. 单轮基线回归：全量测试含 3.3 全部用例无回退；323 条黄金集重跑属任务 7 一部分。

## 7. 分支与合并状态

- `feature/stage-3.5-backend`：`588654e` → `2164447` → `4330196` → `5d5b46b`
- `feature/stage-3.5-frontend`：`b45400d` →（合并 backend）`a53a40c`/`4530f92` → `b4e97b4`（契约同步）
- 浏览器验收补完并确认后，两分支压缩合并回 main（沿用仓库策略）。
