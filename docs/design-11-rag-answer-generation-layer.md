# 设计 11：RAG 问答生成层（单次问答 + 引用定位）

> 实现计划见 `docs/archive/2026-10-stage-3.3-rag-generation-layer-plan-cn.md`（已归档，执行完毕）；RAG 入门与本项目检索链路见 `docs/rag-guide-cn.md`；
> 检索侧契约见 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`。
> 本文档是**契约**：接口形状、分层职责、拒答/降级语义、配置项与实测数据。

## 1. 目标与范围

**本期交付**：单次问答闭环——用户提问 → 检索 → 上下文组装（含检索后扩展）→ 对话模型作答 → **服务端确定性校验引用** → 返回答案 + 可定位回原文的引用；知识库无相关内容时明确拒答。

**Stage 3.4 已把下列扩展位落地**（契约见 §10–§13）：

| 能力 | 状态 | 契约位置 |
| --- | --- | --- |
| SSE 流式输出 | **已交付**（`ChatOptions.streamConsumer` 槽位已兑现，端口签名未变） | §12 |
| 公开问答端点 + 限流 + 密钥兜底 | **已交付**（`PublicRagController`，scope 服务端硬编码） | §10、§11 |
| 多轮对话 | **已交付**（Stage 3.5，会话记忆 + 落库，见 §10） | §10 |
| 换检索实现 / 加查询改写 | 端口已就绪（`RetrievalFacadePort`） | §3 |

**明确不做**：RAPTOR/摘要索引、句级重切、Late Chunking（调研已否定，见 `docs/rag-retrieval-improvement-survey-cn.md`）；**真伴读（锁定本文检索）推迟到公开篇数 ≥ 20**——检索契约目前只有 `scope`，没有 `documentId` 过滤（design-10 §6.1）。公开站聊天页已交付（Stage 3.4 T5）。

## 2. 接口契约

```http
POST /api/v1/admin/rag/answer
Authorization: Bearer <SUPER_ADMIN>
Content-Type: application/json

{
  "query": "Redis 的常见缓存问题和解决方案？",
  "scope": "all",          // all | public；缺省 all（admin 调试）。公开端点必须固定 public
  "topK": 8,               // 可选，缺省用服务端配置的 rerankTopK
  "rerankEnabled": true,   // 可选
  "keywordEnabled": true,  // 可选
  "expansion": true,       // 可选，检索后扩展开关（缺省取服务端配置）
  "conversationId": null   // 多轮记忆（Stage 3.5 已启用）：登录用户带此 id 续聊；
                           // 匿名/非法值按无会话处理（不报错）
}
```

```json
200 OK
{
  "code": "success",
  "data": {
    "answer": "……[1][2]",
    "citations": [
      {
        "index": 1,
        "chunkId": "0eb75e5fd1d74c579efe1aa3f0d92e0f",
        "documentId": "7551bbfe1aba4655bbad7b047b793d7e",
        "documentTitle": "个人知识库开发记录",
        "headingPath": "项目配置信息",
        "sourcePath": "工作/xxx.md",
        "articleSlug": null,
        "sequence": 12,
        "quote": "从该切片原文逐字复制的一段",
        "quoteVerified": true
      }
    ],
    "retrieval": {
      "latencyMs": 412, "vectorCandidates": 30, "keywordCandidates": 30,
      "matchedTopK": 8, "expansionApplied": true, "contextChars": 2987
    },
    "refused": false,
    "refusalReason": null,
    "refusalDetail": null,
    "degraded": false,
    "degradedReason": null,
    "chatModel": "Qwen/Qwen3-30B-A3B-Instruct-2507"
  }
}
```

### 2.1 引用定位语义

- `citation.chunkId` / `documentId` 统一为 **32 位无连字符** 形式（`PublicIds.toPublicId`），与 `GET /api/v1/admin/knowledge/documents/{id}/chunks` 一致，前端可直接用它拉原文并定位到 `sequence` 所在切片。
- **ID 方言**必须显式归一化：检索命中给的是带连字符 36 位 UUID、切片接口给的是无连字符 32 位，二者混用会导致「按引用查不到切片」——这个坑在评测脚本上出现过一次，生成层通过 `RetrievalFacadePort.normalizeId` 统一。
- **`articleSlug` 来源（2026-10-14 契约变更）**：由 `knowledge_document.article_slug` **持久化字段**提供（V7 迁移起），不再从 `sourceType==BLOG && sourcePath 前缀 blog:` 派生。文档发布为公开文章时由发布链路写入（GITEE 与 BLOG 来源统一），发布后向量 payload 局部刷新；未关联文章时仍为 `null`。背景与证据见 `docs/stage-3.4-task-manifest-cn.md` §11.8-F1 订正。

### 2.2 `quote` 与 `quoteVerified`（已被 §15.1 取代，保留作历史）

- `quote` **只可能是知识库原文**：模型逐字给出并通过核验时原样回传；否则回传服务端从该切片原文截取的摘录。
- `quoteVerified=true` 表示这段 quote 由模型指认且逐字核验通过；`false` 表示是服务端摘录（仍来自原文，但不是模型指认的那一段）。前端可用它决定是否展示「引用原文」标记。

## 3. 分层与职责

```
mindora-rag
├── api/web          RagAdminController                 HTTP 契约与视图映射
├── application
│   ├── RagAnswerService                                编排：检索→组装→模型→校验→产出
│   ├── ContextAssembler                                全文补全→扩展→预算→排序→编号
│   ├── AnswerCitationValidator                          解析模型输出 + 逐条引用校验
│   ├── RagPrompt                                        严格 grounding 提示词
│   └── port/RagAdapters                                 ChatClientPort / RetrievalFacadePort
├── domain           ContextPolicy / Citation / RagAnswerRequest / RagAnswerOutcome / RefusalReason
└── infrastructure/adapter
    ├── OpenAiCompatibleChatAdapter                      OpenAI 兼容 /chat/completions
    ├── KnowledgeRetrievalFacadeAdapter                  对 knowledge 的窄封装（只经应用服务）
    └── UnavailableRagAdapters                           未配置模型时的降级实现
