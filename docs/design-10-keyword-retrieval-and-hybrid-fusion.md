# design-10 关键词检索与 Hybrid/RRF 融合契约

> 阶段：Stage 3.2（关键词检索与 Hybrid/RRF 融合）
> 关联：`design-04-rag-and-chat.md`（RAG 与问答总体设计）、`design-09-chunk-metadata-and-vector-payload.md`（切片元数据与向量 payload）
> 教学背景与选型理由见 `rag-guide-cn.md`

---

## 1. 目标与范围

Stage 3.1 交付了「向量检索 + 重排」的单路检索底座。Stage 3.2 在此之上补齐**关键词召回**，并把两路结果用 **RRF** 融合，形成业界标配的 Hybrid Search 链路。

**本阶段交付**：

1. 进程内嵌入式 Lucene 关键词索引（写入 / 删除 / 全量重建）；
2. 双路召回 + RRF 融合 + 融合后重排的检索编排；
3. 「命中来源」与两路排名的可观测字段（用于解释每条结果为何被召回）；
4. 关键词索引的运行与恢复入口；
5. 修复一处**权限泄漏缺陷**（详见 `project-status-cn.md` 缺陷记录）。

**不在本阶段范围**：生成层（LLM 作答）、引用标注、流式输出、公开问答入口、评测集。

---

## 2. 关键词索引契约

### 2.1 定位

Lucene 索引是 **MySQL 切片的派生数据**，不承担真实源职责：

- 损坏、丢失、或首次引入（历史数据尚无索引）都可经「全量重建」从 MySQL 恢复；
- 重建**不调用 embedding**，因而不消耗模型额度；
- 向量库与关键词索引彼此独立：一方不可用不阻塞另一方。

### 2.2 依赖与版本

| 项 | 取值 | 说明 |
| --- | --- | --- |
| Lucene | `9.12.3` | 统一版本属性 `lucene.version`（`backend/pom.xml`） |
| 依赖模块 | `lucene-core`、`lucene-analysis-common` | `CJKBigramFilter` / `WordDelimiterGraphFilter` / `FlattenGraphFilter` 均在 `analysis-common` |
| 引入模块 | `mindora-knowledge` | 与向量库适配器同层 |

> 注意：**不存在 `lucene-analysis-cjk` 制品**（9.12.3 实测无此 artifact），中文分析器在 `lucene-analysis-common` 的 `org.apache.lucene.analysis.cjk` 包内。

### 2.3 分析链

索引侧与查询侧**共用同一前缀链路**，索引侧额外追加展平：

```
StandardTokenizer           非 CJK 按标准规则切分
  → CJKWidthFilter          全角转半角
  → LowerCaseFilter         统一小写
  → CJKBigramFilter         中文二元切分（HAN|HIRAGANA|KATAKANA|HANGUL）
  → WordDelimiterGraphFilter 拆分 headingPath / chunk_id 类标识符
  → FlattenGraphFilter      【仅索引侧】展平 token 图形
```

**关键约束**：`CJKBigramFilter` 与 `WordDelimiterGraphFilter` 都会产生 token 图形。索引侧必须用 `FlattenGraphFilter` 展平；查询侧不得展平（会丢失短语查询所需位序）。**若两侧链路不一致，写入与查询的词项不匹配，关键词检索会静默失效**——因此该行为由 `LuceneKeywordIndexAdapterTest` 固定。

### 2.4 索引字段

| 字段 | 类型 | 存储 | 用途 |
| --- | --- | --- | --- |
| `chunkId` | StringField | 是 | 回表定位切片；命中结果的标识 |
| `documentId` | StringField | 是 | **原子替换的 Term**（见 2.5） |
| `visibility` | StringField | 否 | 权限过滤 |
| `status` | StringField | 否 | 权限过滤 |
| `knowledgeEnabled` | StringField | 否 | 权限过滤 |
| `title` | TextField | 否 | 参与匹配（标题相关性强） |
| `headingPath` | TextField | 否 | 参与匹配（章节名相关性强） |
| `content` | TextField | 否 | 参与匹配（主体） |

正文不落索引存储（`Store.NO`）：展示字段一律回 MySQL 取，避免双份正文与陈旧副本。

### 2.5 写入语义

- **以文档为粒度原子替换**：`IndexWriter.updateDocuments(Term(documentId), docs)`；
- 空切片列表等价于删除该文档；
- 每次写操作后 `commit()` 并 `maybeRefresh()`，保证**写入即可见**。

