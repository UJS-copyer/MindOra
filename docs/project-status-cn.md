# MindOra 项目状态总览

本文只记录**当前有效**的项目状态：迁移结果、Git 集成状态、启动入口、各阶段开发状态、检查结果与下一阶段建议。

> **历史细节已按阶段迁出**：各阶段的「接口与行为」「数据边界」「现场联调记录」现在都在对应的 `stage-*-task-manifest-cn.md` 里（见下方「阶段索引」）。本文件不再累计各阶段明细，保持在一屏可读范围内。

最近维护：2026-10-17

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`，旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## Git 集成状态

- `main`：`6141c9f`（T5 压缩合并 `bd67327` → SSE 预检 403 修复 `9e27590` → eslint 忽略与注释订正 → 引用回链修复压缩合并 `7c2b736` → T5 遗留小项收敛 `7ccf752` → Stage 3.5 实现方案 `6141c9f`），领先 `origin/main` 23 个提交；**Stage 3.4 及之前的全部内容已推送备份（2026-10-17，用户指定推至 3.4 节点）**，3.5 方案文档与后续在本地待下阶段验收后一并推送。Stage 3.5 状态：后端会话引擎已交付并经 API 实机验证；前端登录/会话 UI 质量不达标已弃用（半回退，见上条），台账见 `docs/stage-3.5-task-manifest-cn.md`
- 各阶段基线/交付提交：
  - Stage 1：`d5b160d`（后端内容）、`62a5cab`（前端内容 + 集成）
  - Stage 2：`b341a77`
  - Stage 3：`34e7697`（主线）、`e4a60df`（检索底座交付）
  - Stage 3.2：`08ec7b0`（关键词与融合）、`3c271e8`（检索调试页）、`67a2b67`（契约与联调记录）
  - Stage 3.3：`cdda506`（压缩合并；来源分支 `feature/stage-3.3-backend` 的 16 个提交按约定保留，仅供追溯）
  - Stage 3.4（后端）：`d44fb62`（压缩合并；来源分支 `feature/stage-3.4-backend` 的 8 个提交按约定保留，仅供追溯）；`9e27590`（公开问答 SSE 预检 403 修复，见 §11.7）；`7c2b736`（引用回链修复压缩合并，来源分支 `feature/stage-3.4-backend-fix` 的 4 个提交已按约定压缩，分支本体已删除）
  - Stage 3.4（前端 T5）：`bd67327`（压缩合并；来源分支 `feature/stage-3.4-frontend` 的 10 个提交按约定保留，仅供追溯；真机与浏览器验收均已通过）
- 当前开发：
  - 后端 Stage 3.4 后端闭环已交付，T6 会话历史顺延 Stage 3.5，见 `docs/stage-3.4-task-manifest-cn.md`
  - 前端原型已收敛到 `feature/stage-3.4-frontend`（`bc5fc81`，已随 T5 一并进 main）：首页 `HomeView`、AI 问答 `AiChatView`、`App.vue` 路由接线、`SiteHeader` 导航、`docs/prototypes/` 下的个人工作台快照。**原 `feature/stage-4-frontend` 与 stash 中的同名文件已成为冗余副本**，内容与收敛结果逐字节一致
  - **T5 前台问答页已在 main**：真实 SSE 消费、引用章节级回链、拒答替换/降级/限流冷却/中断态；真机 A–F 与浏览器交互验收全通（§11.3、§11.8）。**三项遗留已收敛（2026-10-16，`待提交`）**：① 降级态真机复现通过（`RAG_CHAT_BASE_URL` 指向不可用端点实测：JSON/SSE 均 `degraded=true`、`degradedReason=rag_chat_unavailable`、正文为「生成模型暂不可用…」+ 18 条服务端摘录、非 500；恢复后正常作答）；② 引用回链已闭环（§11.8-F1 订正）；③ 客户端长度校验改中性 `invalid` 状态（灰色「提示」徽标，不再渲染红色「出错了」，§11.8-F2 关闭）。同批修复管理端文章编辑页「保存并发布草稿」按钮名不副实的问题（现保存后真正调用发布，与列表页一致）
  - **引用回链契约缺口已修复并合并 main（2026-10-14，`7c2b736`）**：`articleSlug` 从派生值改为 `knowledge_document.article_slug` 持久化字段（V7 迁移），Gitee 文档发布即写入 slug 并刷新向量 payload；顺带修复中文标题 slug 退化（回退 `knowledge-<documentId 前 8 位>`）。契约变更见 design-11 §2.1，证据与已知限制（unpublish 不回退可见性、文章无删除端点）见 §11.8-F1 订正。**实机浏览器验收已通过（2026-10-14）**：删两篇存量退化 slug 文章后经知识文档「生成草稿 → 发布」重新发布，新 slug `knowledge-beac3521`/`knowledge-e5962d6e`；`/chat` 真实提问（「DDPM 和 DDIM 有什么区别？」）命中《阅读线路》，引用卡显示「回到原文 →」，点击后回链 `/blog/knowledge-e5962d6e` 并正确渲染原文章节（`GET /api/v1/public/articles/knowledge-e5962d6e` 200）；控制台无应用错误（仅浏览器扩展噪声）。§11.8-F1 闭环
- **Git 分支治理（2026-10-17）**：AGENTS.md 分支策略修订为**主干单分支**（阶段验收打 `stage/<n>` tag 代替阶段分支；仅高风险/长期隔离工作开短命 `feature/<topic>`）。据此完成仓库清理：**删除 20 个过期分支**（Stage 0~3.4 全部阶段分支、codex/*、dev-backend/frontend、冗余的 3.5-frontend——已逐支核验其独有内容均被 main 严格取代）与 **4 个 .worktrees 工作树**（残留目录核验无人工内容后一并删除）。保留 3 个分支：`main`、`feature/stage-3.6-chat-rebuild`（当前开发）、`feature/stage-3.5-backend`（3.5 参照实现）。里程碑追溯改由 main 历史上的阶段交付提交与 tag 承担
- 当前开发分支 `feature/stage-3.6-chat-rebuild`（`68532a9`）：**Stage 3.5 半回退的落点**——后端会话引擎（记忆窗口/落库/CRUD/V8）完整保留，公开站 `frontend/apps/site` 回退到 3.4 已验收的匿名极简形态（3.5 前端登录/会话 UI 质量不达标，弃用；实机缺陷与决策记录见 `docs/stage-3.5-task-manifest-cn.md`）。后续路线见下方「下一阶段建议」（2026-10-17 rev 2 裁决：3.6 管理端问答 + 会话页 → 3.7 查询改写 → 4.1 公开域 → 4.2 回答契约 v2 + 提示词版本化 → 4.3 质量闭环 → 4.4 韧性与成本 → 4.5 运维可视化 → 4.6 双入口 → Stage 5 上线门禁；契约见 `docs/design-11-rag-answer-generation-layer.md` §15/§16，依据见 `docs/ragnote-insights-and-architecture-takeaways.md` §五）

## 启动入口

启动方式、端口、环境变量与本地联调注意事项统一见 **`docs/development-startup-cn.md`**（含 VM 基础设施、SSH 隧道、后端/前端启动命令）。

| 入口 | 地址 |
| --- | --- |
| 前台站点 | `http://localhost:5173/` |
| 前端后台（admin-art） | `http://localhost:5175/` |
| 后端接口 | `http://localhost:8080/` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |

