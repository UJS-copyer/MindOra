# 调研：检索短板如何用成熟方案解决（Stage 3.3 检索侧）

> 本文件是**调研记录**，不是设计契约。按 `AGENTS.md`「先调研，后设计」：先列出业界已有的成熟方案、公开证据与代价，再给出选型与理由。
> 每节都附可核实来源；查不到来源的一律标注「未找到可靠来源」，不做推测性表述。详细分报告在 `logs/research-1..6-*.md`。

## 0. 要解决的问题（来自实测基线，不是猜的）

212 条黄金集、正例 202 / 负例 10，`top-8` 基线：

| 类型 | 条数 | SpanHit@8 | DocHit@8 |
| --- | --- | --- | --- |
| multi-doc | 4 | 100.0% | 100.0% |
| cross-section | 37 | 97.3% | 100.0% |
| vague | 34 | 94.1% | 100.0% |
| code | 48 | 87.5% | 97.9% |
| fact | 38 | 86.8% | 86.8% |
| **overview（整体理解）** | 36 | **72.2%** | 83.3% |
| **合计** | **202** | **88.1%** | **94.1%** |

> 上表为**修正后**口径（切片全文 + `headingPath`）。`long-block` 修正后为 100%，已并入合计；早前误报的 `72.3% / overview 52.8% / long-block 20%` 系度量缺陷所致，更正说明见 §3.1。

两个症状：**①文档基本都找得到（`DocHit@8` 94.1%），但「答案所在的那一个切片」仍会漏（`SpanHit@8` 88.1%）；②`overview`（整体理解）明显最弱（72.2% vs 合计 88.1%）。**

## 1. 方案族一：父子块 / small-to-big（检索单元 ≠ 返回单元）