**为什么必须原子**：重新切片若「先删后写」之间存在窗口、或删除条件不精确，会产生**孤儿切片**（索引里有、正文已不存在）。

### 2.6 检索语义

- 匹配字段：`content`、`title`、`headingPath`，子句之间为 `SHOULD`（提高召回，交由重排收敛）；
- 权限过滤：每个过滤项翻译为 `TermQuery` + `Occur.FILTER`（AND 语义），**与向量侧使用同一套取值**；
- 返回：`chunkId`、`documentId`、BM25 分数。

### 2.7 运行约束（重要）

Lucene 采用**单写者文件锁**：同一索引目录同一时刻只能被一个进程打开。

- 本地开发时**不能同时运行两个后端实例**，否则第二个实例启动即失败：
  `Keyword index cannot be opened: Lock held by another program: .../lucene/keyword/write.lock`
- 该约束是嵌入式单写索引的固有特性。若将来需要多实例部署，必须改为远程/共享索引，或改写入模型（例如由单一索引服务承担写）。

---

## 3. 混合检索契约

### 3.1 链路

```
问题 ─┬─ 向量化 → Qdrant 召回 topN
      └─ 原文   → Lucene 召回 keywordTopN
                     ↓
              RRF 融合（k=60，按排名）
                     ↓
              取前 fusionTopM
                     ↓
          交叉编码器重排（bge-reranker-v2-m3）
                     ↓
              取前 rerankTopK
                     ↓
        回表 MySQL 补全展示字段 + 权限复核
```

### 3.2 RRF 定义

```
RRF_score(d) = Σ_paths 1 / (k + rank_path(d))
```

- 只用**排名**不用**分数**：BM25 与余弦相似度量纲不同，无需归一化，也无需调权重；
- `k` 默认 **60**（通用取值），用于压低单路排名靠前的过度优势；
- 并列时依次按「向量排名升序 → chunkId 字典序」确定顺序，保证结果稳定可复现。

### 3.3 递归与截断

| 阶段 | 默认 | 配置 |
| --- | --- | --- |
| 向量召回 | 30 | `mindora.knowledge.retrieval.top-n` |
| 关键词召回 | 30 | `mindora.knowledge.retrieval.keyword-top-n` |
| 融合后进入重排 | 20 | `mindora.knowledge.retrieval.fusion-top-m` |
| 最终返回 | 8 | `mindora.knowledge.retrieval.rerank-top-k` |
| RRF k | 60 | `mindora.knowledge.retrieval.rrf-k` |

重排为逐条打分，必须限制候选数，故先按融合顺序截断到 `fusionTopM`。

### 3.4 命中来源标记

每条结果携带：

| 字段 | 含义 |
| --- | --- |
| `matchedBy` | `vector` / `keyword` / `both`（两路都命中通常更可信） |
| `vectorRank` | 向量召回中的 1-based 排名（未命中为 `null`） |
| `keywordRank` | 关键词召回中的 1-based 排名（未命中为 `null`） |
| `rrfScore` | 融合分 |
| `vectorScore` | **可为 `null`**（仅关键词命中时无向量分） |

这些字段是「可解释性」的基础：能回答「这条为什么被召回」「关掉关键词会少什么」。

---

## 4. 权限与安全边界

### 4.1 fail-closed

`scope`（`all` / `public`）必填，缺失直接报错；**不存在不带过滤条件的检索路径**。

### 4.2 两路共用同一套过滤

向量过滤与关键词过滤由同一方法生成（`status=active`、`knowledgeEnabled=true`，`public` 额外 `visibility=public`），并有测试断言两侧过滤条件**相等**。否则融合会绕过隔离。

### 4.3 MySQL 是权限权威来源

索引中的权限字段是**写入时固化**的：文档可见性/知识库开关变更后，索引不会自动更新。

两层处理：

1. **回写**：设置变更时经 `VectorStorePort.updatePayload`（Qdrant payload 更新，**不需重算 embedding**）与关键词索引重写同步；
2. **复核（根本）**：检索时按 **MySQL 中的文档当前状态**再次校验访问许可。

**由此得到的重要结论**：**派生索引用于缩小召回范围，权威数据用于最终裁决。**
所以回写失败不是安全事故（只是索引不够新），可以尽力而为——这避免了「文章发布/下线」这类业务流程依赖索引可用性。

