# MindOra 项目主文档（整体概况与开发进度）

> **这是 MindOra 项目文档的唯一主入口。**  
> 本文档负责记录项目的整体架构定位、当前开发进度总览，并通过链接分阶段直达各阶段详细文档。  
> 最近维护日期：2026-10-17

---

## 1. 项目定位与核心特性

**MindOra** 是一个轻量、高效、全栈自托管的**个人知识库与智能问答系统**。它将个人日常的技术写作、私有笔记整理与基于大语言模型（LLM）的智能检索问答深度打通：

- **内容创作与博客发布**：提供优雅沉浸的 Markdown 写作体验，支持草稿保存、封面图上传、标签分类管理与前台静态化极简阅读。
- **私有笔记单向同步**：能够单向拉取 Gitee 等外部私有笔记仓库，自动解析 Markdown 结构、智能分块切片并进行向量化索引，绝不反写污染外部笔记。
- **混合检索与智能排序**：结合了“向量语义检索”与“内嵌 Lucene 关键词全文检索”，通过 RRF 算法将双路结果智能融合，并由重排模型精准打分，找得全、找得准。
- **AI 智能问答与章节回链**：支持针对全库知识库进行精准问答，前台提供打字机式流式出字体验；回答不仅有据可查，还附带可点击跳转回原文对应章节的引用卡片。

---

## 2. 常用运行入口与地址

所有开发环境的基础设施（MySQL、Redis、Qdrant、RocketMQ）均运行在本地虚拟机（`192.168.222.128`）的 Docker 容器中。

| 模块 / 页面 | 访问地址 | 说明 |
| :--- | :--- | :--- |
| **前台展示站点** | `http://localhost:5173/` | 博客浏览、分类筛选、公开 AI 问答（`/chat`） |
| **后台管理系统** | `http://localhost:5175/` | 知识库数据源、同步任务、文章编辑、检索调试台 |
| **后端 API 服务** | `http://localhost:8080/` | Spring Boot 3 模块化单体服务入口 |
| **接口文档（Swagger）** | `http://localhost:8080/swagger-ui/index.html` | 在线 API 调试与契约测试界面 |

> 详细环境启动步骤（虚拟机网络、启动顺序、环境变量模板）请参阅 [开发启动说明](development-startup-cn.md) 与 [后端开发规则](backend-development-rules-cn.md)。

---

## 3. 分阶段开发进度看板

本项目严格执行分阶段交付原则，每个阶段均包含明确的 **Spec 设计目标** 与 **实现情况**。点击各阶段链接可直接查看详细阶段文档：

| 阶段编号 | 阶段名称 | 当前状态 | 核心成果与定位简述 | 详细阶段文档 |
| :--- | :--- | :---: | :--- | :--- |
| **Stage 0** | **工程底座** | ✅ 已完成 | Spring Boot 3 模块化骨架 + Vue 3 Monorepo，Docker 容器化基础中间件，JWT 认证与统一响应模型。 | [stage-0-task-manifest-cn.md](stage-0-task-manifest-cn.md) |
| **Stage 1** | **内容与后台管理** | ✅ 已完成 | 基于 Art Design Pro 的后台管理端，前台博客展示，Markdown 文章编辑、草稿发布与图片素材上传闭环。 | [stage-1-task-manifest-cn.md](stage-1-task-manifest-cn.md) |
| **Stage 2** | **知识库闭环与数据同步** | ✅ 已完成 | Gitee 私有仓库单向拉取同步，文档版本与切片管理，百炼 text-embedding-v4 向量入库，笔记一键转博客草稿。 | [stage-2-task-manifest-cn.md](stage-2-task-manifest-cn.md) |
| **Stage 3.1** | **检索底座与向量检索** | ✅ 已完成 | 切片层级标题与块类型元数据规范，Qdrant 密集向量相似度检索，后台检索调试接口，重排故障降级保护。 | [stage-3-task-manifest-cn.md](stage-3-task-manifest-cn.md) |
| **Stage 3.2** | **关键词与混合检索融合** | ✅ 已完成 | 进程内嵌入式 Lucene 全文检索，RRF 算法融合向量与文本双路召回，建立 MySQL 作为权限判定最终权威。 | [stage-3.2-task-manifest-cn.md](stage-3.2-task-manifest-cn.md) |
| **Stage 3.3** | **切片修复、评测基线与生成层** | ✅ 已完成 | 修复切片器逐字塌缩 Bug，建立 323 条黄金问答自动化评测基线（Hit@8 90%+），通义千问 30B 问答生成与严格引用溯源。 | [stage-3.3-task-manifest-cn.md](stage-3.3-task-manifest-cn.md) |
| **Stage 3.4** | **公开问答闭环与流式交互** | ✅ 已完成 | 前台免登录公开问答（`/chat`），Redis 访问限流与敏感信息过滤，SSE 打字机增量流式出字，章节级引用点击跳回原文。 | [stage-3.4-task-manifest-cn.md](stage-3.4-task-manifest-cn.md) |
| **Stage 3.5** | **会话历史与多轮对话记忆** | ✅ 后端完成 | 滑动窗口多轮记忆（4轮/3000 Token），MySQL 会话持久化；前台保持极简。会话历史归**账号**（公开站登录后可见，暂记 4.7），管理端不做聊天界面。 | [stage-3.5-task-manifest-cn.md](stage-3.5-task-manifest-cn.md) |
| **Stage 3.6** | **身份地基与会话域统一** | 🚧 **3.6a 完成，3.6b 未开始** | 批次①–④已交付：公开站同源化、V9 令牌表、JJWT 双令牌（`exp`/`tv`）、启动期密钥强校验、refresh/logout/me 与统一 401/403、禁用即时生效与认证限流、管理端内存令牌/静默续期/登出。下一步 3.6b（会话域统一）。 | [stage-3.6-task-manifest-cn.md](stage-3.6-task-manifest-cn.md) |
| **Stage 3.7** | **条件化查询改写** | 📋 规划中 | 针对多轮追问中的代词与省略，仅在有历史上下文时由轻量模型改写检索 Query，增强连续对话下的召回准确率。 | [设计契约与规划](design-07-implementation-roadmap.md) |
| **Stage 4** | **体验升级与质量闭环** | 📋 规划中 | 公开域收紧为已发布（4.1）→ 三态回答与后台提示词版本化（4.2）→ 问答记录与踩赞反馈（4.3）→ 容灾成本护栏（4.4）→ 运维监控（4.5）→ 关联文章提问（4.6）。 | [实施路线图](design-07-implementation-roadmap.md) |
| **Stage 5** | **生产化上线门禁** | 📋 规划中 | 独立域名与 HTTPS、反代真实 IP 穿透限流、备份演练、隐私与免责声明、单实例维护窗口约束。 | [上线标准与就绪差距](design-07-implementation-roadmap.md) |