```

依赖方向 `api.web → application → domain`；端口在 `application.port`，适配器在 `infrastructure.adapter`；`mindora-rag` 访问 `mindora-knowledge` **只经** `KnowledgeRetrievalService` / `KnowledgeService` 两个应用服务，不触及其 repository/mapper。

### 3.1 上下文组装（`ContextAssembler`）

1. **全文补全**：检索命中只给 300 字 `contentPreview`，不足以支撑生成，必须按 `documentId` 取切片全文。
2. **检索后扩展**（可关）：邻居 `sequence ±1` 优先、同章节其次（每命中 ≤ `sectionCap`、按距离由近到远），新增内容受 `expansionAddedChars` 预算约束。依据见第 6 节。
3. **总预算截断**：超过 `budgetChars` 即截断。
4. **排序**：按 *Lost in the Middle*（arXiv:2307.03172）把最强证据放开头、次强放结尾、其余居中。
5. **编号**：每段带 `[n]` 与来源（标题 + `headingPath`），供提示词与引用定位使用。
6. **扩展段的文档归属**：扩展切片没有自己的命中记录，其 `documentId/documentTitle/sourcePath/articleSlug` **继承触发它的命中**。`KnowledgeChunk` 只有 `documentVersionId`，若拿版本 id 冒充文档 id，前端按引用查切片会直接报「文档不存在」——这是实机联调抓到的缺陷（见第 7.3 节）。

### 3.2 拒答与降级（三条语义）

| 情形 | 行为 | 落点 |
| --- | --- | --- |
| 检索没有任何候选 | ~~直接拒答、不调模型~~ **已由 §15.4 修订**：调一次小模型判域并写兜底说明 | `answerMode=ungrounded`，`ungroundedReason=no_domain_match\|no_retrieval_result` |
| 模型判断资料不足（提示词第一层） | 拒答 | `refusalReason=model_refused` |
| 回答存在引用问题（第二层服务端校验） | ~~拒答~~ **已取消（§15.2）**：只清洗引用，答案照常返回 | `invalid_citation` 不再产生；越界/未核验的引用被丢弃，答案保留 |
| 对话模型不可用/密钥缺失/响应异常 | **降级**：返回已检索片段 + 提示语 | `degraded=true`，**不是 500**（与 knowledge 的「重排/关键词可降级」语义一致） |

`refusalDetail` 取值：

- `model_refused`：模型主动拒答；
- `citations_not_verifiable:N`：全部 N 条引用都未通过逐字核验；
- `citations_out_of_range:N`：N 条引用编号都落在本次片段之外；
- `no_citation`：模型给了答案但一条引用都没给；
- `no_candidates`：检索没有候选。

> **2026-10-17 起（§15）**：`invalid_citation` / `citations_not_verifiable` / `citations_out_of_range` / `no_citation` 均**不再产生**（引用问题只清洗引用，不拒答）；零候选改走 `answerMode=ungrounded` + `ungroundedReason`。

**不引入任何相似度阈值熔断**：本项目实测正例最低分 0.0008 与负例最高分 0.8324 完全重叠，阈值法在本语料上不可用（详见 `docs/rag-retrieval-improvement-survey-cn.md`）。拒答只能靠「提示词 + 服务端校验」两层。

### 3.3 引用校验粒度（`AnswerCitationValidator`）（**逐字门禁部分已被 §15.1 取代**）

逐条独立校验，不做「任一不合格即整体拒答」：

1. **编号越界** → 丢弃该条（引用了本次候选中不存在的片段，属确定性接地违规）；
2. **quote 逐字核验**（空白归一化后子串匹配，容忍换行/缩进，不容忍改写；最短 4 字）→ 通过则原样保留 `quoteVerified=true`；
3. 未通过 → 严格模式（默认）下丢弃该条；
4. **只有当没有任何一条引用可核验时**才判定不可作答。（**已由 §15.2 修订**：此后引用问题不再导致拒答，答案一律保留，只清洗引用列表。）

改为逐条粒度的原因：7B 模型常给出 2 条引用、其中 1 条 quote 未逐字对上（例如把相邻行拼成一段），而另一条完全可核验；旧的全量拒答会把答对的回答误判为「知识库没有记录」（见第 7.3 节）。

## 4. 配置项

| 配置 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mindora.rag.chat.enabled` | `MINDORA_RAG_CHAT_ENABLED` | `true` | 关闭则注入 Unavailable 适配器，问答走降级路径 |
| `mindora.rag.chat.provider` | `MINDORA_RAG_CHAT_PROVIDER` | `siliconflow` | 仅作标识 |
| `mindora.rag.chat.base-url` | `RAG_CHAT_BASE_URL` | `https://api.siliconflow.cn/v1` | OpenAI 兼容端点 |
| `mindora.rag.chat.api-key` | `RAG_CHAT_API_KEY` | 空 | **必填**，缺省时走降级 |
| `mindora.rag.chat.model` | `RAG_CHAT_MODEL` | `Qwen/Qwen3-30B-A3B-Instruct-2507` | 按实测选定，见第 7.2 节 |
| `mindora.rag.chat.timeout-ms` | `RAG_CHAT_TIMEOUT_MS` | `15000` | 连接/读取超时 |
| `mindora.rag.chat.temperature` | `RAG_CHAT_TEMPERATURE` | `0.2` | |
| `mindora.rag.chat.max-tokens` | `RAG_CHAT_MAX_TOKENS` | `2048` | |
| `mindora.rag.chat.ungrounded-model` | `RAG_CHAT_UNGROUNDED_MODEL` | `Qwen/Qwen2.5-7B-Instruct` | 零候选兜底（判域 + 写说明）用的小模型，见 §15.4；置空则回落主模型，`max-tokens` 固定 ≤256 |
| `mindora.rag.context.budget-chars` | `RAG_CONTEXT_BUDGET` | `4000` | 上下文总预算 |
| `mindora.rag.context.expansion-enabled` | `RAG_CONTEXT_EXPANSION` | `true` | 检索后扩展 |
| `mindora.rag.context.expansion-added-chars` | `RAG_EXPANSION_ADDED` | `1500` | 扩展新增上限（实测拐点） |
| `mindora.rag.context.section-cap` | `RAG_SECTION_CAP` | `4` | 每命中最多补几条同章节切片 |
| `mindora.rag.validation.require-verbatim-quote` | `RAG_REQUIRE_VERBATIM_QUOTE` | **`false`**（§15.1 起） | 关闭时引用文本一律取服务端摘录、答案不因引用问题拒答；置 `true` 仅额外校验模型给的 quote 用作高亮，不影响作答 |
| `mindora.rag.public.enabled` | `MINDORA_RAG_PUBLIC_ENABLED` | `true` | 公开问答总开关；关闭时端点按 404 处理 |
| `mindora.rag.public.rate-limit.backend` | `RAG_RATE_LIMIT_BACKEND` | `redis` | `redis` / `memory`（测试与无 Redis 单实例） |
| `mindora.rag.public.rate-limit.window-seconds` | — | `60` | 固定窗口长度 |
| `mindora.rag.public.rate-limit.anonymous-per-minute` | — | `6` | 匿名按来源 IP 计 |
| `mindora.rag.public.rate-limit.logged-in-per-minute` | — | `20` | 登录用户按 userId 计 |
| `mindora.rag.public.stream.executor-core` / `.executor-max` | — | `2` / `16` | SSE 任务线程池（队列 0，满则回 `server_busy`） |
| `mindora.rag.security.secret-guard.enabled` | `RAG_SECRET_GUARD_ENABLED` | `true` | 密钥兜底开关（见 §11） |