本地运行日志统一写入仓库根目录 `logs/`。**后端、前台、后台三个常驻服务不要拼到一个 PowerShell 复合命令中启动。**

## 开发阶段状态

### 后端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工程、模块边界、认证、统一返回、基础测试 |
| Stage 1 | 已完成 | 内容与后台管理能力：文章、分类标签、资产、登录与管理页 |
| Stage 2 | 已完成 | Gitee Markdown 同步、文档版本、切片、Embedding/Qdrant 适配、MQ 异步任务、索引状态、失败重试、知识文档生成草稿与文章反向入库 |
| Stage 3 | 已完成 | 切片元数据化、向量 payload 富化与点生命周期修复、向量检索读链路、管理端检索调试接口 |
| Stage 3.2 | 已完成 | 关键词索引（Lucene）+ Hybrid/RRF 融合 + 权限修复（MySQL 为权限权威来源） |
| Stage 3.3 | 已完成 | 入库质量修复、323 条黄金集评测基线、RAG 生成层（单次问答 + 引用定位 + 三段式拒答 + 生成侧降级）；已压缩合并进 main（`cdda506`） |
| Stage 3.4 | **后端已合并**（T1 待本人操作） | 公开问答后端闭环：公开端点（scope 服务端硬编码）、固定窗口限流、密钥兜底 SecretGuard、SSE 流式（事件契约 delta→citation→done）、契约同步与访问日志。**T1 凭据轮换待本人操作；T6 会话历史顺延 Stage 3.5（T5 已在 `feature/stage-3.4-frontend` 交付）**。见 `docs/stage-3.4-task-manifest-cn.md` §10、§11 |
| Stage 4 | 未开始 | 运维与可观测增强（含 RAG 请求落库、额度报表与降级告警） |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 工作台壳、路由、请求层、权限基础、主题与布局 |
| Stage 1 | 已完成 | 迁移到 Art Design Pro 方案并完成主要后台页面 |
| Stage 2 | 已完成 | `admin-art` 知识库数据源、同步任务、文档、索引状态、失败重试、切片与模型配置页；mock 默认关闭 |
| Stage 3 / 3.2 | 已完成 | `/knowledge/retrieval` 检索调试页（含「命中来源」「两路排名 / RRF」列与召回→融合→返回指标栏） |
| Stage 3.3 | 已交付 | 后端入库质量修复 + 323 条黄金集评测基线 + RAG 生成层（单次问答 + 引用定位 + 三段式拒答 + 生成侧降级）；已压缩合并进 main（`cdda506`）。**前端无改动** |
| Stage 3.4（T5） | **已实现**（真机 + 浏览器验收通过） | 前台问答页 `/chat`：真实 SSE 流式（`delta → citation → done`）、引用回链定位到原文章节、拒答文案替换、降级/限流/中断态；38 个单测 + `typecheck`/`lint`/`build` 全绿，真机 A–F 用例与真实 Chromium 交互验收全通。见 `docs/stage-3.4-task-manifest-cn.md` §11 |
| Stage 4 | 未开始 | 监控、告警、运维视图 |