---

## 5. 降级与失败语义

| 失败项 | 性质 | 处理 | 观测字段 |
| --- | --- | --- | --- |
| 重排序不可用 | 质量特性 | 降级为融合顺序 | `fallback` / `fallbackReason` |
| 关键词检索不可用 | 质量特性 | 降级为纯向量检索 | `keywordFallback` / `keywordFallbackReason` |
| 嵌入服务不可用 | 能力缺失 | 明确报错 | `retrieval_embedding_unavailable` |
| 向量库不可用 | 能力缺失 | 明确报错 | `retrieval_vector_unavailable` |
| 关键词索引未启用 | 配置 | 写入跳过、检索降级 | — |

`fallback-enabled=false` 时重排失败改为报错（`retrieval_rerank_unavailable`），用于需要严格语义的场景。

---

## 6. 接口契约

### 6.1 检索

`POST /api/v1/admin/knowledge/retrieval/search`

请求（新增 `keywordEnabled`）：

```json
{ "query": "…", "scope": "all|public", "topN": 30, "rerankTopK": 8,
  "rerankEnabled": true, "keywordEnabled": true }
```

响应新增：`keywordTopN`、`fusionTopM`、`keywordEnabled`、`vectorCandidates`、`keywordCandidates`、`keywordFallback`、`keywordFallbackReason`；`results[]` 新增 `rrfScore`、`vectorRank`、`keywordRank`、`matchedBy`，`vectorScore` 改为可空。

### 6.2 关键词索引重建

`POST /api/v1/admin/knowledge/keyword-index/rebuild` → 返回写入切片数（整数）。

以 MySQL 当前切片为准重建整个关键词索引，不调用 embedding。

---

## 7. 配置项汇总

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `mindora.knowledge.keyword.enabled` | true | 是否启用关键词索引 |
| `mindora.knowledge.keyword.index-path` | `<存储根>/lucene/keyword` | 索引目录 |
| `mindora.knowledge.retrieval.keyword-enabled` | true | 是否启用关键词召回（可被请求级覆盖） |
| `mindora.knowledge.retrieval.keyword-top-n` | 30 | 关键词召回条数 |
| `mindora.knowledge.retrieval.fusion-top-m` | 20 | 融合后进入重排的条数 |
| `mindora.knowledge.retrieval.rrf-k` | 60 | RRF 的 k |

---

## 8. 验证证据（2026-09-27 现场联调）

- 关键词索引重建：**2195** 切片 / 3.9s，与 MySQL 有效切片数、Qdrant 点数**三者一致**；
- 后端重启后索引仍可用（结果逐条一致），确认索引持久化；
- 双路 30+30 候选；存在**仅关键词命中**的结果（`matchedBy=keyword`，`vectorRank=null`）；
- RRF 分值抽样核算与公式**逐条吻合**（如 vRank=8、kRank=1 → `1/68 + 1/61 = 0.031099`）；
- 权限隔离：库中仅 1 篇 public 文档时，`scope=public` 只返回该文档且关键词候选为 0，`scope=all` 同样查询返回私有内容——证明是过滤生效而非无匹配；
- 检索耗时：冷启动 ~900ms（Lucene 首次打开），预热后 ~400ms；
- 质量信号：无对应内容时全部结果 rerank 分落在 0.0001~0.0021，真实命中为 0.80~0.99 —— 可作为后续「拒答」阈值依据。

---

## 9. 已知限制与未做项

| 项 | 说明 | 计划 |
| --- | --- | --- |
| 单实例写锁 | 见 2.7，多实例部署前必须解决 | 部署阶段评估 |
| 未建检索评测集 | `Hit@K` / `MRR` 无法量化，调参仍依赖人工观察 | 下一阶段优先 |
| 未做查询改写 | HyDE / Multi-Query / 拆解均未引入 | 待评测数据支撑后评估 |
| 未做上下文增强 | Contextual Retrieval（切片背景前缀）未引入 | 进阶可选 |
| 关键词侧无同义词 | 同义不同词完全依赖向量侧 | 暂不需要 |
| 语料残留瑕疵 | 线上语料仍存在极少数单字符 `-` 切片 | 已记录，暂不修 |
| 生成层缺失 | 第⑤环（LLM 作答）未实现 | 下一阶段 |