测试环境（`mindora-app/src/test/resources/application.yml`）设 `mindora.rag.chat.enabled: false`：单测不访问真实模型，问答走降级路径。

## 5. 为什么这样设计（依据）

| 决策 | 依据 |
| --- | --- |
| 拒答必须做服务端校验，不能只靠提示词 | AbstentionBench（arXiv:2506.09038）：规模提升无助拒答，推理微调反而使拒答率 −24%；GopherCite（arXiv:2203.11147）：「带已验证引用作答 + 不确定即弃答」把高质量率 80% → 90%。**rev2 注**：此条只保留「拒绝要由服务端参与」——服务端仍负责零候选判定、分型字段与可答范围注入，但**不再承担引用级核验**（§15）；代价是拒答更依赖模型，见 §15.1。 |
| 最强证据放首尾 | Lost in the Middle（arXiv:2307.03172）：相关信息置于上下文中部时效果甚至差于闭卷 |
| 不把 topK 提到 20 | 实测扩展（SpanHit@8 91.6%）优于提 k（90.1%）；检索文档数 20→50 仅 +1.5% |
| 不做文档配额去重 | 实测 −6.9pt（本项目答案常来自同文档多块） |
| 不做相似度阈值熔断 | 正负例分数区间完全重叠（见 3.2 节） |
| 引用用 `[n]` 编号而非直接让模型给 chunkId | 模型无法凭空捏造不存在的片段编号：编号越界会被服务端确定性拦下 |

## 6. 召回率策略（已实测）

| 手段 | 实测 | 结论 |
| --- | --- | --- |
| 检索后扩展（邻居 ±1 + 同章节 ≤4、新增 ≤1500 字） | SpanHit@8 **88.1% → 91.6%** | **默认开启** |
| 提高召回条数 k=20 | 90.1% | 不提（不如扩展） |
| 文档配额去重 | 81.2%（−6.9pt） | 不做 |
| 预算扫描 | 无限 91.6%/+6083 字；4000 90.6%；**1500 90.1%/+1419 字（拐点）**；800 89.1% | 取 1500 |

分类型增益：code +6.3、overview +5.6、vague +3.0、cross-section → 100%、fact 0。

## 7. 实机联调记录

环境：Windows 宿主 + VM（MySQL 8.4 / Qdrant 1.15.1 / Redis 7.2 / RocketMQ 5.3.2，语料 158 文档 / 141 有切片 / 2028 切片 = Qdrant 点数）。
联调脚本：`logs/rag-integration-check.mjs`（不采信接口自述，**重新拉切片逐字复核**每条引用的 `quote`、`headingPath`、`sequence`）。

### 7.1 验收结果（最终配置，每例 3 轮）

| 指标 | 结果 |
| --- | --- |
| 正例（5 例 × 3 轮）作答且引用可定位且命中期望文档 | **15/15** |
| 负例（10 例 × 3 轮）正确拒答 | **28/30** |
| 端到端延迟 | 中位数 2312ms，最大 15868ms |

负例唯一失败项是 `Kafka 的事务消息怎么保证 exactly-once`（2/3 轮作答）：该用例本身是**边界用例**——语料里 Kafka 只有零星提及，模型用真实召回的消息幂等内容作答且引用逐字可核验。属「答案相关性」问题，不是接地违规。

### 7.2 模型对比（同一套用例，各 3 轮）

| 模型 | 正例通过 | 典型失败模式 |
| --- | --- | --- |
| `Qwen/Qwen2.5-7B-Instruct` | 8/15 | q01 三轮**稳定**答成 MySQL 日志（期望片段就在检索第 1 位）；q02 两轮 answer 里完全没有 citation；q03/q04 各 1 轮单条引用未逐字 |
| `Qwen/Qwen3-30B-A3B-Instruct-2507` | **15/15** | 无 |

