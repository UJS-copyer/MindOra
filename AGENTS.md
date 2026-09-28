# AGENTS.md — MindOra 代理协作记忆

本文件是仓库级的代理协作约定，任何在本仓库工作的代理都应遵守。它记录的是**长期规则**，而不是一次性指令。

## 子代理与模型分工

- **简单任务、以及可以并行的任务，优先开启多个子代理，并使用 `easy-cliproxyapi/gemini-3.8-flash-high` 执行**，以提高效率。
- controller（主控代理）保留**整体方案、任务拆分、验收口径与实现思路**的决策权，负责把控方向、拆解任务与最终把关。
- 并行拆分必须落在**不同文件或不同关注点**上；同一个文件、或同一处语义改动，不得同时派给多个子代理，避免相互覆盖。
- 子代理的产出**必须被独立验证**（定向测试、构建、契约检查或实际运行），不得仅凭其自述结论就接受。
- 每个子任务仍需「编码 → 定向测试通过 → 独立提交」，保持项目全程可运行、不损坏。
- 模型与角色绑定见 `subagents/agents/agent-policy.toml`；角色职责见 `subagents/agents/*.md`。
- 门禁评审（spec review、quality review）默认沿用 controller 模型以保持把关强度；如需一并交由 flash-high，修改 `agent-policy.toml` 中对应两项即可。

## 现有约定索引

- 设计文档：`docs/design-*.md`（最新为 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`：关键词索引与 Hybrid/RRF 融合契约）
- 阶段路线与交付原则：`docs/design-07-implementation-roadmap.md`
- 后端结构、包边界与本地开发规则：`docs/backend-development-rules-cn.md`
- 项目状态、接口行为与现场联调记录：`docs/project-status-cn.md`
- 各阶段任务清单与验收证据：`docs/stage-*-task-manifest-cn.md`
- 环境启动（VM 基础设施 / 后端 / 前端）：`docs/development-startup-cn.md`
- 本地环境变量模板：`docs/project-env-local.md`
- **RAG 入门与本项目实现讲解（选型理由、代码地图、术语速查）：`docs/rag-guide-cn.md`**

## 分支与提交策略

- **前后端改动分属不同分支**，避免工作区同时混合前后端改动：
  - 后端：`feature/stage-<n>-backend`
  - 前端：`feature/stage-<n>-frontend`
  - 集成：`feature/stage-<n>-integration`
  - 与 `subagents/agents/agent-policy.toml` 中已声明的 branch pattern 保持一致。子阶段同样适用（例如 `feature/stage-3.2-backend`）。
- 分支内仍保持「小功能 → 定向测试通过 → 独立提交」的纪律；**压缩发生在合并回主线的环节**。
- **阶段（或子阶段）验收通过后再合并回 `main`**，并在合并时压缩细小提交，避免主线上堆积过多琐碎历史。
- 不在同一个工作区同时暂存前后端改动；跨端集成只落在 integration 分支。

## 硬性边界

- 不回滚、不丢弃来源不明的工作区改动；禁止批量删除或递归删除。
- 外部服务（Gitee、Embedding、Rerank、向量库）只能经由 adapter / port 边界访问。
- 业务模块之间不得直接访问对方的 mapper、repository、infrastructure 或领域写操作。
- 后台页面只写入 `frontend/apps/admin-art`。
- Flyway 迁移只追加，不修改历史迁移文件。
