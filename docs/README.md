# MindOra 文档索引与单一事实来源

> **这是 `docs/` 的唯一入口。新建任何文档前先读这里。**
> 创建规则（何时允许新建、放哪、叫什么、写什么、何时归档）见根目录 `AGENTS.md` 的「文档创建规则」章节。

最近维护：2026-10-17

## 1. 单一事实来源（SSOT）

同一件事**只在一个文档里定义**，其他文档只放链接。

| 你想知道 | 只看这一个 | 不要另建 |
| --- | --- | --- |
| 产品范围 / 系统架构 / 数据与同步流 / 后台范围 / 数据模型与 API 边界 | [design-01](design-01-product-scope.md) ~ [design-06](design-06-data-model-and-api-boundaries.md) | — |
| 阶段路线与交付原则（含 Stage 4 与发布标准） | [design-07](design-07-implementation-roadmap.md) | — |
| RAG 总体设计（多轮 / 流式 / 公开问答**已交付**；仍以其为需求来源的是日志与质量分析、策略开关、模块启停） | [design-04](design-04-rag-and-chat.md) | — |
| 切片元数据与向量 payload 契约 | [design-09](design-09-chunk-metadata-and-vector-payload.md) | — |
| 关键词索引与 Hybrid/RRF 融合契约 | [design-10](design-10-keyword-retrieval-and-hybrid-fusion.md) | — |
| RAG 问答生成层契约（引用 / 拒答 / 降级 / 配置） | [design-11](design-11-rag-answer-generation-layer.md) | — |
| 现在能不能跑、接口行为、现场联调记录、下一阶段建议 | [project-status-cn.md](project-status-cn.md) | — |
| 某阶段的任务状态与验收证据 | `stage-<N.M>-task-manifest-cn.md` | — |
| 为什么选 A 不选 B（附可核实来源） | [rag-retrieval-improvement-survey-cn.md](rag-retrieval-improvement-survey-cn.md) | — |
| RAG 入门与代码地图（学习向） | [rag-guide-cn.md](rag-guide-cn.md) | — |
| 怎么启动（VM 基础设施 / 后端 / 前端） | [development-startup-cn.md](development-startup-cn.md) / [project-env-local.md](project-env-local.md) | — |
| 后端分层、包边界与本地开发规则 | [backend-development-rules-cn.md](backend-development-rules-cn.md) | — |
| 检索评测工具用法与黄金集标注流程 | [../scripts/rag-eval/README.md](../scripts/rag-eval/README.md) | — |
| 接口契约机读件（OpenAPI） | [openapi/openapi.json](openapi/openapi.json)（[说明](openapi/README.md)） | — |

## 2. 设计契约（长期维护，序号只增不改）

| 文档 | 主题 | 状态 |
| --- | --- | --- |
| [design-01](design-01-product-scope.md) | 产品范围 | 生效 |
| [design-02](design-02-system-architecture.md) | 系统架构 | 生效 |
| [design-03](design-03-data-and-sync-flow.md) | 数据与同步流 | 生效 |
| [design-04](design-04-rag-and-chat.md) | RAG 与智能问答（总体） | **部分被取代**：检索/元数据/生成细节以 design-09/10/11 为准；多轮（3.5）、流式（3.4）、公开问答权限模型（3.4）**已交付**；**日志与质量分析、策略开关、模块启停仍以本文为准**（未兑现） |
| [design-05](design-05-admin-console.md) | 后台与运维 | 生效 |
| [design-06](design-06-data-model-and-api-boundaries.md) | 数据模型与 API 边界 | 生效 |
| [design-07](design-07-implementation-roadmap.md) | 实施路线图 | 生效（持续更新阶段状态） |
| [design-08](design-08-frontend-design-spec-stage-1.md) | 前端设计规格（Stage 1） | 生效（Stage 1 范围） |
| [design-09](design-09-chunk-metadata-and-vector-payload.md) | 切片元数据与向量 payload | 生效 |
| [design-10](design-10-keyword-retrieval-and-hybrid-fusion.md) | 关键词检索与 Hybrid/RRF | 生效 |
| [design-11](design-11-rag-answer-generation-layer.md) | RAG 问答生成层 | 生效 |

## 3. 阶段交付（任务与验收证据）