结论：正例失败的主因是模型指令遵循能力，**不是检索或框架**。默认模型据此改为 Qwen3-30B-A3B（仍可用 `RAG_CHAT_MODEL` 覆盖）。

### 7.3 本轮抓到的两个缺陷（均已修复并加测试）

**缺陷 1：扩展片段的 `documentId` 被填成 `documentVersionId`**
联调脚本独立复核引用可定位时直接 400：`knowledge_document_not_found`。根因是 `KnowledgeChunk` 只有 `documentVersionId`，而扩展片段没有命中记录，原实现拿版本 id 冒充文档 id。修复：记录扩展片段的来源命中（provenance），继承其文档信息。

**缺陷 2：引用校验「快速失败 + 任一不合格即整体拒答」粒度太粗**
原始模型输出显示 q04 答对了（向量 + 关键词混合检索）并给了 2 条引用，只有 index 1 的 quote 未逐字对上；旧实现连另一条完全可核验的引用一起丢掉。修复：改为逐条独立校验（见 3.3 节）。

### 7.4 归因方法

正例失败先分清「没召回」还是「召回了没用对」：直接用同一问题调检索接口，再用切片全文复核关键片段落在第几位检索命中。本轮 q01/q04 的期望片段都在**第 1 位命中**，因此判定为生成侧问题——避免把生成问题误记到召回账上（也避免反过来为提召回而无谓改动检索链路）。

## 8. 已知限制与后续项

1. **负例拒答依赖模型自觉**：10 条负例里 9 条靠模型主动拒答（`model_refused`），服务端校验只兜住边界项。换更弱模型时拒答率会下降，属模型能力问题。
2. **答案相关性无独立校验**：当前只校验「引用是否真实存在且逐字可核」，不校验「引用是否真的支撑该结论」。GopherCite 那类做法需要独立的 NLI/验证模型，本期未引入。
3. **严格逐字校验对模型能力敏感**：`require-verbatim-quote=true` 时，弱模型会把正确回答拖成拒答。若要「宁可少答也不答错」，可保留此默认；若更看重作答率，可设 `false`（引用文本改为服务端摘录）。
4. **单实例限制**：Lucene 关键词索引是单写者，后端只能单实例（见 `docs/design-10-keyword-retrieval-and-hybrid-fusion.md`）。
5. **公开端点未做**：本轮只有 admin 调试端点，公开问答必须先限定 `scope=public` 并叠加限流与配额。
6. **语料凭据处置（未处理）**：扫描发现疑似明文凭据（`sk-` 20 行、`api_key` 11、`token` 14、`password` 9、`secret` 5，集中在《个人网站开发》《风险与隐患工点管理后端》《栀子花洗涤项目》）。**明文正文的实际存放位置**（2026-10-10 逐字段核实）：MySQL 的 `knowledge_document_version.content_snapshot` 与 `knowledge_chunk.content`；Qdrant payload 只存 id/序号/权限标记/contentHash，**不含正文**；Lucene 正文字段为 `Field.Store.NO`，索引文件里没有可整段读回的正文（倒排词项仍含 token 片段）。因此现实风险路径是**生成层把它当引用回传**，其次是数据库访问/备份/导出。处置顺序：核实 → **轮换密钥**（改文本无法撤销已入库的暴露）→ 脱敏 → 全量重建索引 → 复扫验收。
7. **安全控制粒度决策（2026-10-10 确认）**：**不引入入库门禁**——「检索时不可见」被接受为控制粒度（`knowledge_enabled` + `visibility` 的三层过滤已满足需要）。代价是敏感笔记仍会被切片并嵌入（向量已计算），但正文只落在 MySQL。可选增强（P2）：允许回答**指出存在某个密钥/配置项**（讲名称）而不输出其值——需提示词与生成层兜底脱敏**两处同时**实现，只做提示词不足以兜底。

## 9. 验证入口

```bash
# 单元/集成测试（离线，不访问真实模型）
cd backend && mvn -s .mvn/mindora-settings.xml -gs .mvn/mindora-settings.xml -pl mindora-rag -am test

# 实机联调（需要后端运行 + 有效管理员令牌）
REPEAT=3 node logs/rag-integration-check.mjs
```

`mindora-rag` 测试覆盖：对话适配器 11（请求结构/响应解析/空 key/空消息/上游失败/空响应 + 流式 5：stream 标志与逐帧累积/DONE 后忽略/消费端异常中断且原样上抛/上游 500/非法帧）、上下文组装器 10、引用校验器 17、编排 20（三条降级拒答语义/参数校验/密钥兜底/流式转发）、SecretGuard 6、流式密钥窗口 6、answer 抽取器 12（含随机切分性质测试）、限流适配器 7。合计 **85**。

---

## 10. 公开问答端点（Stage 3.4 交付；Stage 3.5 扩展会话历史）

| 端点 | 鉴权 | 请求体 | 说明 |
| --- | --- | --- | --- |
| `POST /api/v1/public/rag/answer` | 匿名/登录 | `query`（2~500 字）+ 可选 `conversationId` | 一次性返回 `answer` + `citations[]` + `refused/degraded` + `conversationId` |
| `POST /api/v1/public/rag/answer/stream` | 匿名/登录 | 同上 | SSE，事件契约见 §12（done 事件含 `conversationId`） |
| `GET /api/v1/public/rag/conversations` | 登录（匿名返回空列表） | — | 会话列表（未软删，按最近消息倒序） |
| `GET /api/v1/public/rag/conversations/{id}/messages` | 登录 | — | 会话完整消息（恢复对话）；匿名/非本人 404 语义 |
| `DELETE /api/v1/public/rag/conversations/{id}` | 登录 | — | 软删会话 |