---

## 4. 核心系统规范与设计契约（单一事实来源）

本项目坚持规范驱动，核心架构与对外契约均有明确的设计文档：

| 领域主题 | 对应设计文档 | 核心规范内容 |
| :--- | :--- | :--- |
| **产品定位与系统架构** | [design-01](design-01-product-scope.md) / [design-02](design-02-system-architecture.md) | 产品功能边界、模块化单体架构与分层设计原则 |
| **数据同步与存储流转** | [design-03](design-03-data-and-sync-flow.md) / [design-06](design-06-data-model-and-api-boundaries.md) | 外部仓库同步时序、数据库表结构模型与 API 边界 |
| **切片元数据规范** | [design-09](design-09-chunk-metadata-and-vector-payload.md) | Markdown 切片层级路径、块类型与 Qdrant Payload 规范 |
| **混合检索与 RRF 融合** | [design-10](design-10-keyword-retrieval-and-hybrid-fusion.md) | 嵌入式 Lucene 分词配置、双路召回算法与权限过滤 |
| **问答生成与应答契约** | [design-11](design-11-rag-answer-generation-layer.md) | 流式 SSE 事件契约、应答三态（有依据/兜底/拒绝）、服务端摘录引用与提示词版本化 |
| **身份与令牌生命周期** | [design-12](design-12-identity-and-token-lifecycle.md) | 双令牌（Access 30 分钟 / Refresh 14 天滑动）、轮换与撤销、401 语义、Cookie 约定与 V9 迁移 |
| **实施路线与阶段定义** | [design-07](design-07-implementation-roadmap.md) | 整体阶段推进顺序、可上线判据与技术取舍 |

---

## 5. 开发流程与文档协作要求

在本项目中工作的所有代理与开发者必须严格遵守根目录 [AGENTS.md](../AGENTS.md) 中的协作规范：

1. **坚持 SDD（规范驱动开发）核心流程**：
   - **Step 1 规范先行**：根据业务需求先明确设计文档（Spec），厘清输入输出与边界。
   - **Step 2 跑通核心 MVP**：优先开发最小可行产品（MVP），快速把核心功能链路跑通，暴露真实问题。
   - **Step 3 grill-me 深度讨论**：根据 MVP 实测中暴露出的问题、冲突或瓶颈，主动向用户发起追问式深度讨论（grill-me 机制），复盘并确定调整方案。
   - **Step 4 闭环更新文档**：讨论达成一致后，再把最终经过实战验证的真实设计与契约更新回文档，保持代码与文档严密一致。
2. **记录性文档必须「说人话」**：
   - 严禁生硬堆叠技术性名词、指令堆砌或冗长杂乱的提交哈希。
   - 必须用清晰、通俗、人能看懂的语言描述「解决了什么问题」、「做成了什么效果」、「有哪些已知限制」。
3. **大阶段中小阶段的临时文档管理**：
   - 在大阶段内部拆分子任务实现具体功能时，如果确实需要梳理设计，可以生成带有临时标记的临时文档。
   - 该功能开发与验收结束后，关键结论并入阶段文档，**临时文档必须立即删除**，严禁长期滞留污染目录。
4. **杜绝碎片化记录与随意建档**：
   - 严禁随意新建低效的日志或备忘录。所有工程事实统一维护在既有设计文档或对应的阶段文档中。

---

## 6. 归档与历史资产说明

- **归档文档目录**：[docs/archive/](archive/README.md) 用于存放已完成使命的阶段性执行计划与被取代的历史设计方案，归档后冻结不再修改。
- **设计原型快照**：`docs/prototypes/` 存放纯静态 HTML 页面原型，仅供视觉交互参考，不参与构建，不影响生产代码。
- **评测工具与题库**：评测脚本位于 `scripts/rag-eval/`，候选评测题库详见 [评测题库说明](stage-3.3-candidate-questions-cn.md)。
