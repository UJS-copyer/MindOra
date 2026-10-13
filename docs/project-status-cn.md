# MindOra 项目状态总览

本文只记录**当前有效**的项目状态：迁移结果、Git 集成状态、启动入口、各阶段开发状态、检查结果与下一阶段建议。

> **历史细节已按阶段迁出**：各阶段的「接口与行为」「数据边界」「现场联调记录」现在都在对应的 `stage-*-task-manifest-cn.md` 里（见下方「阶段索引」）。本文件不再累计各阶段明细，保持在一屏可读范围内。

最近维护：2026-10-11

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`，旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## Git 集成状态

- `main`：`2f56211`（Stage 3.3 已压缩合并 `cdda506`），领先 `origin/main` **10 个提交（未推送）**
- 各阶段基线/交付提交：
  - Stage 1：`d5b160d`（后端内容）、`62a5cab`（前端内容 + 集成）
  - Stage 2：`b341a77`
  - Stage 3：`34e7697`（主线）、`e4a60df`（检索底座交付）
  - Stage 3.2：`08ec7b0`（关键词与融合）、`3c271e8`（检索调试页）、`67a2b67`（契约与联调记录）
  - Stage 3.3：`cdda506`（压缩合并；来源分支 `feature/stage-3.3-backend` 的 16 个提交按约定保留，仅供追溯）
  - Stage 3.4（后端）：`feature/stage-3.4-backend` 领先 main **8 个提交**（`b89e6bb` 公开端点 → `13adbd5` 限流 → `ee50bbb` SecretGuard → `442e2c7` 适配器流式 → `2722edc` answer 抽取器 → `7d4958f` SSE 端点 → `a25b5ba` 契约同步 → `17356f1` 扫描脚本），**待阶段验收后压缩合并**
- 当前开发：
  - 后端 Stage 3.4 后端闭环已交付（T5 前台问答页、T6 会话历史顺延 Stage 3.5），见 `docs/stage-3.4-task-manifest-cn.md`
  - 前端原型已收敛到 `feature/stage-3.4-frontend`（`bc5fc81`）：首页 `HomeView`、AI 问答 `AiChatView`、`App.vue` 路由接线、`SiteHeader` 导航、`docs/prototypes/` 下的个人工作台快照。**原 `feature/stage-4-frontend` 与 stash 中的同名文件已成为冗余副本**，内容与收敛结果逐字节一致
- 已完成阶段的分支（内容均已通过压缩合并进入 main）与空指针分支待清理；清理需人工确认后执行
- 工作区约定：前后端改动分属不同分支；不在同一工作区同时暂存前后端改动

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
| Stage 3.4 | **后端已完成**（待验收合并） | 公开问答后端闭环：公开端点（scope 服务端硬编码）、固定窗口限流、密钥兜底 SecretGuard、SSE 流式（事件契约 delta→citation→done）、契约同步与访问日志。**T1 凭据轮换待本人操作；T5 前台问答页与 T6 会话历史顺延 Stage 3.5**。见 `docs/stage-3.4-task-manifest-cn.md` §10 |
| Stage 4 | 未开始 | 运维与可观测增强（含 RAG 请求落库、额度报表与降级告警） |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 工作台壳、路由、请求层、权限基础、主题与布局 |
| Stage 1 | 已完成 | 迁移到 Art Design Pro 方案并完成主要后台页面 |
| Stage 2 | 已完成 | `admin-art` 知识库数据源、同步任务、文档、索引状态、失败重试、切片与模型配置页；mock 默认关闭 |
| Stage 3 / 3.2 | 已完成 | `/knowledge/retrieval` 检索调试页（含「命中来源」「两路排名 / RRF」列与召回→融合→返回指标栏） |
| Stage 3.3 | 已交付 | 后端入库质量修复 + 323 条黄金集评测基线 + RAG 生成层（单次问答 + 引用定位 + 三段式拒答 + 生成侧降级）；已压缩合并进 main（`cdda506`）。**前端无改动** |
| Stage 4 | 未开始 | 监控、告警、运维视图 |

## 当前检查结果

- 后端全量测试：11 个模块 `BUILD SUCCESS`（Stage 3.4 交付时实测）
- `mindora-rag` 定向测试 **85** 个通过（适配器 11 / 组装器 10 / 校验器 17 / 编排 20 / SecretGuard 6 / 流式密钥窗口 6 / answer 抽取器 12 / 限流适配器 7）；`mindora-app` **39** 个（含公开端点 5 + 限流 4 + SSE 6 + 契约 1）
- 实机联调（真实模型 + 2028 切片语料）：
  - 管理端回归（REPEAT=3）：正例 **15/15**（流式重构无回归）、负例 27/30（唯一失败为 Kafka 边界用例）
  - 公开端点：公开文档 2/2 作答且**引用只来自 public**；负例 2/2 拒答；**私有内容 2/2 零召回**
  - SSE：14 个事件 `delta×12 → citation → done`，`firstDeltaMs=9125ms`
  - 限流：`200,200,429,429,429,429`（额度压到 2 时），SSE 预检返回 JSON 429
  - 降级：对话模型不可用时 JSON 与 SSE 均 `degraded=true`，**非 500**
  - 语料凭据扫描：全量 158 篇 6 处高置信命中（均在私有文档）；**public 暴露面 0 命中**
- `npm run api:sync` / `api:generate` / `api:check` 通过（公开问答三端点已进 OpenAPI 与生成的 TS 类型）
- **开发环境畅通性复测（2026-10-13，main `d44fb62`）**：全量 11 模块 `BUILD SUCCESS`；后端启动后检索「向量 30 + 关键词 30 → 融合 20 → 返回 8」（1037ms，说明 MySQL/Qdrant/Lucene 均通）；管理端问答 5 条引用（真实模型）；公开端点 1 条引用；SSE `delta → citation → done`（`latencyMs=2340`）；限流后端无 fail-open 告警（Redis 通）。测试前修复了 VM 网桥丢 IPv4（见下方运行约束）
- `git diff --check` 通过（仅有 Windows `LF will be replaced by CRLF` 提示）
- 浏览器验证：`/#/knowledge/retrieval` 返回真实结果并正确渲染；`/#/500`、404/403 页面正常
- 权限控制：当前后端管理接口仍以 `SUPER_ADMIN` 为主，动态后端权限模式尚未切换到 `VITE_ACCESS_MODE=backend`