**Stage 3.5 会话语义**：登录用户带合法 `conversationId` 时注入记忆窗口（滑动窗口 + token 双约束，历史消息剥离 `[n]` 编号，不进本轮引用核验域）；终态（done/refused/degraded）落库 `chat_conversation`/`chat_message`（V8 迁移），新会话 id 随响应/done 事件**首次下发**，前端续聊携带；**匿名或非法 `conversationId` 按无会话处理**（不报错、响应字段为 null）——归属不匹配与不存在同为 404 语义，防枚举。详见 `docs/stage-3.5-implementation-plan-cn.md` 与 `docs/stage-3.5-task-manifest-cn.md`。

**三条硬性约束**：

1. **scope 由服务端硬编码 `public`**，且请求体**根本没有** scope 字段——比「接受但忽略」更难被误用（单测断言控制器交给领域层的入参）；
2. 公开内容判定沿用知识库既有过滤（`visibility=public` + `knowledgeEnabled` + `status=active` + MySQL 权威复核），RAG 层不做二次判断、也不放宽；
3. 叠加限流（§10.1）与密钥兜底（§11）。

响应形状与管理端**完全一致**（共用 `RagAnswerViews`），差别只在请求体收窄与权限。

错误码：`validation_error`(400)、`rag_rate_limited`(429)、`rag_public_disabled`(404)。预检失败一律返回**普通 JSON**（对 SSE 端点而言，此时尚未写 `text/event-stream` 头）。

### 10.1 限流（固定窗口）

- Redis `INCR` + **仅首次** `EXPIRE`（每次 INCR 都续期等于不限流）；无 Redis 时用进程内实现（测试与单实例）。
- 分桶：登录用户 `u:{userId}`（20/分）、匿名 `ip:{remoteAddr}`（6/分）。身份经 `CallerIdentityPort` 端口获取——业务模块不依赖 Spring Security，由 app 层读 `SecurityContextHolder` 的 `TokenPrincipal`。
- **fail-open**：Redis 不可用时放行 + warn。理由是 design-04 的分级原则——限流是**成本护栏**，不是权限边界；把公开问答整体打成 429 是更糟的故障模式。泄露防护由 §11 独立承担。
- 阈值是初始保守值，需按真实分布调整（见 stage-3.4 清单 §10.4）。
- `getRemoteAddr()` 是直连地址；置于反向代理后必须按可信代理解析 `X-Forwarded-For`，否则全体访客共用代理 IP 的额度。

## 11. 密钥兜底（SecretGuard）

**定位**：内容是否公开由**文章级 visibility** 决定；本层只兜「不该公开的文档意外可检索」这一剩余风险。误伤取向是**宁可误拒、不可泄露**。

| 层 | 时机 | 规则 | 命中后果 |
| --- | --- | --- | --- |
| 上下文层 | 检索后、调模型**前** | 仅前缀型高置信规则 | 直接拒答（`protected_content`），密钥**不进 prompt**、不花模型调用 |
| 答案层 | 生成后（含流式窗口） | 全部规则（含赋值型 + 占位符白名单） | 改判拒答，`refusalDetail=secret_guard:answer:{ruleId}` |
| 引用摘录 | 产出前 | 同上（拼接答案 + 所有 citation 的 `quote`） | 改判拒答——降级路径回传的是服务端摘录，是**独立泄露路径** |

规则取自 gitleaks 默认规则集的高置信子集：`sk-` 风格密钥（≥32 位）、AWS `AKIA/ASIA/ABIA/ACCA`、阿里云 `LTAI`、GitHub `gh[pousr]_`、Google `AIza`、Slack `xox[baprs]-`、`-----BEGIN … PRIVATE KEY-----`、赋值型（关键字 + 分隔符 + 20~64 位值，带占位符白名单 `\${API_KEY}`/`<your-key>`/`xxxx`/`changeme` 等）。不做熵检测。

**流式不变式**：`StreamingSecretFilter` 始终保留尾部 `HOLDBACK = 96` 字符不转发（必须 ≥ 最长规则长度，否则跨边界命中会漏检；修改规则表需同步调大）。命中抛 `SecretGuardSignal` → 经适配器的 `StreamConsumerAbort` 冒泡出读取循环 → 关闭响应流（与「客户端断开」共用同一中断机制）。

**管理端不启用**（`secretGuard=false`）：管理员看的是自己的库，拒答反而妨碍排查。

## 12. SSE 事件契约（Stage 3.4 交付）

```text
data: {"type":"delta","text":"…"}                    ← 0..n，经密钥窗口过滤
data: {"type":"citation","citation":{…§2 引用结构…}}   ← 校验后逐条
data: {"type":"done","refused":…,"refusalReason":…,"refusalDetail":…,
       "degraded":…,"degradedReason":…,"chatModel":…,"latencyMs":…,"firstDeltaMs":…}
data: {"type":"error","code":"…","message":"…"}       ← 意外失败
```

- **终态规则**：`done` 或 `error` **恰一个**为终态。`server_busy`（线程池满）以 `error` 表达。
- **模型契约未改**：流式只是把模型输出的 JSON 信封增量抽出 `answer` 正文（`StreamingAnswerExtractor` 状态机处理转义与跨 delta 的键名/转义符/Unicode 转义），完整累积文本仍交给既有校验器解析 `citations`。这样「先流文字、后校验引用」不需要改模型协议，Stage 3.3 的实测基线不受影响。
- **已流出文字随后被判定为拒答**（**注：§15 后此路径只剩安全类 `refused`**）：严格模式下引用逐字核验需要完整文本，因此 `done{refused=true}` 可能晚于已显示的文字。**前端必须以标准拒答文案替换已显示内容**——这是设计取舍，不是缺陷（Dify/Vercel AI SDK 同为「终止事件携带最终元数据」的模型）。
- **首字节延迟**：`firstDeltaMs`（收到请求 → 首个 delta）与 `latencyMs`（总耗时）都在 `done` 中；降级路径 `firstDeltaMs=-1`（无 delta）。
- **中断**：客户端断开 → 写事件抛错 → 异常穿透到适配器 → 停止读取上游并关闭连接；服务端记录 `status=cancelled`，不残留线程。
- **已知限制**：无 `ping` 保活、无断线重连与「续流」（个人项目从简，靠 emitter 超时 = chat 超时 + 30s 兜底）。若将来置于有 idle 超时的反向代理之后，需要补心跳。

