# RAG 入门与本项目实现讲解（MindOra）

> 这份文档写给**正在一边实现、一边学习 RAG 的人**。
>
> 它解决三个问题：
> 1. RAG 到底是什么，业界的「标准答案」长什么样（有来源，可自行核对）；
> 2. 本项目（MindOra）现在是怎么实现的，**每个选择为什么这么定**，以及刻意**没做**什么；
> 3. 你接下来每写一行代码，应该能对应到本文的哪一节。
>
> 阅读建议：先通读第 1、2 章建立心智模型，再精读第 3 章（那是你正在写的代码），第 4、5 章当字典用。
>
> **本文所有引用的一手资料都已逐条核实**（论文标题/作者/年份、官方文档 URL），来源列表见文末附录 A。

---

## 目录

- [第 1 章 RAG 是什么：先建立正确的心智模型](#第-1-章-rag-是什么先建立正确的心智模型)
- [第 2 章 业界成熟方案速览](#第-2-章-业界成熟方案速览)
- [第 3 章 本项目的实现思路与架构](#第-3-章-本项目的实现思路与架构)
- [第 4 章 当前能力边界与后续路线](#第-4-章-当前能力边界与后续路线)
- [第 5 章 学习路径与速查表](#第-5-章-学习路径与速查表)
- [附录 A 参考资料（已核实）](#附录-a-参考资料已核实)

---

## 第 1 章 RAG 是什么：先建立正确的心智模型

### 1.1 大模型的两个硬伤

大语言模型（LLM, Large Language Model）有两个绕不过去的限制：

| 限制 | 通俗解释 | 后果 |
| --- | --- | --- |
| **知识截止（Knowledge Cutoff）** | 模型的"记忆"停在训练数据的时间点 | 问它你昨天写的笔记、你公司的内部文档，它不可能知道 |
| **幻觉（Hallucination）** | 模型本质是"概率接龙"，它倾向于生成**看起来合理**的文本，而不是**真实**的文本 | 它会一本正经地编造不存在的 API、错误码、论文 |

RAG（**R**etrieval-**A**ugmented **G**eneration，检索增强生成）就是针对这两点的一类药物：**在模型开口之前，先去你的资料库里把相关材料找出来，塞进提示词（Prompt）里，让模型"看着材料回答"。**

> 一句话：**RAG = 给大模型配一个会查资料的助理**，而不是指望它什么都记住。

### 1.2 三种方案对比

这是最该先搞清楚的取舍，因为它决定了你要不要做 RAG：

| 方案 | 适用场景 | 核心代价 |
| --- | --- | --- |
| **直接问大模型** | 通识常识、文案写作、代码逻辑推演 | 拿不到你的私有数据与实时信息 |
| **全量塞进长上下文** | 单篇长文精读、一份合同审阅（文档固定且不多） | Token 成本与首字延迟随长度剧增；且存在"**迷失在中间**"现象——长文本中段的关键信息召回率明显衰减（[Liu et al., 2023](https://arxiv.org/abs/2307.03172)） |
| **RAG** | 动态更新的知识库、内部资料、需要**权限控制**与**溯源**的问答 | 依赖检索准确度；引入了切块与检索链路的工程复杂度 |

**关键判断**：长上下文模型（100 万 Token）普及后，RAG 不会被淘汰。因为 RAG 真正提供的价值是**海量知识的动态准入、实时增删改、细粒度权限管控与可审计溯源**——这些是"把资料塞进 Prompt"永远替代不了的数据管理职责。你做的 Obsidian → Gitee → 知识库这条链路，本质上就是在解决这个问题。

### 1.3 RAG 的完整链路：五个环节，每一环都可能出错

**这是全文最重要的一张图。** 新手常以为 RAG 是"接个向量库"，其实它是一条五段流水线：

```
①文档加载/切块 → ②索引构建 → ③召回检索 → ④后处理/重排 → ⑤生成回答
   Chunking        Indexing     Retrieval    Rerank          Generation
```

每一环的典型失败模式（参考 [Seven Failure Points](https://arxiv.org/abs/2401.05856)，Barnett et al., 2024）：

| 环节 | 典型失败 | 本项目的应对 |
| --- | --- | --- |
| ① 切块 | 机械按字数截断，把表格、代码块切断，语义支离破碎 | 结构感知切块，按标题层级与块类型切（第 3.2 节） |
| ② 索引 | 只存正文，丢了标题路径、权限等元数据 | 切片携带 `headingPath`/`blockType`/权限字段 |
| ③ 召回 | 词汇鸿沟（同义不同词搜不到）或语义漂移（专有名词搜错） | 向量 + 关键词**双路召回** |
| ④ 重排 | 截断过窄丢掉答案，或冗余文本干扰模型注意力 | 先融合去重，再交叉编码器重排 |
| ⑤ 生成 | 模型无视材料继续编造 | **本项目尚未实现**（见第 4 章） |

> **记住这个结论**：RAG 的效果上限由 **①→④** 决定。检索找不对，第⑤步再强的模型也只会"流利地胡说"。这就是本项目**先把检索做扎实再碰生成**的原因。

### 1.4 三代范式：你的系统在往哪一代演进

这个划分出自综述 [Retrieval-Augmented Generation for Large Language Models: A Survey](https://arxiv.org/abs/2312.10997)（Gao et al., 2023）：

| 范式 | 定义 | 你的系统对应 |
| --- | --- | --- |
| **朴素 RAG（Naive）** | 切块 → 向量化 → 相似度 Top-K → 拼进 Prompt | Round 3.1 之前的状态 |
| **高级 RAG（Advanced）** | 在检索**前**做查询改写，在检索**后**做重排与压缩 | **当前项目正在做的**（双路召回 + RRF + 重排） |
| **模块化 RAG（Modular）** | 检索/路由/重排/反思拆成可编排模块，支持多轮与 Agent | 未来可选 |

奠基论文是 Facebook AI Research 的 [Lewis et al., 2020](https://arxiv.org/abs/2005.11401)，它把检索器与生成器**联合训练**。但今天工业界的 RAG 基本都走"**外挂工程**"路线：**不微调模型权重**，只靠向量库 + 提示词工程，因为这样成本低、迭代快。

### 1.5 初学者最常犯的三个认知错误

1. **"接了向量数据库就是合格的 RAG"**
   纯向量检索有个先天弱点：对**专有名词、型号、错误码、缩写**极不敏感。你问"Qdrant 的 `points/search` 为什么报 NamedVectorStruct 错"，向量检索很可能给不出那条笔记，而关键词检索一击即中。业界标配基线是**混合检索 + 重排**。

2. **"检索质量全靠调 chunk size"**
   切块长度只是表面参数。真正决定检索上限的是**文档结构的保真度**（按标题层级切）、**元数据挂载**（标题路径、块类型），以及对**查询意图**的理解。把结构切没了，再调长度也救不回来。

3. **"长上下文普及后 RAG 就过时了"**
   见 1.2 节。RAG 的护城河是**动态准入 + 权限 + 溯源**，不是"省 Token"。

---

## 第 2 章 业界成熟方案速览

本章是"标准答案库"。**每一节末尾都会标注本项目是否采用**，以及判断理由。

### 2.1 分块策略（Chunking）

| 策略 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **固定长度** | 按固定字符/Token 滑动窗口 | 实现最简单，块大小可控 | 极易在句中切断语义；表格与代码块会断裂 |
| **递归/结构感知** | 按 Markdown/HTML 语法树、标题层级、段落边界切 | 保留语义完整性，标题天然携带上下文 | 块大小不均匀；超长小节会溢出 |
| **语义分块** | 按相邻句子向量的相似度阶跃点切 | 理论最优的语义边界 | 计算开销大，块长不可控 |
| **父子块（Parent-Document）** | 小块（Child）用于精准匹配，命中后回溯取大块（Parent）喂给模型 | 兼顾匹配精度与阅读上下文 | 需双层存储与映射管理 |

**本项目的选择：结构感知分块**（`MarkdownChunker`）。
理由：中文技术笔记天生有清晰的标题层级与块结构（段落/列表/代码/表格/引用），按结构切能**零成本**获得高质量边界，且 `headingPath` 直接成为检索时的上下文补充。作为兜底，超长块仍会做二级字符切分。
**刻意没做**：语义分块（2000 切片规模下收益不抵复杂度）与父子块（在保留标题路径的前提下收益有限）。

> 📖 对照代码：`backend/mindora-knowledge/src/main/java/com/mindora/knowledge/application/MarkdownChunker.java`

### 2.2 混合检索（Hybrid Search）与 RRF

**为什么要两路？** 两种检索的失效模式刚好互补：

| 检索方式 | 擅长 | 不擅长 |
| --- | --- | --- |
| **稠密向量**（语义） | 同义改写、口语化提问、"意思相近但用词不同" | 精确的专有名词、错误码、代码标识符、版本号 |
| **关键词/倒排**（BM25） | 字面精确命中，术语、API 名、报错信息 | 理解语义，"换个说法就搜不到" |

**怎么合并两路结果？** 候选的**排名**是可比的，但**分数不可比**——余弦相似度落在 `[-1, 1]`，BM25 是**无上界**的正数，强行加权必须先归一化，而且权重极难调。

**RRF（Reciprocal Rank Fusion，倒数排序融合）** 绕开了这个问题：

```
RRF_score(d) = Σ_over_retrievers  1 / (k + rank_i(d))
```

- 只看**排名**，不看**分数** → 天然跨量纲，**开箱即用、免调参**；
- `k`（通用取值 **60**）用来压低"某一路排第一"的过度优势，让两路都能贡献候选。原始论文：[Cormack et al., SIGIR 2009](https://dl.acm.org/doi/10.1145/1571941.1572114)。

**其他融合方式**：
- **线性加权**：需 Min-Max 归一化 + 人工调权重（要调参，效果未必更好）；
- **LTR（Learning to Rank）**：需要大量标注数据训练，个人项目不适用。

**本项目的选择：采用 RRF，k=60**。这是当前 Hybrid Search 的事实标准做法（[Qdrant 混合检索文档](https://qdrant.tech/documentation/search/hybrid-queries/)）。
一个额外的好处：RRF 能识别出**两路都命中**的切片——这通常是"更可信"的强信号，本项目把它显式暴露为 `matchedBy = both`。

> 📖 对照代码：`KnowledgeRetrievalService#fuse`

### 2.3 重排序（Rerank / Cross-Encoder）

这是**性价比最高的单点优化**，值得理解原理：

| | 双编码器（Bi-Encoder，即向量检索） | 交叉编码器（Cross-Encoder，即重排） |
| --- | --- | --- |
| 编码方式 | Query 与 Chunk **分别**编码成向量，再算余弦 | Query 与 Chunk **拼在一起**送进模型 |
| 交互能力 | 无词级交互（只在最后算个相似度） | **全注意力交互**，能判断"这个词是否真的回答了这个问题" |
| 速度 | 快，可预计算 + 向量库检索 | 慢，必须逐对实时计算 |
| 定位 | **粗筛**（从全库捞候选） | **精排**（对少量候选优中选优） |

**关键理解**：重排不是"更准的向量检索"，而是**换了一种计算范式**——它让 Query 和文档发生了真正的交互。代价是延迟（数十至数百毫秒）与算力随候选数增长，所以只能作用于**粗筛后的小候选集**。

**典型链路比例**：召回 `N₁=30` + `N₂=30` → 融合去重 → 取 `M` 条重排 → 最终 `K` 条进 Prompt。业界常见 `K=3~5`。

**本项目的选择**：`N=30`/`30`，`M=20`，`K=8`，重排模型用 `BAAI/bge-reranker-v2-m3`（经 SiliconFlow 调用）。
- `K=8` 而非 3~5：因为当前消费方是**检索调试页**（需要看一屏列表），未来的问答层会从这 8 条里再取前 3~5 条进 Prompt；
- `M=20`：给重排留出足够候选，同时把延迟控制在可接受范围。

> 📖 对照代码：`KnowledgeRetrievalService#applyRerank`、`SiliconFlowRerankAdapter`

### 2.4 查询改写类技术（Query Transformation）

| 技术 | 思路 | 解决什么 | 代价 |
| --- | --- | --- | --- |
| **HyDE** | 先让 LLM 编一段"假想答案"，再用**这段答案**的向量去检索（[Gao et al., 2022](https://arxiv.org/abs/2212.10496)） | 问题太短，与答案文档在向量空间不对称 | 每次检索多一次 LLM 调用（+1~3 秒） |
| **Multi-Query** | 让 LLM 把问题改写成 3~5 个近义问法，各自检索后聚合 | 用户表述狭隘、词汇不重合 | 同上，且要再做一次融合 |
| **Query Decomposition** | 把多跳复杂问题拆成子问题串行检索 | "A 和 B 在 C 方面有什么差异"这类复合问题 | 实现复杂、延迟叠加 |

**本项目的选择：暂不引入。**
理由：个人知识库的提问通常短而精准；且已有**混合检索 + 重排**覆盖了大部分语义鸿沟。引入 LLM 改写会显著增加延迟，收益不确定。**这是一个"以后用评测数据说话再决定"的待选项**，不是永久拒绝。

### 2.5 给切片补上下文

| 方案 | 做法 | 成本 | 效果 |
| --- | --- | --- | --- |
| **标题路径**（本项目采用） | 切片携带所属标题层级，如 `Gitee > 部署` | **零模型成本**（切块时天然得到） | 解决"这个段落属于哪个主题" |
| **Contextual Retrieval**（[Anthropic, 2024](https://www.anthropic.com/engineering/contextual-retrieval)） | 建库时用 LLM 为每个切片生成背景前缀，补齐代词与前置概念 | 入库一次性 LLM 算力 | 更彻底，能解决"该特性提升了 30%"里"该特性"指什么的指代问题 |

**本项目的选择：先做标题路径；Contextual Retrieval 列为进阶可选项。**
理由：标题路径零成本且已覆盖大部分场景；Contextual Retrieval 对 2000 切片的总成本其实很低（一次性），**若后续评测发现指代类问题突出，这是值得优先尝试的高阶优化**。

### 2.6 评测：怎么知道检索变好了？

**这是最容易被跳过、却最重要的一环。** RAG 评测通常拆成两段：

- **检索侧**：找得全不全、准不准
- **生成侧**：答得忠不忠实、切不切题

主流框架：[Ragas](https://docs.ragas.io/)（Context Precision、Faithfulness 等指标）、[TruLens](https://www.trulens.org/getting_started/core_concepts/rag_triad/)（**RAG Triad**：上下文相关度、立足度、回答相关度）。

**检索侧核心指标**（参考 [Stanford IR 教材](https://nlp.stanford.edu/IR-book/pdf/08eval.pdf)、[TREC 度量](https://trec.nist.gov/pubs/trec16/appendices/measures.pdf)）：

| 指标 | 含义 | 适合回答 |
| --- | --- | --- |
| **Hit@K**（命中率） | 前 K 条里是否**至少有一条**相关（0/1） | "检索到底有没有找到？" |
| **Recall@K** | 前 K 条覆盖了全部相关切片的比例 | "找全了吗？" |
| **MRR** | 第一条相关结果位置的倒数均值 | "相关结果排得够靠前吗？" |
| **nDCG** | 带位置衰减的多级相关度排序质量 | "整体排序质量如何？"（需要多级标注，成本高） |

**本项目的选择：先建最小可用的 Hit@K + MRR 评测集。**
做法很朴素：**人工准备 30~50 条你熟悉的真实问题，为每条标出「答案应该在哪个文档/切片」**（这就是"黄金测试集"），然后写个脚本跑检索、统计命中率。不需要引入 Ragas 之类的框架就能得到有意义的结论。
**这条尚未实现**，是 Round 3.3 之后值得优先补上的能力——**没有它，调 RRF 的 `k`、改 `M`/`K` 都只能靠感觉。**

### 2.7 引用溯源与幻觉抑制

**引用溯源（Citation）**：让答案能追溯到原文出处（文档 ID + 标题路径 + 切片编号）。

为什么生产系统**必须**有它：
1. LLM 的生成机制决定了幻觉不可避免——它是概率采样，不是事实检索；
2. 没有溯源的知识库，用户无法核实答案，也就无法信任它。

**主流做法**：切片携带元数据（本项目**已具备**：`chunkId` / `documentId` / `headingPath` / 来源路径 / 文章 slug）→ Prompt 中要求模型"依据切片编号回答并标注引用" → 生产环境可再用 NLI 小模型做句子级校验。

**幻觉抑制的工程手段**（本项目**尚未实现**，属第 5 阶段）：
1. **严格 grounding 提示词**："仅基于给定上下文回答，不得加入外部常识"；
2. **主动拒答**："上下文中没有依据时，明确回答『知识库未记录此内容』"；
3. **阈值熔断**：重排分数低于基线（如 < 0.3）时直接拒绝调用 LLM——这一点本项目已有数据基础，因为检索结果里**已经返回了 `rerankScore`**；
4. **后验校验**：生成后用廉价模型核查。

### 2.8 Agentic RAG 与 GraphRAG：何时才需要？

| 方案 | 解决什么 | 引入成本 |
| --- | --- | --- |
| **Agentic RAG** | 跨文档**多跳推理**，让 Agent 自行决定是否检索、改写、多轮工具调用 | 高：多轮 LLM 交互、状态管理与调试复杂度 |
| **[GraphRAG](https://github.com/microsoft/graphrag)** | **全库总结**类宏观问题（"这批资料的整体主题是什么"） | 很高：需抽取实体关系 + 构建社区摘要 |

**本项目的选择：都不引入。**
理由：2000 切片规模下，**"混合检索 + 重排"的线性流水线就是性价比最优解**。盲目上 Agent 只会让系统更难调试。等你真的遇到"多跳推理"或"全库总结"的具体失败案例（并能量化）时，再针对性引入也不迟——那时候你已经有评测集来证明它值得。

### 2.9 技术选型决策矩阵（针对本项目规模）

| 技术 | 解决问题 | 主要代价 | 本项目 |
| --- | --- | --- | --- |
| 结构感知分块 | 切片语义断裂、代码块被切断 | 需规则适配；块大小不均 | ✅ **已采用**（基石） |
| 混合检索（向量+关键词） | 向量漏召专有名词与代码符号 | 需维护两套检索组件 | ✅ **已采用** |
| RRF 融合 | 两路分数不可比、难调权重 | 需理解排名融合 | ✅ **已采用**（k=60） |
| 重排序（Cross-Encoder） | 双编码器无词级交互，初筛排序不准 | 额外数十~数百毫秒延迟 | ✅ **已采用** |
| 标题路径上下文 | 切片脱离所属主题 | 零成本 | ✅ **已采用** |
| 关键词索引的中文分词 | 中文无空格，词典分词有未登录词问题 | 索引体积略增 | ✅ **已采用**（CJK 二元） |
| 评测集（Hit@K / MRR） | 无法量化"变好了没" | 需人工标注 | ⏳ **待补**（第 4 阶段） |
| Contextual Retrieval | 指代与背景缺失 | 一次性 LLM 成本 | ⏳ 进阶可选 |
| HyDE / Multi-Query | 查询过短、表述狭隘 | 每次检索 +1~3 秒 | ⏸ 暂缓（等评测数据） |
| 父子块检索 | 匹配精度与上下文完整性的矛盾 | 双层存储与映射 | ⏸ 暂缓 |
| 语义分块 | 更优语义边界 | 计算开销大、块长不可控 | ⏸ 暂缓 |
| Agentic RAG / GraphRAG | 多跳推理 / 全库总结 | 索引与多轮交互成本极高 | ❌ 不需要 |

---

## 第 3 章 本项目的实现思路与架构

### 3.0 先明确一件事：本项目目前只完成了 RAG 的前四环

这句话很重要，避免你对现状产生错误预期：

| 五环 | 状态 |
| --- | --- |
| ① 切块 | ✅ 完成 |
| ② 索引 | ✅ 完成（向量 + 关键词双索引） |
| ③ 召回 | ✅ 完成（双路） |
| ④ 重排 | ✅ 完成（RRF 融合 + 交叉编码器重排） |
| ⑤ **生成回答** | ❌ **尚未开始**（`mindora-rag` 模块目前仍是空脚手架） |

**所以现在能回答的是**：`POST /api/v1/admin/knowledge/retrieval/search` 返回**排序好的相关切片**（带分数与来源）。
**现在还不能**：把它变成一段自然语言答案，也不能流式输出或在答案里标注引用。

**为什么这个顺序是对的？** 因为 RAG 的效果上限由检索决定。先建好**可观测、可评测、权限正确**的检索底座，再做生成，你才能在生成效果不好时**判断到底是检索的锅还是模型的锅**。反过来做（先接 LLM），你面对的将是一个无法拆解的黑盒。

### 3.1 整体架构：两阶段数据流

```
═══════════ 写入链路（同步/重建时，离线）═══════════

  Gitee 仓库（Obsidian 笔记）      博客文章
        │                              │
        └──────────┬───────────────────┘
                   ▼
        ┌──────────────────────┐
        │ ① Markdown 解析       │  MarkdownDocumentParser
        │   提取正文与标题层级   │
        └──────────┬───────────┘
                   ▼
        ┌──────────────────────┐
        │ ② 结构感知切块        │  MarkdownChunker
        │   headingPath         │  ← 关键元数据在这一步产生
        │   blockType/codeLang  │
        │   contentHash         │
        └──────────┬───────────┘
                   ▼
        ┌──────────────────────┐
        │ ③ 落 MySQL            │  knowledge_document
        │   （唯一真实源）      │  knowledge_document_version
        └──────────┬───────────┘  knowledge_chunk  ← 正文存这里
                   │
        ┌──────────┴───────────┐
        ▼                      ▼
  ┌───────────────┐    ┌──────────────────┐
  │ ④ 向量化       │    │ ⑤ 关键词索引      │
  │ text-embedding │    │ Lucene + CJK 二元 │
  │ -v4（1024 维） │    │ 分词              │
  └───────┬───────┘    └────────┬─────────┘
          ▼                     ▼
  ┌───────────────┐    ┌──────────────────┐
  │ Qdrant        │    │ 本地 Lucene 索引  │
  │ mindora_chunk │    │ lucene/keyword   │
  │ named: dense  │    └──────────────────┘
  └───────────────┘
     （派生索引）         （派生索引）


═══════════ 检索链路（在线）═══════════

  用户问题
     │
     ├──────────────────┐
     ▼                  ▼
  向量化            （原文直接用）
     │                  │
     ▼                  ▼
  Qdrant 召回      Lucene 召回
  topN=30          keywordTopN=30
  （权限过滤）      （同一套权限过滤）
     │                  │
     └────────┬─────────┘
              ▼
      ┌───────────────┐
      │ RRF 融合       │  k=60，按排名求和
      │ 标注 matchedBy │  vector / keyword / both
      └───────┬───────┘
              ▼
      取前 fusionTopM=20
              ▼
      ┌───────────────┐
      │ 交叉编码器重排  │  bge-reranker-v2-m3
      └───────┬───────┘
              ▼
      取前 rerankTopK=8
              ▼
      ┌───────────────┐
      │ 回表 MySQL     │  补全标题/来源/正文预览
      │ + 权限复核     │  ← MySQL 是权限权威
      └───────┬───────┘
              ▼
      返回结果（含两路排名、RRF 分、重排分、来源字段）

              ⋮
        （第⑤环 生成：尚未实现）
```

### 3.2 写入链路逐环节：为什么这么设计

#### ① 解析（`MarkdownDocumentParser`）

把 Markdown 拆成"正文 + 标题结构"。**为什么要单独这一步**：切块需要知道标题层级，而标题信息一旦在切块阶段丢失，后面再也找不回来。

#### ② 切块（`MarkdownChunker`）—— 本项目的核心资产

**切块单位**是"块级"（block-level），而不是"字符级"：

| 元数据 | 含义 | 检索时的作用 |
| --- | --- | --- |
| `headingPath` | 所属标题层级，如 `Gitee > 部署` | ① 作为上下文参与重排打分；② 展示给用户，说明"这条来自哪一节" |
| `blockType` | `paragraph` / `list` / `code` / `table` / `quote` | 可解释性；未来可对代码块做特殊处理 |
| `codeLanguage` | 代码块语言 | 同上 |
| `contentHash` | 内容哈希 | 判断切片是否变化，避免无意义地重算 embedding |

**为什么按结构切而不是按字数切**：中文技术笔记的价值高度依赖结构。一个"部署步骤"列表被从中间切断，两半都失去意义；而按结构切，整个列表作为一个检索单元，语义完整。代码块同理。

**踩过的坑**（真实修复记录，值得学习）：
- 代码围栏（```）紧跟段落且中间**没有空行**时，会被误吞进上一段 → 补充了"结构化块打断"的判断；
- Setext 标题（用 `---` 下划线表示标题）与**分隔线**语法冲突 → 必须排除分隔线，否则 `---` + `---` 会被解析成一个标题叫 `---`；
- `FIXED_SIZE` 策略曾错误地让跨界切片都继承第一个章节的元数据 → 已修正。

> 这些正是"结构感知切块"的复杂度所在：**收益高，但边界情况必须靠测试兜住**。相关回归测试在 `MarkdownChunkerTest`（20 个用例）。

#### ③ 落 MySQL：**唯一真实源（Source of Truth）**

`knowledge_document` / `knowledge_document_version` / `knowledge_chunk`。

**为什么正文存 MySQL 而不存向量库**：向量库只适合做"相似度检索"，不适合承担事务、权限、版本、关联查询等职责。把正文放 MySQL，向量库只存**向量 + 检索所需的元数据**，职责清晰。
**由此得到一个重要推论**：**向量库与 Lucene 都是"派生索引"，随时可以从 MySQL 重建。** 这不是理论，本项目真的这么用过——Qdrant Cloud 免费集群因闲置被删后，重新指向本地 Qdrant 并全量重建即可恢复。

#### ④ 向量化（`OpenAiCompatibleEmbeddingAdapter`）

- 模型：阿里云百炼 `text-embedding-v4`，**1024 维**
- 协议：OpenAI 兼容接口（因此 adapter 命名为 `OpenAiCompatible*`，未来换供应商成本低）

**为什么是 1024 维**：中文语义检索的性价比区间。维度更高收益递减而存储与计算线性增长；维度更低语义区分度不足。
**为什么选云端 API 而非本地模型**：个人项目无需维护 GPU；且换供应商只需改配置（provider/model/base-url 都在 `application.yml`）。

**一个易忽略的工程细节**：embedding 调用必须设**超时**。本项目曾用 `SimpleClientHttpRequestFactory` 设置超时却**没有生效**（测试实测等满了整个时长），换成 `JdkClientHttpRequestFactory` + `HttpClient.connectTimeout` 才真正生效。**教训：超时配置必须写测试验证，不能只看代码对不对。**

#### ⑤ 关键词索引（`LuceneKeywordIndexAdapter` + `KeywordAnalyzers`）

这是本轮（Round 3.2）新增的部分，也是**技术选型最需要解释**的部分。

**为什么需要它**：向量检索对**专有名词、错误码、API 名、代码标识符**不敏感。你的笔记里充满 `headingPath`、`chunk_id`、`points/search` 这类符号——这正是关键词检索的主场。

**为什么用 Lucene 而不是数据库全文索引**：
| 方案 | 问题 |
| --- | --- |
| MySQL `LIKE '%词%'` | 无法用索引，全表扫描；且无相关度排序 |
| MySQL `FULLTEXT` | 默认不支持中文分词；相关度模型（BM25 变体）不可控；无法定制分析链 |
| **Lucene（嵌入式）** | 成熟的 BM25 实现，分析链完全可定制，**进程内运行无额外服务**，索引是纯本地派生数据 |

**为什么用 CJK 二元分词（Bigram）而不是词典分词**：

| 方案 | 原理 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **CJK Bigram**（采用） | 中文按相邻两字切分："向量数据库" → `向量`/`量数`/`数据`/`据库` | **零未登录词（OOV）问题**——新词、型号、造词都能命中 | 索引体积约 1.5~2 倍；可能产生无意义二元组 |
| 词典分词（如 SmartChinese） | 依赖词典 + HMM，"向量数据库" → `向量`/`数据库` | 词粒度自然，索引更小 | 词典保守，**对 `Rerank`、`Embedding`、`bge-reranker-v2-m3` 这类新词与型号召回弱** |
| IK Analyzer | 中文分词常用方案 | 效果好 | 上游自 2012 年起停止维护，社区分支只适配到 Lucene 8/9 |

**决策依据**：本项目语料是**中文技术笔记**，特点是**新术语多、英文混排多、代码符号多**。Bigram 的"零 OOV"特性正好命中这个特点；而索引体积翻倍对 2000 个切片来说**完全可忽略**。这是一个典型的"**根据语料特征而非参数指标做选择**"的例子。

**分析链**（`KeywordAnalyzers`）：

```
StandardTokenizer        →  非 CJK 部分按标准规则切分
  ↓
CJKWidthFilter           →  全角转半角
  ↓
LowerCaseFilter          →  统一小写
  ↓
CJKBigramFilter          →  中文二元切分（HAN|HIRAGANA|KATAKANA|HANGUL）
  ↓
WordDelimiterGraphFilter →  拆开 headingPath / chunk_id 这类标识符
  ↓
FlattenGraphFilter       →  【仅索引侧】把 token 图形展平后再入库
```

**必须理解的一个坑**：`CJKBigramFilter` 与 `WordDelimiterGraphFilter` **都会产生 token 图形（graph）**。索引侧必须用 `FlattenGraphFilter` 展平，查询侧则**不能**展平（会丢失短语查询所需的位序信息）。更关键的是——**索引侧与查询侧必须共用完全相同的分析链前缀**，否则写入与查询的词项不一致，关键词检索会**静默失效**（不报错，就是永远搜不到）。

> **工程建议**：这类"静默失效"风险，必须用**针对性单测**把分析行为固定下来。本项目的 `LuceneKeywordIndexAdapterTest` 断言了：中文无词典召回、驼峰/下划线标识符拆分、原子替换清除旧切片、权限过滤一致性。

**索引写入语义**：以**文档为粒度原子替换**（Lucene `updateDocuments` on `documentId`）。
**为什么强调原子**：重新切片时若"先删后写"之间存在窗口，或删除条件不精确，就会产生**孤儿切片**（旧切片残留在索引里，检索到却有正文缺失）。原子替换让"一篇文档要么全是新切片，要么没变"。

**为什么关键词索引是嵌入式（同进程）而不是独立服务**：2000 切片规模下，独立服务（如 Elasticsearch）带来的运维成本远超收益。**规模决定架构**，这一点在个人项目里尤其重要。

### 3.3 检索链路逐环节：为什么这么设计

#### 双路召回（`KnowledgeRetrievalService#search`）

1. **向量路**：问题 → 向量化 → Qdrant `search`，`topN=30`，带权限过滤
2. **关键词路**：问题原文 → Lucene 查询，`keywordTopN=30`，带**同一套**权限过滤
3. **RRF 融合**：`k=60`，累加 `1/(k+rank)`，标注 `matchedBy` 与两路排名
4. **截断**：取前 `fusionTopM=20` 进入重排（重排是逐条打分，必须限制候选数）
5. **重排**：`bge-reranker-v2-m3` 对 `headingPath + "\n" + content` 打分
6. **截断**：取前 `rerankTopK=8`
7. **回表补全 + 权限复核**：按 `chunkId`/`documentId` 查 MySQL，补标题、来源、正文预览

**关于第 5 步的输入细节**：重排的文本是 `headingPath + 正文`，而不是只有正文。**原因**：标题路径本身就是重要的相关性信号——用户问"怎么部署"，标题含"部署"的段落理应得分更高。

**关于第 7 步的重要性**：它不只是一个"美化输出"的步骤。见 3.4。

#### 三个容易忽略但必须做的设计

**① 权限过滤的 fail-closed（失败即拒绝）**

`scope`（`all` / `public`）**必须显式传入**，缺失直接报错，**不存在"不带过滤条件"的检索路径**。这是安全设计的基本姿势：不确定时拒绝，而不是放行。

**② MySQL 是权限的权威来源，索引不是安全边界** ⭐

这是本项目一个**重要的架构决定**，也是踩坑后的修正：

索引里的权限字段（`visibility` / `status` / `knowledgeEnabled`）是**写入时固化**的。如果一篇文档在索引后被改为私密，索引里的旧字段不会自动更新——**公开检索仍会命中它，等于泄漏私有内容。**

修复分两层：
1. **回写**：设置变更时，通过 Qdrant 的 payload 更新接口（**不需要重算 embedding**）与关键词索引重写，把新权限同步过去；
2. **复核（关键）**：检索时**按 MySQL 中的文档当前状态再做一次权限校验**。

**为什么第 2 层才是根本**：它让权限隔离**不再依赖派生索引的新鲜度**。由此得到两个好处：
- 回写失败不再是安全事故（只是索引不够新），因此可以**尽力而为**，不会让"文章发布/下线"这类业务流程依赖索引可用性；
- 索引损坏、重建期间、迁移过程中，权限始终正确。

> **一句话原则**：**派生索引用于缩小召回范围，权威数据用于最终裁决。**

**③ 降级分级（Quality vs Capability）**

并非所有失败都该同等对待：

| 失败项 | 性质 | 处理 |
| --- | --- | --- |
| 重排序不可用 | **质量特性** | 降级为融合顺序，返回 `fallback=true` + 原因 |
| 关键词索引不可用 | **质量特性** | 降级为纯向量检索，返回 `keywordFallback=true` + 原因 |
| 嵌入服务不可用 | **能力缺失** | 直接返回明确的临时不可用错误 |
| 向量库不可用 | **能力缺失** | 直接返回明确的临时不可用错误 |

**为什么这样分**：质量特性退化后**结果仍然正确，只是可能不够好**；而能力缺失意味着**核心链路断了**，此时返回"看起来正常但没有意义"的结果，比明确报错更危险。

### 3.4 关键取舍一览：为什么是 A 而不是 B

| 决策点 | 选择 | 主要理由 | 被否决的方案与原因 |
| --- | --- | --- | --- |
| 切块方式 | 结构感知（按标题/块） | 中文技术笔记结构性强，元数据零成本 | 固定长度（切断语义）、语义分块（开销大、块长不可控） |
| 检索方式 | 向量 + 关键词双路 | 两者失效模式互补 | 纯向量（专有名词/代码符号漏召） |
| 融合方式 | RRF（k=60） | 只用排名，跨量纲免调参 | 线性加权（需归一化+调权）、LTR（需标注数据） |
| 中文分词 | CJK Bigram | 零 OOV，适配新词/型号密集的技术语料 | 词典分词（新词召回弱）、IK（上游停维护） |
| 关键词引擎 | 嵌入式 Lucene | 成熟 BM25 + 可定制分析链 + 无额外服务 | MySQL LIKE（全表扫描）、FULLTEXT（中文支持弱） |
| 重排 | Cross-Encoder 重排 | 词级交互，性价比最高的单点提升 | 只调向量 Top-K（治标不治本） |
| 正文存储 | MySQL 为唯一真实源 | 事务/权限/版本/关联查询 | 正文存向量库（职责错配） |
| 权限裁决 | MySQL 复核 + fail-closed | 不依赖派生索引新鲜度 | 只信索引 payload（存在泄漏风险，实测确认） |
| 查询改写 | 暂不引入 | 个人库提问精准；已有混合+重排覆盖 | HyDE/Multi-Query（+1~3s，收益待验证） |
| Agent/GraphRAG | 不引入 | 2000 切片线性流水线性价比最优 | 多跳/全库总结成本极高 |

### 3.5 代码地图：想学哪一块就看哪个文件

| 关注点 | 文件 |
| --- | --- |
| Markdown 解析 | `backend/mindora-knowledge/src/main/java/com/mindora/knowledge/application/MarkdownDocumentParser.java` |
| **切块与元数据（核心）** | `.../application/MarkdownChunker.java` |
| 写入链路编排（同步/重建/权限同步） | `.../application/KnowledgeService.java` |
| **检索编排（双路召回/RRF/重排）** | `.../application/KnowledgeRetrievalService.java` |
| 端口定义（依赖倒置边界） | `.../application/port/KnowledgeAdapters.java` |
| 向量库适配（Qdrant） | `.../infrastructure/adapter/QdrantVectorStoreAdapter.java` |
| 关键词索引适配（Lucene） | `.../infrastructure/adapter/LuceneKeywordIndexAdapter.java` |
| **中文分词分析链** | `.../infrastructure/adapter/KeywordAnalyzers.java` |
| Embedding 适配 | `.../infrastructure/adapter/OpenAiCompatibleEmbeddingAdapter.java` |
| 重排适配 | `.../infrastructure/adapter/SiliconFlowRerankAdapter.java` |
| 检索调试接口 | `.../api/web/KnowledgeAdminController.java` |
| Spring 装配与配置项 | `backend/mindora-app/src/main/java/com/mindora/app/config/KnowledgeModuleConfiguration.java` |
| 数据表结构与迁移 | `backend/mindora-app/src/main/resources/db/migration/V4__*.sql`、`V6__stage_3_chunk_metadata.sql` |
| 配置默认值 | `backend/mindora-app/src/main/resources/application.yml` |

**建议的读码顺序**：`MarkdownChunker` → `KnowledgeService#indexVersion` → `QdrantVectorStoreAdapter` / `LuceneKeywordIndexAdapter` → `KnowledgeRetrievalService`。这条路径正好对应"写入链路 → 检索链路"。

---

## 第 4 章 当前能力边界与后续路线

### 4.1 已完成

- ✅ 结构感知切块 + 富元数据（`headingPath` / `blockType` / `codeLanguage` / `contentHash`）
- ✅ 向量索引（Qdrant，1024 维，named vector，payload 含权限与结构字段）
- ✅ 关键词索引（Lucene，CJK 二元分词，文档级原子替换）
- ✅ 混合检索 + RRF 融合（k=60，可配置）
- ✅ 交叉编码器重排（`bge-reranker-v2-m3`）
- ✅ 权限隔离（fail-closed + MySQL 权威复核 + 变更回写）
- ✅ 分点降级语义（质量特性降级 / 能力缺失报错）
- ✅ 管理端检索调试接口（返回两路排名、RRF 分、重排分、命中来源）
- ✅ 全量重建能力（关键词索引可仅凭 MySQL 重建，不消耗 embedding）

### 4.2 尚未实现（按建议优先级）

| 优先级 | 能力 | 说明 | 为什么重要 |
| --- | --- | --- | --- |
| **P0** | **检索评测集（Hit@K / MRR）** | 30~50 条人工标注问答 | **没有它，调参只能靠感觉**；也是后续所有优化的依据 |
| **P1** | **生成层（第⑤环）** | `mindora-rag` 模块：拼接上下文 → 调用 LLM → 返回答案 | 这是 RAG 的最后一块拼图 |
| **P1** | **引用溯源** | 答案中标注切片来源 | 没有溯源的知识库无法建立信任 |
| **P2** | **流式输出（SSE）** | 首字延迟体验 | 交互体感的关键 |
| **P2** | **幻觉抑制** | 严格 grounding 提示词、主动拒答、低分熔断 | 生产可用性的底线 |
| **P3** | **公开问答 + 限流** | 前台问答入口与配额 | 对外可用 |
| **P3** | Contextual Retrieval | 为切片生成背景前缀 | 解决指代类失败 |
| **P3** | HyDE / Multi-Query | 查询改写 | 需先用评测集证明收益 |

### 4.3 与业界成熟方案的差距清单

| 维度 | 本项目现状 | 业界成熟做法 | 差距评估 |
| --- | --- | --- | --- |
| 切块 | 结构感知 + 元数据 | 结构感知 / 语义分块 / 父子块 | **基本对齐**（缺超长块的系统化处理） |
| 召回 | 双路 + RRF | 双路/多路 + RRF | **对齐** |
| 重排 | Cross-Encoder | Cross-Encoder | **对齐** |
| 生成 | **无** | 受控生成 + 流式 | **主要差距** |
| 引用 | 检索层已有元数据，答案层无 | 句级引用标注 | 待补齐 |
| 评测 | **无** | Ragas / RAG Triad / 黄金集 | **主要差距** |
| 可观测 | 检索侧有 traceId、分数、耗时 | 全链路 trace + 反馈闭环 | 部分具备 |

**结论**：本项目在**检索侧已达到业界主流水平**，主要差距集中在**生成、引用、评测**三块。这个顺序是合理的——先把容易被忽略却决定上限的检索做扎实。

### 4.4 建议的下一轮拆分（供参考）

1. **建评测集**（不写业务代码）：标注 30~50 条问答 → 脚本统计 `Hit@8`、`MRR` → 得到**基线数字**；
2. **用基线驱动调参**：对比 `keyword-enabled` 开/关、`rrf-k` 不同取值、重排开/关的效果；
3. **再做生成层**：有了基线，生成效果不好时才能判断是检索还是模型的锅；
4. **最后做引用与流式**。

---

## 第 5 章 学习路径与速查表

### 5.1 术语速查（中英对照）

| 中文 | 英文 | 一句话解释 |
| --- | --- | --- |
| 检索增强生成 | RAG (Retrieval-Augmented Generation) | 先检索资料再让模型回答；本文主题 |
| 切块 | Chunking | 把长文档切成可独立检索的小块 |
| 嵌入 / 向量化 | Embedding | 把文本映射成高维向量，语义相近则向量相近 |
| 稠密检索 | Dense Retrieval | 用向量做语义检索 |
| 稀疏检索 / 倒排 | Sparse / Inverted Index | 基于词频的关键词检索（BM25） |
| 混合检索 | Hybrid Search | 同时用向量与关键词两路召回 |
| 倒数排序融合 | RRF (Reciprocal Rank Fusion) | 只用排名融合多路结果的算法 |
| 召回 | Recall / Retrieval | 从全库捞出候选 |
| 重排序 | Rerank | 对候选做精细打分重排 |
| 双编码器 | Bi-Encoder | Query 与文档分别编码（快，粗筛） |
| 交叉编码器 | Cross-Encoder | Query 与文档拼在一起编码（慢，精排） |
| 精度 / 召回率 | Precision / Recall | 找得准 / 找得全 |
| 幻觉 | Hallucination | 模型编造看似合理但不真实的内容 |
| 立足度 | Groundedness | 答案是否有上下文依据 |
| 溯源 / 引用 | Citation / Attribution | 答案标注来源 |
| 未登录词 | OOV (Out-of-Vocabulary) | 分词词典里没有的词 |
| 向量数据库 | Vector Database | 存储向量并做相似度检索的库 |

### 5.2 推荐精读顺序（由浅入深）

1. **[LlamaIndex — Understanding RAG](https://developers.llamaindex.ai/python/framework/understanding/rag)** —— 建立完整的工业级链路心智模型（最推荐先读）
2. **[RAG 综述（Gao et al., 2023）](https://arxiv.org/abs/2312.10997)** —— 三代范式与全模块技术全景
3. **[Seven Failure Points（Barnett et al., 2024）](https://arxiv.org/abs/2401.05856)** —— 生产环境的 7 个真实失败点，直接提升你的设计直觉
4. **[RRF 原始论文（Cormack et al., SIGIR 2009）](https://dl.acm.org/doi/10.1145/1571941.1572114)** —— 理解为什么"只看排名"反而更稳
5. **[Anthropic — Contextual Retrieval](https://www.anthropic.com/engineering/contextual-retrieval)** —— 切片上下文增强的工程实践
6. **[Qdrant — Hybrid Queries](https://qdrant.tech/documentation/search/hybrid-queries/)** —— 你正在用的向量库的混合检索与 RRF 实现
7. **[Ragas 指标文档](https://docs.ragas.io/)** / **[TruLens RAG Triad](https://www.trulens.org/getting_started/core_concepts/rag_triad/)** —— 需要做评测时再看

### 5.3 每写一轮代码，可以观察什么

| 你在改什么 | 该观察什么指标 | 怎么观察 |
| --- | --- | --- |
| 切块规则 | 切片总数、`headingPath` 覆盖率、块类型分布 | 管理端索引页 / 数据库查询 |
| 向量化 | 向量数与 MySQL 已索引切片数是否一致 | Qdrant `points_count` vs MySQL count |
| 关键词索引 | 索引切片数、中文/标识符检索能否命中 | 检索调试页（把 `keywordEnabled` 关掉对比） |
| RRF | 同一条结果的 `vectorRank` / `keywordRank` / `matchedBy` | 检索调试页 |
| 重排 | `rerankScore` 是否明显改变排序 | 检索调试页（重排开/关对比） |
| 权限 | 越权内容是否绝不出现 | 用 `scope=public` 查一篇 private 文档的内容 |
| 整体 | `Hit@8`、`MRR` | **待建评测集** |

**最实用的一个技巧**：检索调试页支持 `keywordEnabled=false`，**把两路分别关掉对比结果**，是理解"两路各自贡献了什么"最快的方法。

### 5.4 配置项速查

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `mindora.knowledge.retrieval.top-n` | 30 | 向量召回条数 |
| `mindora.knowledge.retrieval.keyword-top-n` | 30 | 关键词召回条数 |
| `mindora.knowledge.retrieval.fusion-top-m` | 20 | 融合后进入重排的条数 |
| `mindora.knowledge.retrieval.rerank-top-k` | 8 | 最终返回条数 |
| `mindora.knowledge.retrieval.rrf-k` | 60 | RRF 的 k 值（越大越平缓） |
| `mindora.knowledge.retrieval.rerank-enabled` | true | 是否启用重排 |
| `mindora.knowledge.retrieval.keyword-enabled` | true | 是否启用关键词召回 |
| `mindora.knowledge.retrieval.fallback-enabled` | true | 重排失败是否降级（false 则报错） |
| `mindora.knowledge.keyword.enabled` | true | 是否启用关键词索引 |
| `mindora.knowledge.keyword.index-path` | `<存储根>/lucene/keyword` | Lucene 索引目录 |

---

## 附录 A 参考资料（已核实）

> 下列条目的**标题、作者、年份与 URL 均已逐一抓取一手来源页面核实**。

### 论文

1. Lewis et al., **Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks**, 2020（NeurIPS 2020）—— RAG 奠基论文（联合训练检索器与生成器）。
   <https://arxiv.org/abs/2005.11401>
2. Gao et al., **Retrieval-Augmented Generation for Large Language Models: A Survey**, 2023 —— 朴素/高级/模块化三代范式划分的出处。
   <https://arxiv.org/abs/2312.10997>
3. Barnett et al., **Seven Failure Points When Engineering a Retrieval Augmented Generation System**, 2024 —— 生产环境七个真实失败点。
   <https://arxiv.org/abs/2401.05856>
4. Liu et al., **Lost in the Middle: How Language Models Use Long Contexts**, 2023（TACL 2023）—— 长上下文中段信息衰减现象。
   <https://arxiv.org/abs/2307.03172>
5. Cormack, Clarke, Buettcher, **Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods**, SIGIR 2009 —— RRF 原始论文。
   <https://dl.acm.org/doi/10.1145/1571941.1572114>
6. Gao, Ma, Lin, Callan, **Precise Zero-Shot Dense Retrieval without Relevance Labels**, 2022 —— HyDE。
   <https://arxiv.org/abs/2212.10496>

### 官方文档与工程博客

7. LlamaIndex, **Understanding RAG** —— 工业级链路抽象。
   <https://developers.llamaindex.ai/python/framework/understanding/rag>
8. Anthropic, **Introducing Contextual Retrieval**, 2024-09-19 —— 切片上下文增强。
   <https://www.anthropic.com/engineering/contextual-retrieval>
9. Qdrant, **Hybrid Queries** —— 混合检索与 RRF 实现（本项目所用向量库）。
   <https://qdrant.tech/documentation/search/hybrid-queries/>
10. **Ragas** 指标文档 —— Context Precision、Faithfulness 等。
    <https://docs.ragas.io/>
11. **TruLens — The RAG Triad** —— 上下文相关度 / 立足度 / 回答相关度。
    <https://www.trulens.org/getting_started/core_concepts/rag_triad/>
12. Sentence Transformers, **Cross-Encoder Usage** —— Bi-Encoder 与 Cross-Encoder 的区别。
    <https://sbert.net/docs/cross_encoder/usage/usage.html>
13. Microsoft, **GraphRAG** —— 图谱化 RAG，适用于全库总结类问题。
    <https://github.com/microsoft/graphrag>
14. Stanford, **Introduction to Information Retrieval（第 8 章 评测）** —— 检索指标定义。
    <https://nlp.stanford.edu/IR-book/pdf/08eval.pdf>
15. NIST TREC, **Common Evaluation Measures** —— 评测度量标准。
    <https://trec.nist.gov/pubs/trec16/appendices/measures.pdf>

### 本项目内部文档

16. `docs/design-04-rag-and-chat.md` —— RAG 与问答的原始设计。
17. `docs/design-09-chunk-metadata-and-vector-payload.md` —— 切片元数据与向量 payload 契约。
18. `docs/project-status-cn.md` —— 项目状态、接口行为与现场联调记录。
19. `docs/stage-3-task-manifest-cn.md` —— Stage 3 任务清单与验收证据。
20. `docs/backend-development-rules-cn.md` —— 后端包边界与开发规则。

---

*本文与实现同步维护：新增检索能力时，请同时更新第 3、4 章与配置项表。*