## 当前检查结果

- 后端全量测试：11 个模块 `BUILD SUCCESS`（Stage 3.4 交付时实测）
- `mindora-rag` 定向测试 **85** 个通过（适配器 11 / 组装器 10 / 校验器 17 / 编排 20 / SecretGuard 6 / 流式密钥窗口 6 / answer 抽取器 12 / 限流适配器 7）；`mindora-app` **42** 个（含公开端点 5 + 限流 4 + SSE 6 + 契约 1，以及 §11.7 缺陷的 3 个回归用例）
- 实机联调（真实模型 + 2028 切片语料）：
  - 管理端回归（REPEAT=3）：正例 **15/15**（流式重构无回归）、负例 27/30（唯一失败为 Kafka 边界用例）
  - 公开端点：公开文档 2/2 作答且**引用只来自 public**；负例 2/2 拒答；**私有内容 2/2 零召回**
  - SSE：14 个事件 `delta×12 → citation → done`，`firstDeltaMs=9125ms`
  - 限流：`200,200,429,429,429,429`（额度压到 2 时），SSE 预检返回 JSON 429
  - 降级：对话模型不可用时 JSON 与 SSE 均 `degraded=true`，**非 500**
  - 语料凭据扫描：全量 158 篇 6 处高置信命中（均在私有文档）；**public 暴露面 0 命中**