### 12.1 前端接入注意（实现约束，均已核实）

1. **不能用原生 `EventSource`**：该端点是 **POST**，而 `EventSource` 只支持 GET。必须用 `fetch()` 取 `response.body` 的 `ReadableStream`，自行按空行切帧、剥掉 `data: ` 前缀、`JSON.parse` 后按 `type` 分派。也**不要**注册 `addEventListener('delta')`——服务端发送时没有设置 event name，唯一判据是 JSON 里的 `type` 字段。
2. **预检失败不是事件流**：`rag_public_disabled`(404) / `validation_error`(400) / `rag_rate_limited`(429) 都返回**普通 JSON**（此时响应头还不是 `text/event-stream`）。前端必须先判状态码与 `content-type`，再决定「按 SSE 解析」还是「按 JSON 错误体解析」。
3. **429 没有 `Retry-After` 响应头**：错误体只给 `code=rag_rate_limited` 与 `message`。前端需自行按窗口（默认 60s，见 `window-seconds`）给出「请稍后再试」的提示或倒计时。
4. **跨源**：前台站点以绝对地址访问后端（`VITE_API_BASE_URL ?? http://localhost:8080`），浏览器会走 CORS。后端允许源默认是 `http://localhost:5173`、`http://localhost:5174` 及二者 `127.0.0.1` 变体（`mindora.cors.allowed-origins`）；**5175 不在白名单里**，前台站点请使用 5173，或把新源加入配置后再用。SSE 属跨源流式请求，同样受该白名单约束。
5. **带 JWT 可提升额度**：请求携带有效 `Authorization: Bearer <token>` 时按 `userId` 分桶（20/分），匿名按 IP（6/分）。已登录用户应当带上令牌。

## 13. 访问日志

每条问答（公开同步/公开流式/管理端）落一行 `RAG_ACCESS`：

```text
endpoint=public|public-stream|admin scope=… user=… ip=… status=ok|refused|degraded|error|cancelled
refusal=… model=… latencyMs=… firstDeltaMs=… citations=… query=…（截断 100 字）
```

用途：限流阈值调优、异常流量发现、拒答构成分析。**不记录**完整 prompt、完整上下文与系统提示词（design-04 明令默认不存）。落库（`rag_chat_log` 表）、报表与降级告警归 Stage 4 运维范围。

## 14. 实机验证（2026-10-11）

公开端点实机结论（完整记录见 `docs/stage-3.4-task-manifest-cn.md` §10.2）：公开文档可作答且**引用只来自 public**；负例拒答；**私有内容零召回**；SSE 事件序 `delta×12 → citation → done`（`firstDeltaMs=9125ms`）；限流 `200,200,429,429,429,429` 且 SSE 预检返回 JSON 429；对话模型不可用时 JSON 与 SSE **均为降级而非 500**；管理端回归正例 15/15（流式重构无回归）、负例 27/30（唯一失败仍是 Kafka 边界用例）。

---

## 15. 2026-10-17 修订（v2）：引用不做逐字门禁、应答改为三态（有依据 / 无依据兜底 / 拒绝）、公开域收紧为已发布

> **本节取代** §2.2（quote 与 quoteVerified）、§3.2 表中的「第三线 / invalid_citation」、§3.3 的严格逐字门禁；原章节保留作历史。
> **背景（作者裁决）**：① 知识库不可能覆盖所有情况，逐字校验会把正确回答拖成拒答、并让回答趋同；回答应由模型**基于引用来源自行组织**。② 用户问的若是与本站无关的事，应当**说明情况并给替代路径**（参考 RAGNote 的「信息说明 + 实用建议 + 引导」），不是丢一句"知识库没有记录"。③ 公开站要按可上线标准收紧知识范围。④ 零候选时允许再调一次小模型做判定与说明。⑤ 模型完全没给引用也不拒答。
> 实现落在 Stage 4.1（公开域）、4.2（回答契约 v2）与 3.6（管理端问答页），见 `docs/design-07-implementation-roadmap.md`。

### 15.1 引用：从「逐字核验门禁」改为「服务端摘录 + 编号校验」

| 项 | 旧（§2.2 / §3.3） | 新 |
| --- | --- | --- |
| `quote` 来源 | 模型逐字给出且通过子串核验才保留 | **一律由服务端从命中切片截取**（按句边界，≤200 字） |
| 模型给的 `quote` | 判据（不过即丢弃该条） | 仅作「重点片段」提示：逐字命中则用作高亮，未命中静默忽略，**不影响引用去留与作答** |
| 编号越界 | 丢弃该条；一条不剩 → 拒答 `invalid_citation` | 丢弃该条 **并从正文里删掉这个 `[n]` 标记**（不能让用户点到死链）；答案照常返回，`citations` 可为空 |
| 模型没给引用 | 拒答 `no_citation` | **不拒答**：答案保留，`citations` 为空，`citationless=true` |
| 拒答触发 | 含「引用不合格」 | **引用问题永不导致拒答** |
| 响应字段 | `quote` + `quoteVerified` | `quote`（服务端摘录）+ `quoteSource="server_excerpt"`；`quoteVerified` 保留一版恒 `false` 作兼容，随后移除 |
| 配置 | `require-verbatim-quote` 默认 `true` | 默认 **`false`** |

**保留的确定性下限（这是「可追溯」的底线，不再放宽）**：

