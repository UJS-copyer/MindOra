# MindOra 项目状态总览

本文只记录**当前有效**的项目状态：迁移结果、Git 集成状态、启动入口、各阶段开发状态、检查结果与下一阶段建议。

> **历史细节已按阶段迁出**：各阶段的「接口与行为」「数据边界」「现场联调记录」现在都在对应的 `stage-*-task-manifest-cn.md` 里（见下方「阶段索引」）。本文件不再累计各阶段明细，保持在一屏可读范围内。

最近维护：2026-10-10

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`，旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## Git 集成状态

- `main`：`cdda506`（Stage 3.3 已压缩合并），领先 `origin/main` **9 个提交（未推送）**
- 各阶段基线/交付提交：
  - Stage 1：`d5b160d`（后端内容）、`62a5cab`（前端内容 + 集成）
  - Stage 2：`b341a77`
  - Stage 3：`34e7697`（主线）、`e4a60df`（检索底座交付）
  - Stage 3.2：`08ec7b0`（关键词与融合）、`3c271e8`（检索调试页）、`67a2b67`（契约与联调记录）
  - Stage 3.3：`cdda506`（压缩合并；来源分支 `feature/stage-3.3-backend` 的 16 个提交按约定保留，仅供追溯）
- 当前开发：
  - 后端 Stage 3.4（公开问答闭环）待开工，见 `docs/stage-3.4-task-manifest-cn.md`
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
| Stage 3.3 | 已完成（待合并） | 入库质量修复、323 条黄金集评测基线、RAG 生成层（单次问答 + 引用定位 + 三段式拒答 + 生成侧降级） |
| Stage 3.4 | 进行中（待开工） | 公开问答闭环：语料凭据处置 → 公开端点与限流 → 契约同步 → SSE 流式 → 前台问答页 → 会话历史。见 `docs/stage-3.4-task-manifest-cn.md` |
| Stage 4 | 未开始 | 运维与可观测增强 |

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

- 后端全量测试：11 个模块 `BUILD SUCCESS`（Stage 3.3 收尾时实测）
- `mindora-rag` 定向测试 42 个通过（适配器 6 / 组装器 10 / 校验器 17 / 编排 9）
- 实机联调（真实模型 + 2028 切片语料）：正例 15/15、负例 28/30、降级路径返回 `degraded=true` 而非 500
- `npm run api:sync` / `api:check` / `typecheck` / `build --workspace @mindora/admin-art` 均通过（Stage 3.2 时实测；**新增的 `POST /api/v1/admin/rag/answer` 尚未同步进 OpenAPI，见 Stage 3.4 任务 T3**）
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
- **本地鉴权**：不设 `MINDORA_JWT_SECRET` 时后端使用默认密钥，与本机既有登录会话一致；联调令牌只在本地使用，不入库。

## 下一阶段建议

**Stage 3.4：公开问答闭环。** 完整任务拆分、优先级、验收口径与依赖顺序见 **`docs/stage-3.4-task-manifest-cn.md`**，要点：

1. **T1 语料凭据处置（公开前硬门禁）**：核实 → 轮换密钥 → 脱敏 → 全量重建索引 → 复扫验收。
2. **T2 公开端点与限流**：`POST /api/v1/public/rag/answer`，`scope=public` 服务端硬编码 + Redis 令牌桶。
3. **T3 契约同步**：`npm run api:sync` / `api:generate`，把新端点写进 OpenAPI 与生成的 TS 类型，并扩充 `OpenApiContractTest` 的路径断言。
4. **T4 SSE 流式**：打通已预留的 `ChatOptions.streamConsumer` 槽位，拒答/降级事件也要有流式语义。
5. **T5 前台问答页**（独立前端分支）：引用可点击回链原文，拒答与降级有明确 UI。
6. **T6 会话历史**：登录用户落库 `conversationId`。
7. **T7 质量收尾**：323 条黄金集人工复核定稿、RAG 请求日志与降级告警。

已确认的安全策略（2026-10-10）：**不引入入库门禁**，「检索时不可见」即被接受为控制粒度；可选增强为「回答可指出密钥/配置项的名称但不输出其值」。

暂不引入（待评测数据支撑后再评估）：HyDE / Multi-Query 查询改写、Contextual Retrieval 上下文前缀、父子块检索、Agentic RAG 与 GraphRAG。理由见 `docs/rag-guide-cn.md` 第 2 章与决策矩阵。