**机制（已核到源码）**：LangChain `ParentDocumentRetriever` 继承 `MultiVectorRetriever`：**子块**进向量库做 embedding，**父块只进 docstore**；子块 metadata 用 `id_key`（默认 `doc_id`）挂父 id；检索 = 子块向量检索 → 按序去重父 id → `docstore.mget` 取父块。
来源：[LangChain ParentDocumentRetriever API](https://datastax.github.io/ragstack-ai/api_reference/0.4.0/langchain/retrievers/langchain.retrievers.parent_document_retriever.ParentDocumentRetriever.html)、[LangChain Open Tutorial](https://langchain-opentutorial.gitbook.io/langchain-opentutorial/10-retriever/05-parentdocumentretriever)

**两个源码级缺陷（重要）**：返回的父块**不带分数**（父块级重排无从下手）；按父 id 去重会让**返回条数少于 k**，把 top-8 的"8 个切片"塌缩成更少的父块。

**向量库原生支持**：Qdrant 有 `query_points_groups`（`group_by` / `group_size` / `limit`）配 `with_lookup` 按 **point id 等值 join** 带回父 payload，官方原文明确 "not a vector search"；限制是 `group_by` 仅支持 keyword/integer、组查询不支持 offset 分页。**命名向量不是父子机制。**
来源：[Qdrant Query Points Groups](https://qdrant.tech/documentation/concepts/search/)

**更正一处常见误解**：Weaviate 的 **multi-tenancy 与父子检索无关**（官方定义是多租户 shard 隔离），Weaviate 没有原生父子检索。

**证据边界（诚实说明）**：**未找到** `ParentDocumentRetriever` 与朴素切片检索同基准对照的公开数字。相邻技术 Anthropic Contextual Retrieval 有硬数字，但那是**入库期上下文前置**，**不可等同**。

**对本项目的判断**：**有条件推荐，且推荐"检索后扩展"形态、不引入 LangChain 组件**（Java 栈，耦合成本 > 收益）。
理由：我们的瓶颈在**切片层**（DocHit 94.1% 已很高，而 parent-document 主收益在文档/父块层），去重还会**降低答案粒度**，可能反向伤害 `overview`。**唯一真正对得上的是 `long-block`**（13249 字符长块的 embedding 被稀释，见 §5）。

## 2. 方案族二：邻居窗口扩展 / 自动合并（LlamaIndex 的两个官方实现）

**Sentence Window（参数已核到源码）**：`SentenceWindowNodeParser` 默认 `window_size=3`（每侧句数，窗口 ≤ 2N+1）、`window_metadata_key="window"`、`original_text_metadata_key="original_text"`；切句走 NLTK Punkt。`MetadataReplacementPostProcessor` 在**检索后**仅 `set_content` 替换为窗口文本（**不重算 embedding 与 score**），其 `target_metadata_key` **无默认值（必填）**。
来源：[LlamaIndex Metadata Replacement + Node Sentence Window](https://llamaindexxx.readthedocs.io/en/latest/examples/node_postprocessor/MetadataReplacementDemo.html)

**Auto-Merging（阈值已核到源码）**：`AutoMergingRetriever(..., simple_ratio_thresh=0.5)`，`ratio = 命中子节点数 / 该父节点全部子节点数`，**仅当 `ratio > 0.5`（严格大于）**才丢弃子块、返回整父块；父块得分 = 命中子块得分的平均；循环至稳定；另有相邻缺口补齐。层级默认 `[2048, 512, 128]`，叶子进向量库、其余进 docstore。

**证据偏保守（务必注意）**：官方评测里 sentence window correctness `4.367 vs 4.217`（faithfulness 持平），auto-merging `4.267 vs 4.208`、pairwise `0.525`，原文直言 **"results are roughly the same"**。独立论文 ARAGOG（[arXiv:2404.01037](https://arxiv.org/abs/2404.01037)）中 sentence window 检索精度最高（diff 0.1021, p<0.0001），但**答案相似度没跟上**，且**叠加 Cohere rerank 无显著提升（p=0.9768）**；ARAGOG 明确**未评测** auto-merging。

**对本项目的判断**：**不推荐做句级重切**（我们 251 字符均长的切片已接近该粒度，重切要重建 Qdrant+Lucene+黄金集，而收益仅小幅）。**推荐只借用思路**：重排后对 top-8 做 chunk 级邻居扩展（按 `sequence_no`，须设字符上限）+ 按 `heading_path` 做 `>0.5` 比例的父块合并。**两者均为检索期改动，无需重建索引。**

## 3. 方案族三：多粒度索引（RAPTOR / 摘要索引 / Multi-Vector）

三者同族：**新增一层「非原文」检索单元 + 用 ID 关联回原文**，把「检索单元」与「返回单元」解耦；区别只在粒度。综述归类有背书：Gao 等 RAG 综述 [arXiv:2312.10997](https://arxiv.org/abs/2312.10997) 的 Advanced RAG pre-retrieval「优化索引结构」即归属桶，Table I 把 RAPTOR 登记为 `Retrieval Granularity=Chunk` + `Recursive`。

**RAPTOR 确切数字**（[arXiv:2401.18059](https://arxiv.org/abs/2401.18059)）：collapsed tree 优于 tree traversal；SBERT+RAPTOR vs SBERT：QuALITY 56.6 vs 54.9、QASPER F1 36.70 vs 36.23；**Table 8 是「全局视图有效」的硬证据：查满 3 层 73.68 vs 只查叶层 57.9**。

**但有一条算术必须先做**：本项目 **2028 切片 / 141 篇 ≈ 14.4 切片/篇**，`top-8` 的覆盖上限 ≈ `8 / 14.4 = 55.6%`，与实测 **`overview` SpanHit 52.8% 高度吻合**。这强烈提示 `overview` 的失败**主要是 top-k 覆盖预算问题，而不是"缺少全局视图"**。

### 3.1 ✅ 已用离线实验验证（含一处度量口径更正）

> **⚠️ 度量口径更正（重要）**：先前 `SpanHit` 的实现有一个缺陷 —— 检索接口返回的是**带连字符的 UUID**，而切片接口返回**无连字符的 32 位十六进制**，两者比对失败后代码回退到接口的 `contentPreview`（**仅 300 字**）。因此先前报告的 `SpanHit@8 = 72.3%`、`overview 52.8%`、`long-block 20%` **都是被截断预览低估的数字**。修正为按切片全文判定后如下；其中 `long-block` 从 20% 变成 100%，说明「超长切片完全不可召回」是**度量假象，不是真实缺陷**。

**修正后的基线**（判定范围＝切片全文 + `headingPath`）

| 类型 | SpanHit@8 | SpanHit@20（候选池上界） | DocHit@8 |
| --- | --- | --- | --- |
| cross-section | 97.3% | 97.3% | 100.0% |
| **long-block** | **100.0%** | 100.0% | 100.0% |
| multi-doc | 100.0% | 100.0% | 100.0% |
| vague | 94.1% | 94.1% | 100.0% |
| code | 87.5% | 89.6% | 97.9% |
| fact | 86.8% | 86.8% | 86.8% |
| **overview** | **72.2%** | **80.6%** | 83.3% |
| **合计** | **88.1%** | **90.1%** | 94.1% |

**① 提高 k 的收益比原先估计小得多**：`@8 → @20` 只有 **+2.0pt**（88.1 → 90.1），而不是先前误报的 +3.9pt。`overview` 仍是「提 k 收益最大」的类型（+8.4pt），也仍是内容类里最弱的。

**② 检索后扩展（本调研推荐的第一步）经实测有效**：重排后对 top-8 命中做「邻居 ±1 + 同章节（每命中最多 4 条）」扩展，**判定口径为"答案片段是否出现在最终上下文里"**：

| 策略 | SpanHit@8 | 相对基线 |
| --- | --- | --- |
| 基线（现状） | 88.1% | — |
| + 邻居 ±1 | 90.6% | +2.5 |
| + 同章节 | 90.6% | +2.5 |
| **+ 邻居 + 同章节（不限预算）** | **91.6%** | **+3.5** |
| 文档配额去重（每文档 ≤2） | 81.2% | **−6.9（更差 → 放弃）** |
| 参照：直接把 k 提到 20 | 90.1% | +2.0 |

**关键点：合并扩展（91.6%）超过了把 k 提到 20 的上界（90.1%）** —— 有些答案切片连 top-20 都进不去，但它就躺在已召回切片的隔壁，这是提高 k 拿不到的收益。

按类型（不限预算）：`code` 87.5%→93.8%（**+6.3**）、`overview` 72.2%→77.8%（**+5.6**）、`vague` 94.1%→97.1%、`cross-section` 97.3%→100%、`fact` 不变、`long-block` 已是 100%。**收益最大的正是本来要治的 `code` 与 `overview`**；`fact` 拿不到收益（答案本来就在命中的那一条里）。

**③ 代价与预算参数（已做字符预算扫描）**

| 扩展字符上限 | SpanHit@8 | 平均新增上下文 |
| --- | --- | --- |
| 不限 | 91.6% | +6083 字符 |
| 4000 | 90.6% | +3275 字符 |
| 2500 | 90.1% | +2269 字符 |
| **1500** | **90.1%** | **+1419 字符** |
| 800 | 89.1% | +764 字符 |

**1500 字符就是拐点**：再放宽到 2500 收益为零，放到 4000 也只多 0.5pt。基线上下文平均 3026 字符，**1500 预算约等于 +47%**，是生成层可接受的代价 → **建议实现参数：每次查询新增上下文 ≤1500 字符**。

**④ 被数据否掉的方案**：文档配额去重（每文档最多 2 条）把 88.1% 拉低到 **81.2%** —— 本项目多数问题的答案恰恰是「同一文档里的多块」，强行提高文档多样性反而丢掉答案。**不实现。**

**⑤ 残余失败的性质**：不限预算仍有 8.4% 未命中；把这批未命中用例**改用答案片段本身去检索，20/20 都能召回到正确切片** → 索引与切片本身是健康的，残余失败属于**提问措辞与答案文本的语义错配**，不是索引/切分问题，靠扩展或调 k 都治不了。

**更关键的边界**：RAPTOR 的增益来自**整本书 / 整篇论文（约 5000 token 长文）**，而本项目**单篇文档平均仅约 3.6K 字符** —— 「全局视图」很可能**一条文档级摘要就够，不需要多层级树**。且 Anthropic 实测「给 chunk 加通用文档摘要增益非常有限」「摘要式索引性能低」。

**对本项目的判断**：
1. **不要**以「提升文档命中」为由引入这三个方案（DocHit 已 94.1%）；
2. **先做零索引改动的实验（已完成，见 §3.1）**：提高 k 与「检索后扩展」都已实测 —— 扩展 +3.5pt（88.1→91.6）、且优于把 k 提到 20（90.1）；文档配额去重被否掉（−6.9pt）；
3. 只有同时满足「A 提高 k + 去重后仍失败」「B 人工确认答案文字确在库内但任何单切片都不含完整答案」「C 需求确实是跨文档主题综合」三条，才考虑摘要索引（141 次 LLM 调用、增量更新最便宜），RAPTOR 放最后。

## 4. 方案族四：入库期上下文增强（Contextual Retrieval / Late Chunking）

**Contextual Retrieval（Anthropic, 2024）**：为每个 chunk 生成 50–100 token 的上下文说明再拼接后嵌入。官方数字：仅上下文嵌入把 **top-20 失败率 5.7%→3.7%（−35%）**；再加 Contextual BM25 → **2.9%（−49%）**；再叠 rerank → **1.9%（−67%）**。
来源：[Anthropic Contextual Retrieval](https://www.anthropic.com/news/contextual-retrieval)

⚠️ **不要用 67% 做预期**：本项目**已有 BM25 与 rerank 两段**，可归因的增量只落在 **35% 那一档**；且 Anthropic 用 top-20 recall 失败率，与本项目 DocHit@8/SpanHit@8 **口径不同、不可换算**。

**成本不是瓶颈**：2028 次 LLM 调用约 530 万输入 token，按 Anthropic 口径约 **$0.37**。
**但第一步应该零 LLM 成本**：把**已有的 `headingPath` + 文档标题**作为前缀拼进切片文本（数据已在 Lucene 里，近乎纯代码改动），仅当复测仍不达标，再对 19.8% 无 `headingPath` 的约 400 个切片调用 LLM（调用量降 80%）。
⚠️ 「标题路径前缀」是**合理外推**，Anthropic 原文**未单独测过**，不得当作有来源背书。

**Late Chunking（Jina）在本项目被堵死（硬结论）**：Jina 官方兼容性自检清单明确写「只返回单个 embedding 的 web API 大概率不支持 late chunking」；百炼 `text-embedding-v4` 官方契约的请求体只有 `model`/`input`/`dimensions`/`encoding_format`，响应为每个 input 一个向量，**无 token 级字段**。要走这条路必须换模型并全量重建索引，BEIR 增益多为个位数 nDCG@10 → **不推荐**。

**注意**：Contextual Retrieval 改的是**入库文本**，因此**需要重建向量与 BM25 索引**。

## 5. 方案族五：切片尺寸与超长切片（含一个此前未识别的问题）

**🔴 最高优先级新发现**：`text-embedding-v4`（阿里百炼）官方文档写明**单行最长 8192 token**。本项目最长切片 **13,249 字符**，按中文换算（1 汉字 ≈ 0.67–1.0 token）≈ **8,880–13,249 token，很可能已触及或超过模型输入上限**。超限时服务端是截断还是报错，官方该页**未说明**，**必须实测**。
这比"调 chunkSize"优先级更高，且它可能正是 `long-block` SpanHit 20% 的直接原因。

**Chroma《Evaluating Chunking Strategies for Retrieval》(2024-07-03) 确切数字**：`Recursive 200/overlap 0` → recall 88.1 / precision 7.0 / IoU 6.9；`Recursive 400/0` → recall 89.5；**OpenAI 默认 `800/400` 被点名"召回略低于平均、其余所有指标最低"**；`Cluster 200` precision 8.0 / IoU 8.0 最高；`LLM(GPT-4o)` recall 91.9 最高。原文："策略间 recall 差距可达 9%"；overlap 越大 IoU 越差。**局限：只测到 800 token，未覆盖超长块。**
来源：[Chroma Technical Report: Evaluating Chunking Strategies](https://research.trychroma.com/evaluating-chunking)

**没有普适最优块大小**：NVIDIA（2025-06-18）在 128/256/512/1024/2048 token 上实测，同一类金融文档内就得到三个不同最优值（1024 / 512 / 页级），128 与 2048 两端都变差；NAACL 2025 Findings（Vectara）结论是**定长切分更可靠、语义切分收益不足以抵偿算力**。→ **参数必须在本项目黄金集上 A/B，不能照搬业界数值。**

**长文本 embedding 稀释的机制有一手依据**：*Length-Induced Embedding Collapse*（ACL 2025, [arXiv:2410.24200](https://arxiv.org/abs/2410.24200)）—— self-attention 在 PLM 中充当低通滤波器，文本越长滤波越强，embedding 保留更多低频成分、token 特征趋于相似，最终**聚团塌缩**。最贴近本项目故障的量化指标是 EDI（**未同行评审的 2026 预印本**，已标注）：>4k token 切片收益最大，与本项目 `long-block` SpanHit 20% / DocHit 100% 高度吻合。

**对本项目参数的对照结论**：
- `chunkSize=800` 字符 ≈ **530–800 token（偏大但不极端）**；
- `maximumSize=1600` ≈ **1,070–1,600 token**，正落在 NVIDIA 观测到下滑的区间；
- **`overlap=120/800 = 15%` 恰好是 NVIDIA 实测最优比例（10–20% 区间中段）→ 反而不该动**；
- **真正可疑的是 `minimumSize=40` 字符（≈27–33 token）** —— 语料 30.3% 的切片不足 50 字符由此而来，而所有公开评测的最小块都在 **100 token** 量级。

## 6. 方案族六：生成层（下一阶段的直接依据）

**上下文顺序**：*Lost in the Middle*（[arXiv:2307.03172](https://arxiv.org/abs/2307.03172)）确切结论是 **U 形曲线** —— 相关信息在**开头或结尾**表现最好、在**中间显著下降**；GPT-3.5-Turbo 在 20 篇上下文里把答案放中间时，表现**低于不给任何文档的 closed-book（56.1%）**。论文自身建议「重排（把相关内容推到更靠前）+ 排序截断（该少给就少给）」。

**引用契约有现成标准可抄**：Anthropic Citations 的 `citations[]`（`cited_text` / `document_index` / `document_title` / `start_char_index` / `end_char_index` / `type`）；OpenAI Responses `file_search` 的 `annotations[]`（`{type:"file_citation", index, file_id, filename}`）。**跨厂商统一 JSON Schema 标准：未找到可靠来源。**

**拒答有公开评测支撑**：GopherCite（[arXiv:2203.11147](https://arxiv.org/abs/2203.11147)）用「带已验证引文作答 + 不确定即弃答」，人工评测高质量率 **80% → 90%**（弃答最不确定的三分之一后）。但 AbstentionBench（[arXiv:2506.09038](https://arxiv.org/abs/2506.09038)，20 数据集/20 模型）显示：**扩大规模无益、推理微调平均让弃答能力下降 24%**，精心设计的 system prompt 有提升但**不解决根本问题** → 除提示词外**必须叠加服务端确定性校验**。

**片段数量**：20→50 篇仅 +1.5%（GPT-3.5）/ +1%（Claude-1.3），reader 性能**远早于 recall 饱和**；*The Power of Noise*（SIGIR 2024）证明「高分但无关」的片段有**负作用**；Dify 默认 `TopK=3 / Score Threshold=0.5`。**统一最优 k：无公开结论。**

**可参考的开源实现**：RAGFlow（核心特性即 grounded citations）、Dify（Citation and Attribution 开关 + TopK/Score Threshold）、AnythingLLM（Document similarity threshold）。**Verba 已归档停止维护，不建议参考。**

**对本项目的直接含义**：拒答做成**三段式** —— 提示词层（模型自述不可答）→ **校验层（服务端确定性校验：chunkId 必须在本次 top-8 内、quote 必须能在切片原文匹配、headingPath 必须存在）** → 兜底层（全部校验失败即统一拒答话术）。因为重排分已实测不可作门禁（正例最低 0.0009 vs 负例最高 0.8324），**不要重新引入任何分数阈值当作拒答门禁**。

## 7. 结论与选型（含"为什么不选"）

| 方案 | 公开证据强度 | 是否需要重建索引 | 本项目结论 |
| --- | --- | --- | --- |
| 检索后邻居/同章节扩展（借 auto-merging / sentence-window 思路，自研实现） | **已实测有效**：+3.5pt（88.1→91.6），1500 字符预算下 +2.0pt | **否** | **推荐（第一步，参数已定）** |
| 提高 k | **已实测**：k=20 → 90.1%（+2.0pt，见 §3.1） | **否** | 可用，但**不如扩展**（扩展 91.6% > 90.1%） |
| 文档配额去重 | **已实测为负收益**：81.2%（−6.9pt） | 否 | **不做** |
| Contextual Retrieval 的"零成本版"（headingPath 前缀） | 属合理外推，无直接来源 | 是 | 第二步（成本极低） |
| 超长切片子块化（>2000 字符） | 稀释机制有 ACL 2025 一手依据，但**本项目该类实测 SpanHit 100%** | 仅局部 | **不做**（原担心的问题不存在：全部切片 `index_status=indexed`，超长切片也成功嵌入且能召回） |
| Contextual Retrieval 完整版（LLM 补上下文） | 有硬数字，但增量只能归因 −35% 档 | 是 | 有条件，仅对无 headingPath 的约 400 切片 |
| 摘要索引 / 文档摘要 | Anthropic 实测增益非常有限 | 是 | 仅在 top-k 实验后仍失败时考虑 |
| RAPTOR | 数字确切，但增益来自书级长文 | 是（全库） | **暂不做**（单篇仅 3.6K 字符，一条文档摘要可能就够） |
| Multi-Vector Retriever | 与摘要索引收益重叠、存储放大 | 是 | 不单独做 |
| Late Chunking | 增益个位数，且需换模型 | 是（全量） | **不做**（本项目 embedding API 不支持 token 级输出） |
| 重排分阈值熔断拒答 | 已被本项目数据证伪 | — | **不做**（改为生成层三段式拒答） |
| 句级重切分 | 官方评测 "roughly the same" | 是 | **不做** |

**推荐执行顺序（每一步都可被黄金集量化，且前两步零索引改动）**：

1. **口径先冻结**：扩展后"返回单元"从切片变为切片组，必须**先定义新的命中口径**（例如「答案片段是否出现在最终装配的上下文里」），否则指标不可比；
2. **零改动实验（已完成）**：k 与扩展、配额去重都已实测（见 §3.1）。结论：**扩展 > 提 k > 现状**，配额去重为负收益；
3. **检索后扩展（已验证，待实现）**：重排后、截断前，按 `sequence_no ± 1` + 同 `heading_path`（每命中 ≤4 条）补充上下文，**每次查询新增 ≤1500 字符**（拐点值）；
4. ~~实测 8192 token 上限行为，再决定超长块是否子块化~~ → **已核实无需**：全部 2028 切片 `index_status=indexed` 且有向量点，`long-block` 实测 SpanHit 100%；
5. **入库期零成本上下文**：`headingPath` 前缀（需重建）；
6. **生成层**：三段式拒答 + `citations[]` + 按 Lost-in-the-Middle 组织上下文顺序。

## 8. 明确「未找到可靠来源」的项（不得据以决策）

- `ParentDocumentRetriever` 与朴素切片检索的公开同基准对照数字；
- RAPTOR Appendix A 的 token 开销 / 构建时间 / 压缩比具体数值；
- RAPTOR 的增量更新方案；
- 摘要索引与 Multi-Vector 的官方成本数字；
- 针对**中文语料**的块大小公开评测；
- 同行评审来源给出的「overlap 应为 X%」普适结论；
- 超大**中文** chunk 的定量阈值；
- `text-embedding-v4` 超限时截断还是报错的官方说明（**需实测**）；
- 重排与上下文扩展的先后顺序的公开对照结论；
- 跨厂商统一的引用 JSON Schema 标准；
- Jina Embeddings 每百万 token 单价、百炼生成模型逐项单价、两者的中立第三方对比评测。