- `npm run api:sync` / `api:generate` / `api:check` 通过（公开问答三端点已进 OpenAPI 与生成的 TS 类型）
- **前台问答页（T5，`feature/stage-3.4-frontend`）**：`npm test --workspace=@mindora/site` **38 passed**（传输层 20 / 状态机 10 / 锚点 8）；`npm run typecheck`（7 工作区）0 错误；`npm run lint` 0 错误（顺带修掉主线自 `cdda506` 起 `scripts/rag-eval/run.mjs` 缺 `fetch`/`Buffer` 全局的 2 个 lint 错误）；`npm run build --workspace=@mindora/site` 通过（134.5 kB / gzip 46.5 kB）
- **T5 真机验收（2026-10-13，真实后端 + 真实模型，用前端传输层与状态机直打端点）**：正例 `delta×18 → citation×1 → done`（`latencyMs=3720`、引用只来自 public 文档）；负例 `refused` 且已流出文字被标准拒答文案替换；私有内容零召回；限流（后端额度压到 2）实测 `200,200,429,429,429,429` 且状态机进入 `rate_limited` + 60s 冷却
- **T5 浏览器验收（2026-10-13，真实 Chromium，视口 1929×992）**：`/chat` 正例流式（正文逐字追加 → 输入框 `disabled`、按钮变「停止」→ 完成显示 `Qwen/Qwen3-30B-A3B-Instruct-2507 · 4670 ms · 首字 2605 ms`、1 张引用卡）；负例拒答（`已拒答` + 标准文案整体替换 + **无引用卡**）；1 字问题被本地拦截（网络面板 **0 请求**）；两次交换各 `OPTIONS 200` + `POST 200`，无 4xx/5xx；控制台仅浏览器扩展噪声；路由与硬刷新后的 `localStorage` 恢复均正常。**发现**：① 引用回链在现有公开语料上不可触发（唯一 public 文档是 Gitee 来源，`articleSlug` 恒为 `null`，需一篇 BLOG 来源的公开文档）；② 客户端长度校验复用 `error` 状态，渲染成红色「出错了」徽标偏重。详见 `docs/stage-3.4-task-manifest-cn.md` §11.8
- **真机验收发现的后端缺陷（已修复，`9e27590`）**：公开问答 SSE 端点若只收到 `Accept: text/event-stream`，其**预检错误**（400/429）曾退化成 **403 + 空响应体**（错误响应无法完成内容协商 → ERROR 分派到 `/error` 被鉴权拦截）。修复：错误响应显式声明 `application/json`（跳过协商）+ 放行 `/error` + SSE 端点声明 `produces`。同上修复还覆盖了「匿名访问不存在的 public 路径」这类错误分派被改写成 403 的情况。前端原先的绕开写法（`Accept: text/event-stream, application/json`，`1537b49`）可保留也可回退。详见 `docs/stage-3.4-task-manifest-cn.md` §11.7
- **真机验收发现的降级根因（已修复，`9e27590`）**：本地 `mindora-local.yml` 只配了 `mindora.knowledge.*` 密钥，`mindora.rag.chat.api-key` 默认空 → 正常本地启动必然 `degradedReason=rag_chat_api_key_missing`（现象只是「对话模型不可用」）。修复：密钥兜底链 `${RAG_CHAT_API_KEY:${mindora.knowledge.rerank.api-key:}}` + 启动期 WARN。实测（不设 env、local profile）公开问答 `degraded=false`、模型 `Qwen/Qwen3-30B-A3B-Instruct-2507`
- **开发环境畅通性复测（2026-10-13，main `d44fb62`）**：全量 11 模块 `BUILD SUCCESS`；后端启动后检索「向量 30 + 关键词 30 → 融合 20 → 返回 8」（1037ms，说明 MySQL/Qdrant/Lucene 均通）；管理端问答 5 条引用（真实模型）；公开端点 1 条引用；SSE `delta → citation → done`（`latencyMs=2340`）；限流后端无 fail-open 告警（Redis 通）。测试前修复了 VM 网桥丢 IPv4（见下方运行约束）
- `git diff --check` 通过（仅有 Windows `LF will be replaced by CRLF` 提示）
- 浏览器验证：`/#/knowledge/retrieval` 返回真实结果并正确渲染；`/#/500`、404/403 页面正常
- 权限控制：当前后端管理接口仍以 `SUPER_ADMIN` 为主，动态后端权限模式尚未切换到 `VITE_ACCESS_MODE=backend`

## 前台流程

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表 → 文章详情
3. 按分类或标签筛选文章
4. 从详情页返回列表或首页
5. 顶栏「AI 问答」进入 `/chat`：提问后按 SSE 流式出字 → 引用卡片 → 完成后显示模型与延迟；
   点引用卡回链到原文对应章节（`/blog/{slug}#h={anchor}`）
6. 拒答（含「已出字后改判拒答」的替换）、降级、限流（60s 冷却）都在页面上有明确状态与文案；
   流式中可点「停止」中断

当前前台定位是个人站点 + 博客阅读 + 公开知识库问答；问答页（T5）已实现，真机验收待补。

## 阶段索引（明细在各自的任务清单里）