1. `citation.chunkId` 必须能在本次候选集合中回查，否则丢弃该条；
2. `citation.quote` 必须逐字出现在该 `chunkId` 的切片正文中（服务端自取，天然成立）；
3. `headingPath` / `articleSlug` / `sequence` 用于回链，缺失则前端降级为纯文本；
4. 引用卡上不再声称「逐字核验」，文案统一为「摘自原文」；
5. **正文 `[n]` 与引用卡一一对应**：产出前扫描答案正文里的 `[n]`，只保留能回查的编号，其余标记**直接删除**（不保留成纯文本），避免正文出现点不动的死链。

**代价（要写进验收，不许隐去）**：我们不再能声明「模型说的每个字都在原文里」，只能声明「展示给你的引文来自原文，答案由模型基于这些片段组织」。这换来的是作答率与表达自然度——这是作者明确选择的取舍。

### 15.2 应答三态：有依据作答 / 无依据兜底 / 拒绝

参考项目的做法不是「拒答」，而是**先把情况说明白，再给可行动的替代**。照此把「拒答」拆成三态：

| 态 | `answerMode` | 触发 | 带引用 | 关键约束 |
| --- | --- | --- | --- | --- |
| 有依据作答 | `grounded` | 有候选，模型按候选作答 | 可带 | 正文 `[n]` 必须都能回查 |
| 无依据兜底 | `ungrounded` | 零候选（主题无关 / 未收录），或有候选但模型判定资料不足 | **不带** | 必须标注「非知识库内容」；**不得伪造引用** |
| 拒绝 | `refused` | 仅安全类原因（如 SecretGuard 命中、内容违规） | 不带 | 保留原有语义 |

`ungrounded` 的两种原因（`ungroundedReason`）：

| 原因 | 场景 | 文案要求 |
| --- | --- | --- |
| `no_domain_match` | 与本站收录主题**无关**（如「今天天气怎么样」） | 说明本站覆盖范围（分类 + 代表主题），给替代路径 |
| `no_retrieval_result` | 主题相关但本站**没有写过** | 「本站没有写过这个」+ 最接近的 2~3 篇标题 |
| `model_refused` | 有候选，但资料不足以支撑作答 | 说明「资料不足」，给替代路径（同一结构，不另造文案） |

**为什么不再叫「拒答」**：旧的三线拒答在零候选时只说「知识库没有记录」，对访客是死路。参考项目的结构是「信息说明 → 实用建议 → 引导继续问」；我们采纳结构，但**加一条它没有的硬约束**：兜底态的答案**不带任何引用**，且必须标注「非知识库内容」——否则就是拿模型常识冒充站内资料。

**防模型偷懒（保留）**：检索**有候选**时，模型只能在「按候选作答」与 `ungrounded(model_refused)` 之间选；`no_domain_match` 只允许在**零候选**时出现。服务端校验：`no_domain_match` 但候选非空 → 强制改判 `ungrounded(model_refused)`。

### 15.3 公开检索域收紧为「前台已发布且可见」

现状（design-10 §4.2）公开域过滤 = `visibility=public + knowledgeEnabled + status=active`，**权威字段在 `knowledge_document`**，与「文章是否发布」是两套标记。已知缺口：文章 unpublish 不回退 `visibility`（`docs/stage-3.4-task-manifest-cn.md` §11.8-F1 订正）→ **前台已下架的文章仍可能被公开问答召回**。

新契约（`scope=public`，fail-closed）：

1. 公开域候选必须能回溯到**一篇处于已发布状态的文章**；没有关联文章的 Gitee 文档**不进公开域**；
2. 权威判定在 MySQL：检索时复核文章状态（`published`），知识库侧字段降为「入库意图」；
3. **无法判定即排除**，不做「拿不准就放行」；
4. 私有域（管理端 `scope=all`）不变：草稿、未发布笔记、全部来源都可检索。

理由：知识范围必须与前台可见内容一致。否则「站上搜不到、问答却引用了」——对访客是信息泄漏，对作者是两套事实。

**边界说明（不改的那一半）**：草稿**继续入库**（切片 + 向量 + 关键词），这是个人知识助手的价值所在；「只处理已发布文章」不成立，成立的是「**公开域**只处理已发布文章」。

### 15.4 零候选时的兜底形态（按参考项目截图落地）

零候选不再「直接拒答、不调模型」，而是：**调一次小模型做判定 + 生成说明**，再由服务端拼装结构化回复。

页面结构（六段，顺序固定）：

1. **一句话开场**：说明这是什么情况（「关于这个问题，我需要先说明一下」）；
2. **信息说明卡**（前端固定组件，不交给模型排版）：写明「知识库中没有检索到相关片段」+ 原因（域外 / 未收录）；
3. **可选常识补充**：允许模型给通用建议，但整段必须由前端标注「非知识库内容」，且**不得出现 `[n]`**；**只能给方法（怎么查、去哪查），不得给具体事实**（温度、股价、日期事件等一律不许编，参考项目截图里那句「10 月中旬…早晚温差大」是边界：属季节性常识，可留）；
4. **可行动的下一步**：告诉用户还能怎么问（改问本站主题 / 加具体主题词 / 直接去检索文章）；
5. **「您可能还想了解」建议追问（服务端生成，不占模型调用）**：从当前域目录取 3 条（分类 + 代表文档标题），点一下即发问；
6. **评价按钮**（赞 / 踩）：与 4.3 的质量闭环共用同一接口。

**成本**：零候选时多一次调用，用小模型（`mindora.rag.chat.ungrounded-model`，默认 **`Qwen/Qwen2.5-7B-Instruct`**，置空则回落主模型；`max-tokens ≤ 256`）。判定与说明**在同一次调用内完成**，不拆两次。

**为什么值得花这一次调用**：零候选恰好是访客最需要被带路的时刻。旧做法省了这一次调用，换来的是「死路 + 不知道能问什么」。

**可答范围注入（`answerableScope`）**：兜底时把**当前域的可答范围摘要**（分类 + 文档标题）注入提示词，受 `budget-chars`（4000）约束；几十篇可全标题注入，几百篇只注入分类计数与代表标题——由 `catalogMode=full|summary|off` 控制（按文档数阈值自动切档）。