| 文档 | 阶段 | 状态 |
| --- | --- | --- |
| [stage-2-task-manifest-cn.md](stage-2-task-manifest-cn.md) | Stage 2 知识库闭环 | 已交付（含接口行为 / 数据边界 / 联调记录） |
| [stage-3-task-manifest-cn.md](stage-3-task-manifest-cn.md) | Stage 3 检索底座 | 已交付（含接口行为 / 数据边界 / 联调记录） |
| [stage-3.2-task-manifest-cn.md](stage-3.2-task-manifest-cn.md) | Stage 3.2 关键词与融合 | 已交付（含接口行为 / 权限修复 / 联调记录 / 运行约束） |
| [stage-3.3-task-manifest-cn.md](stage-3.3-task-manifest-cn.md) | Stage 3.3 入库质量 + 评测基线 + 生成层 | 生成层待合并回 main（含接口行为 / 联调记录 / 归因方法） |
| [stage-3.4-task-manifest-cn.md](stage-3.4-task-manifest-cn.md) | Stage 3.4 公开问答闭环 | **已交付**（T1 凭据轮换仍待本人操作；T5 前台问答页、T6 会话历史均已在 3.4 / 3.5 交付）。接口行为与实机联调记录见其 §10 |
| [stage-3.5-implementation-plan-cn.md](stage-3.5-implementation-plan-cn.md) | Stage 3.5 会话历史与多轮记忆（T6） | **已实施**：记忆策略、存储选型、改写取舍与评测口径；前端会话 UI 已弃用（半回退）。§5「不做查询改写」已由 3.7 取代 |
| [stage-3.5-task-manifest-cn.md](stage-3.5-task-manifest-cn.md) | Stage 3.5 任务清单与台账 | **代码交付**（后端 API 实机验证通过；浏览器 UI 验收与多轮黄金集评测待补） |

阶段执行计划在执行完毕后进 [archive/](archive/)。**当前阶段**的计划与任务清单见上表最后一行（计划与台账合一，不再另建计划文档）。

> **约定**：各阶段的「接口与行为」「数据边界」「现场联调记录」一律写在对应阶段的任务清单里；`project-status-cn.md` 只保留当前有效状态与阶段索引，不再累计阶段明细。

## 4. 状态台账

| 文档 | 职责 |
| --- | --- |
| [project-status-cn.md](project-status-cn.md) | **当前有效状态**：迁移结果、Git 集成状态、启动入口与地址、各阶段开发状态、检查结果、前台流程、阶段索引、运行约束、下一阶段建议 |

## 5. 调研与选型

| 文档 | 职责 |
| --- | --- |
| [rag-retrieval-improvement-survey-cn.md](rag-retrieval-improvement-survey-cn.md) | **结论**：候选方案族、各自代价、为什么选/不选（附来源） |
| [ragnote-insights-and-architecture-takeaways.md](ragnote-insights-and-architecture-takeaways.md) | 同类项目（ragnote）的可借鉴点与架构结论 |
| `logs/research-1..6-*.md` | **过程材料**：六份分报告，结论已并入上面两份 |

## 6. 规则与上手

| 文档 | 职责 |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | 代理协作规则、**文档创建规则**、分支与提交策略、硬性边界 |
| [backend-development-rules-cn.md](backend-development-rules-cn.md) | 后端结构、包边界、日志与常驻进程规则 |
| [development-startup-cn.md](development-startup-cn.md) | 环境启动（VM / 后端 / 前端） |
| [project-env-local.md](project-env-local.md) | 本地环境变量模板 |
| [../README.md](../README.md) | 项目总览与快速开始 |

## 7. 归档与生成物

| 位置 | 规则 |
| --- | --- |
| [archive/](archive/) | **冻结**的历史文档（已完成的执行计划、被取代的设计）。不再修改，只在 [archive/README.md](archive/README.md) 追加索引。 |
| `docs/generated/` | 脚本产出物（评审清单、报表、dump）。**不进版本库**，不得放在 `docs/` 根目录。 |
| `docs/prototypes/` | **设计原型静态快照**（HTML），只作视觉/交互参考，不参与构建、不被应用引用。改动原型不影响运行代码。 |
| `logs/` | 运行日志、评测脚本、语料导出、调研过程材料。不进版本库。 |

## 8. 已知文档债务（待处理）

| 问题 | 证据 | 处置 |
| --- | --- | --- |
| **超长文档** | `design-06` 798、`rag-guide-cn` 705、`design-05` 538、**`design-04` 507（已越 500 线，尚未拆分）**、`design-11` 498（§15 契约 v2 + §16 提示词版本化；**下次改到必须先拆**：把 §10–§13 拆成 `design-12-public-qa-and-streaming.md`）、`design-07` 495（Stage 4/5 + 就绪差距）、`design-03` 399、`stage-3.3-task-manifest-cn` ≈440、`stage-3.2-task-manifest-cn` ≈150 | 规则：新建 ≤ 300 行、涨到 500 行必须拆分。**不专项重构**，按「下次因业务需要改到该文档时顺手拆分」处理，`design-06`、`design-04` 与 `stage-3.3-task-manifest-cn` 优先 |
| 生成物混在 `docs/` 根目录 | [stage-3.3-candidate-questions-cn.md](stage-3.3-candidate-questions-cn.md) 单文件约 299 KB，是脚本生成的候选问题评审清单 | 迁至 `docs/generated/`（已加入 `.gitignore`）。**该文件正在被编辑，暂缓执行**；Stage 3.4 T7 会用到它 |
| 主题重复叙述 | [rag-guide-cn.md](rag-guide-cn.md) 第 2 章「业界成熟方案速览」与调研文档重复；第 4 章「能力边界」与 project-status 重复 | 内容保留（学习价值独立），已加指针声明「结论以 design/survey 为准」 |
| 原始设计与落地契约并存 | [design-04](design-04-rag-and-chat.md) 的检索/生成章节已被 design-09/10/11 细化 | 已标注状态，明确哪部分仍以 design-04 为准 |