| 阶段 | 任务清单与验收证据（含接口行为 / 数据边界 / 现场联调记录） |
| --- | --- |
| Stage 2 | `docs/stage-2-task-manifest-cn.md` |
| Stage 3 | `docs/stage-3-task-manifest-cn.md` |
| Stage 3.2 | `docs/stage-3.2-task-manifest-cn.md` |
| Stage 3.3 | `docs/stage-3.3-task-manifest-cn.md` |
| Stage 3.4 | `docs/stage-3.4-task-manifest-cn.md`（计划与台账） |
| 设计契约 | `docs/design-01..11-*.md`，索引见 `docs/README.md` |
| 已归档（冻结） | `docs/archive/`（已完成的执行计划、被取代的设计） |

## 当前有效的运行约束

- **单实例**：Lucene 关键词索引是单写者，**本地不能同时运行两个后端实例**（第二个实例会因 `write.lock` 启动失败）。详见 `docs/stage-3.2-task-manifest-cn.md` 与 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`。
- **VM 端口**：本机兜底方案是 SSH 隧道（`13306→3306`、`16333→6333`、`16379→6379`），命令与覆盖用的环境变量见 `docs/development-startup-cn.md`。**2026-10-13 T5 真机验收实测宿主机可直连** `192.168.222.128` 的 3306/6333/6379/9876（`backend/mindora-app/config/mindora-local.yml` 也是直连配置，验收时未启隧道）；若某天直连超时，按 `development-startup-cn.md` 起隧道并用环境变量覆盖。
  - 为什么 ufw 列表里没有 3306/6333 却仍可直连：**Docker 的发布端口会绕过 ufw**（Docker 在 `nat`/`FORWARD` 链写 DNAT 规则，ufw 只管 `INPUT`）。此前判断的「ufw 拦截」是误判，真正原因是下面的网桥故障。
  - **安全提醒**：这意味着 MySQL / Qdrant / Redis 对同网段开放。要真正关闭需在 compose 里把端口绑定到回环（`127.0.0.1:3306:3306`）或加 `DOCKER-USER` 链规则——**只加 ufw 规则不起作用**。
- **VM Docker 网桥易丢失 IPv4（已复现三次，运行期发生、非重启导致）**：症状是「VM 上 3306 的 TCP 可连（docker-proxy 在听）但到容器不通」→ 后端报 `Communications link failure`、Qdrant `readyz` 返回 `000`。判断：`ip -4 -br addr show br-ff83ebfa1109` 无 IPv4。**首选修法（无需重启容器，2026-10-13 实测有效）**：`ip addr add 172.19.0.1/16 dev br-ff83ebfa1109`；备选 `systemctl restart docker`。触发者未定位（networkd 未接管、docker 未重启），根治需看门狗。详见 `docs/stage-3.4-task-manifest-cn.md` §10.3。
- **本地鉴权**：不设 `MINDORA_JWT_SECRET` 时后端使用默认密钥，与本机既有登录会话一致；联调令牌只在本地使用，不入库。

## 下一阶段建议

**2026-10-17 路线裁决（rev 2：项目按可上线标准做）**。**「生产级」的定义**（已写入 design-01 范围复核）：**能给陌生人用、不烧钱、坏了能发现、改了能回退、问答体验接近常见产品**；不含多实例 / 高可用 / SLA。
契约变更见 `docs/design-11-rag-answer-generation-layer.md` §15（回答契约）与 §16（提示词版本化），取舍依据见 `docs/ragnote-insights-and-architecture-takeaways.md` §五，上线门禁见 `docs/design-07-implementation-roadmap.md` Stage 5（§5.7 为就绪差距）。
**顺序：3.6 管理端问答 + 会话页 → 3.7 查询改写 → 4.1 公开域 → 4.2 回答契约 v2（含提示词版本化）→ 4.3 质量闭环 → 4.4 韧性与成本 → 4.5 运维可视化 → 4.6 双入口（仅预填）→ Stage 5 上线门禁**。

1. **收口当前分支（3.6 管理端问答 + 会话页）**：管理端问答入口（`scope=all`，可切 `public` 复测）+ 会话列表 / 消息查看 / 删除。公开站保持匿名极简（3.5 半回退的结论，不重开）。**它同时是 4.3 记录页与 `chat_conversation` 的数据来源**，故先做。
2. **3.7 查询改写**（方案已定，不重议）：有历史才触发、失败兜底原 query、三问对照验证。约束：改写只作用于检索 query，**不替换写入 prompt 的用户原句**。
3. **4.1 公开域收紧为「已发布」**（fail-closed）：`scope=public` 的候选必须能回溯到已发布文章，权威判定在 MySQL；补掉「文章 unpublish 不回退 `visibility`」缺口（`stage-3.4` §11.8-F1）。**草稿继续入库**，只收紧公开域（design-11 §15.3）。
4. **4.2 回答契约 v2 + 提示词版本化**：① 引用改**服务端摘录**，`require-verbatim-quote` 默认 `false`，引用问题不再导致拒答（编号越界只丢该条 + 删掉正文里那个 `[n]`，`no_citation` 也照常作答）；② 应答改**三态**：`grounded` 有依据作答 / `ungrounded` 无依据兜底 / `refused` 仅安全类；③ **零候选时多调一次小模型**（`Qwen/Qwen2.5-7B-Instruct`）判域并写说明，页面结构照参考项目：信息说明卡 + 可选常识（标「非知识库内容」）+ 可行动下一步 + 服务端生成的「您可能还想了解」追问；④ 有候选时禁止判「域外」；⑤ **提示词外置到后台**（V9 `prompt_template`：版本历史 / 发布即生效 / 一键回退 / 占位符校验），见 design-11 §16。**生效后必须重跑 3.3 基线并写新口径**（旧 15/15、28/30 不再可比）。
5. **4.3 问答记录 + 评价（质量闭环）**：`rag_chat_log`（query / 引用数 / 越界数 / 拒答类型 / degraded / latency / **配置快照**）+ 评价（有帮助 / 没帮助 + 原因）；管理端「会话 → 记录 → 同问题复测」。**不落**完整 prompt 与上下文（design-04 明令）。
6. **4.4 韧性与成本护栏**：向量挂→关键词（design-10 §5 已改语义）、重排挂→融合序、模型挂→摘录、关键词挂→向量；单问预算与日上限；降级必须显式标记。
7. **4.5 运维可视化**：同步 / 索引 / 检索 / 生成四类失败可见、可重试、降级告警；限流阈值（6/20 每分钟）按访问日志真实分布调整。
8. **4.6 双问答入口（只做零契约成本版）**：文章页「就本文提问」→ `/chat` 预填标题。**真伴读暂不做**（检索契约无 `documentId` 过滤；2 篇语料下与全库召回几乎等价），公开篇数 ≥ 20 后重评。
9. **Stage 5 上线门禁**（design-07）：域名 / HTTPS / 反代 + **可信代理 `X-Forwarded-For` 解析**（否则全体访客共用一个限流桶）、CORS 换真实源、备份恢复演练、隐私与 AI 免责页、`tag stage/4` 后按「管理端内测 → 公开只读 → 公开问答」放量。**已接受的硬约束**：进程内 Lucene 单写者 → 单实例 + 维护窗口。
10. **贯穿项 —— T1 语料凭据处置（公开暴露前硬门禁，本人操作）**：核实 → **轮换密钥** → 源文件脱敏 → 全量重建索引 → `node scripts/security/scan-corpus-secrets.mjs` 复扫为 0 命中。基线：全量 6 处（5 篇私有文档），public 暴露面 0。
11. **贯穿项 —— 公开内容扩充（本人内容运营）**：公开语料现为 2 篇；**由作者从草稿中挑选后手动发布**（不做自动/批量发布）；≥ 20 篇是「公开问答有实用价值」的前提，但**不是工程 done 的前提**（takeaways §5.5 D1）。

**Backlog（不排期）**：向量 / 重排 A/B（harness 缺 `--vector-only` / `--keyword-only`，补半天；参数见 takeaways §5.3）、真伴读、语义缓存。

已闭环（留档）：T5 前台问答页 2026-10-16 全部验收（`bd67327` + `7c2b736`）；T6 会话历史后端代码交付（`docs/stage-3.5-task-manifest-cn.md`），其前端会话 UI 已弃用。

永不做的清单：见 takeaways §5.5（14 项）。**多用户**（单站点多账号，参考项目那种）成本约 1 周、可做但不排期；**多租户**（一套部署多站点/多知识库）明确不做，评估见 takeaways §5.8。「答案相关性独立校验」唯一保留触发条件：4.3 数据里「引文来自原文但结论答偏」占比 > 10%。