### 15.5 两域：管理端问答与公开问答各自怎么工作

同一条 `RagAnswerService`，两个入口，差别只在**域**与**回链目标**：

| 入口 | 域 | 鉴权 | 引用回链目标 | 兜底文案视角 |
| --- | --- | --- | --- | --- |
| 公开 `/api/v1/public/rag/answer(/stream)` | `public`（= 已发布文章，服务端硬编码） | 匿名 / 登录 | `/blog/{slug}#h={anchor}` | 访客：说明本站公开内容范围 |
| 管理端 `/api/v1/admin/rag/answer` | `all`（草稿、私有笔记、未发布、Gitee 来源） | `SUPER_ADMIN` | 管理端知识文档 / 切片详情（私有文档无 `articleSlug`，回链到文档预览） | 作者：给「该文档未发布 / 未入索引」这类诊断信息 |

三条配套规则：

1. **管理端必须能按公开域复测**：`scope` 可选 `public`，用于「线上答不好的问题，在管理端用公开域重跑」。4.3 的同问题复测靠它，否则复测结论不可迁移（与 5.3 里 RAGNote 自己强调的坑同源）。
2. **管理端问答页是 3.6 的交付物**：会话历史的生产者只剩管理端（3.5 的公开站会话 UI 已弃用），没有管理端问答入口，V8 的 `chat_conversation` 就没有数据来源。所以 **3.6 = 管理端问答 + 会话列表 / 查看 / 删除**。
3. **SecretGuard 管理端不开**（§11）：作者要能看见自己的库；但页面上要标注「私有内容，勿外传」，因为那里可能出现密钥类文本。

### 15.6 对既有基线与验收的影响

- **3.3 的实测基线不再等价**：旧基线（正例 15/15、负例 28/30）是在 `require-verbatim-quote=true` 下测的。改法生效后**作答率上升、`invalid_citation` 归零、拒答率下降**，必须重跑一轮并把新数字写进对应 stage 文档；**口径变更不得与旧数字直接比较**。
- **完成判据修订**（`docs/ragnote-insights-and-architecture-takeaways.md` §5.5 D3）：改为「零越界引用 + 每条 quote 均为服务端摘录 + 兜底态零引用」。
- **前端**：引用卡去掉「逐字核验」标记（改「摘自原文」）；新增三态渲染（`grounded` 正常答案 / `ungrounded` 信息说明卡 + 「非知识库内容」标注 + 建议追问 / `refused` 标准拒答）；`quoteVerified` 保留一版兼容。
- **OpenAPI**：`RagAnswerView` 与 SSE `done` 增加 `answerMode`、`ungroundedReason`、`suggestions[]`、`citationless`；移除 `refusalDetail` 中的 `citations_not_verifiable:*` / `citations_out_of_range:*`；`refused` 布尔字段保留一版（等价于 `answerMode=refused`）。

---

## 16. 提示词版本化（2026-10-17 新增，随 4.2 交付）

> **目的**：满足「生产级」定义里的**改了能回退**。现状 prompt 硬编码在 `RagPrompt`，改一句要发版、回退只能回滚整个版本。
> 参考做法：RAGNote 把提示词放后台，可维护、可看历史、可恢复（`logs/ragnote-v2-intro-2026-10.md` L503–507）。

### 16.1 来源优先级与生效语义

1. **DB 生效版本**（`prompt_template.active_version`）——后台「发布」后立即生效；
2. **配置文件**（`mindora.rag.prompt.*`）——本地 / 测试覆盖用，存在时优先于 DB；
3. **代码内置默认**（`RagPrompt`）——兜底，DB 与配置都没有时才用。

**生效不重启**：生成层按 `templateKey + version` 读缓存（TTL 60s，或发布时主动失效），发布即刷。

### 16.2 模板与版本

| 项 | 说明 |
| --- | --- |
| `templateKey` | `answer.grounded` / `answer.ungrounded` / `scope.classify`（首个版本只需这三个） |
| 版本 | 单调递增整数；每版存全文 + 备注 + 创建人 + 创建时间 |
| 状态 | `draft` / `active` / `archived`；同一 key 同时只有一个 `active` |
| 回退 | 把任一历史版本置为 `active`（一条 UPDATE，不重启、不发版） |
| 差异 | 版本间展示文本 diff，确认「这次到底改了什么」 |
| 占位符 | `{{context}}` `{{question}}` `{{history}}` `{{scope}}` 等；**发布前校验占位符齐全，缺一个即拒绝发布**（防止线上拼出半截 prompt） |

迁移：`V9__prompt_template.sql`（表结构在 design-06 下次修订时并入登记）。

### 16.3 与三态的绑定

| key | 用途 | 约束 |
| --- | --- | --- |
| `answer.grounded` | 有候选时作答 | 要求 JSON 输出（`answer` / `citations` / `scope`），引用只许 `[n]` |
| `answer.ungrounded` | 零候选兜底 | 输出说明 + 可行动建议，**禁止引用**，`max-tokens ≤ 256`，用 `Qwen/Qwen2.5-7B-Instruct` |
| `scope.classify` | 零候选时判域 | 输出 `no_domain_match` / `no_retrieval_result` |

三个 key **版本独立、回退互不影响**——这才是「只回退那一处」。

### 16.4 后台页面（design-05 增补时落地）

列表（key + 当前 active 版本 + 更新时间）→ 编辑（带占位符校验）→ 预览（用固定样例上下文渲染）→ 发布 → 历史（diff + 一键回退）。

### 16.5 验收

- 改一版 `answer.grounded` 并发布，**不重启**，下一次问答生效（实机核对 `RAG_ACCESS` 日志里的版本号）；
- 回退到上一版同样不重启生效；
- 占位符缺失的版本**发布被拒**且有错误提示；
- 历史版本可 diff；同一 key 的 `active` 唯一性有测试断言。