## 前台流程

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表 → 文章详情
3. 按分类或标签筛选文章
4. 从详情页返回列表或首页

当前前台定位是博客展示与阅读入口；问答页属 Stage 3.4 范围。

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
- **VM 端口**：VM 的 ufw 未放行 3306/6333，本机通过 SSH 隧道（13306/16333/16379）访问；命令与覆盖的环境变量见 `docs/development-startup-cn.md`。
- **VM Docker 网桥易丢失 IPv4（已复现三次，运行期发生、非重启导致）**：症状是「VM 上 3306 的 TCP 可连（docker-proxy 在听）但到容器不通」→ 后端报 `Communications link failure`、Qdrant `readyz` 返回 `000`。判断：`ip -4 -br addr show br-ff83ebfa1109` 无 IPv4。**首选修法（无需重启容器，2026-10-13 实测有效）**：`ip addr add 172.19.0.1/16 dev br-ff83ebfa1109`；备选 `systemctl restart docker`。触发者未定位（networkd 未接管、docker 未重启），根治需看门狗。详见 `docs/stage-3.4-task-manifest-cn.md` §10.3。
- **本地鉴权**：不设 `MINDORA_JWT_SECRET` 时后端使用默认密钥，与本机既有登录会话一致；联调令牌只在本地使用，不入库。

## 下一阶段建议

**Stage 3.4 后端闭环已交付**（公开端点 + 限流 + 密钥兜底 + SSE + 契约同步 + 访问日志），完整证据见 **`docs/stage-3.4-task-manifest-cn.md` §10**。下一步（Stage 3.5）按优先级：

1. **完成 T1 语料凭据处置（公开暴露前硬门禁，本人操作）**：核实 → **轮换密钥** → 源文件脱敏 → 全量重建索引 → `node scripts/security/scan-corpus-secrets.mjs` 复扫为 0 命中。当前基线：全量 6 处（5 篇私有文档），public 暴露面 0。
2. **T5 前台问答页**（独立前端分支 `feature/stage-3.4-frontend` 已收敛原型）：消费 `/api/v1/public/rag/answer/stream`（`delta` → `citation` → `done`），引用回链原文，拒答/降级/限流有明确 UI，并处理「已流出文字随后被判定为拒答」的替换逻辑。
3. **T6 会话历史**：登录用户落库 `conversationId`，多轮记忆按 design-04（`chat_context_turns=4`、`max_history_tokens=3000`）实现并补评测。
4. **公开前决策**：公开语料当前仅 1 篇（《阅读线路》）——公开问答要有实用价值，需要先扩充公开内容（本人内容运营决策）；限流阈值（6/20 每分钟）按访问日志的真实分布调整。
5. **Stage 4 运维增强**：RAG 请求落库（`rag_chat_log`）、额度报表、降级告警、同步/索引失败面可视化。

暂不引入（待评测数据支撑后再评估）：HyDE / Multi-Query 查询改写、Contextual Retrieval 上下文前缀、父子块检索、Agentic RAG 与 GraphRAG；答案相关性独立校验（NLI/verifier）先量化 Kafka 类边界的占比再决定。理由见 `docs/rag-guide-cn.md` 第 2 章与决策矩阵。
