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
| 多轮对话 | 未实现（`conversationId` 仍被忽略，顺延 Stage 3.5） | — |
| 换检索实现 / 加查询改写 | 端口已就绪（`RetrievalFacadePort`） | §3 |

**明确不做**：前端聊天页（顺延 Stage 3.5）、RAPTOR/摘要索引、句级重切、Late Chunking（调研已否定，见 `docs/rag-retrieval-improvement-survey-cn.md`）。

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
  "conversationId": null   // 预留：多轮记忆（今天忽略，非法值按无会话处理）
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

### 2.2 `quote` 与 `quoteVerified`

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
| 检索没有任何候选 | **直接拒答，不调用模型**（省钱且防幻觉） | `refused=true`，`refusalReason=no_context` |
| 模型判断资料不足（提示词第一层） | 拒答 | `refusalReason=model_refused` |
| 回答存在引用问题（第二层服务端校验） | 拒答 | `refusalReason=invalid_citation`，`refusalDetail` 给出细分原因 |
| 对话模型不可用/密钥缺失/响应异常 | **降级**：返回已检索片段 + 提示语 | `degraded=true`，**不是 500**（与 knowledge 的「重排/关键词可降级」语义一致） |

`refusalDetail` 取值：

- `model_refused`：模型主动拒答；
- `citations_not_verifiable:N`：全部 N 条引用都未通过逐字核验；
- `citations_out_of_range:N`：N 条引用编号都落在本次片段之外；
- `no_citation`：模型给了答案但一条引用都没给；
- `no_candidates`：检索没有候选。

**不引入任何相似度阈值熔断**：本项目实测正例最低分 0.0008 与负例最高分 0.8324 完全重叠，阈值法在本语料上不可用（详见 `docs/rag-retrieval-improvement-survey-cn.md`）。拒答只能靠「提示词 + 服务端校验」两层。

### 3.3 引用校验粒度（`AnswerCitationValidator`）

逐条独立校验，不做「任一不合格即整体拒答」：

1. **编号越界** → 丢弃该条（引用了本次候选中不存在的片段，属确定性接地违规）；
2. **quote 逐字核验**（空白归一化后子串匹配，容忍换行/缩进，不容忍改写；最短 4 字）→ 通过则原样保留 `quoteVerified=true`；
3. 未通过 → 严格模式（默认）下丢弃该条；
4. **只有当没有任何一条引用可核验时**才判定不可作答。

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
| `mindora.rag.context.budget-chars` | `RAG_CONTEXT_BUDGET` | `4000` | 上下文总预算 |
| `mindora.rag.context.expansion-enabled` | `RAG_CONTEXT_EXPANSION` | `true` | 检索后扩展 |
| `mindora.rag.context.expansion-added-chars` | `RAG_EXPANSION_ADDED` | `1500` | 扩展新增上限（实测拐点） |
| `mindora.rag.context.section-cap` | `RAG_SECTION_CAP` | `4` | 每命中最多补几条同章节切片 |
| `mindora.rag.validation.require-verbatim-quote` | `RAG_REQUIRE_VERBATIM_QUOTE` | `true` | 关闭后仅校验编号，引用文本回退为服务端摘录 |
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
| 拒答必须做服务端校验，不能只靠提示词 | AbstentionBench（arXiv:2506.09038）：规模提升无助拒答，推理微调反而使拒答率 −24%；GopherCite（arXiv:2203.11147）：「带已验证引用作答 + 不确定即弃答」把高质量率 80% → 90% |
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

## 10. 公开问答端点（Stage 3.4 交付）

| 端点 | 鉴权 | 请求体 | 说明 |
| --- | --- | --- | --- |
| `POST /api/v1/public/rag/answer` | 匿名 | 仅 `query`（2~500 字） | 一次性返回 `answer` + `citations[]` + `refused/degraded` |
| `POST /api/v1/public/rag/answer/stream` | 匿名 | 仅 `query` | SSE，事件契约见 §12 |

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
- **已流出文字随后被判定为拒答**：严格模式下引用逐字核验需要完整文本，因此 `done{refused=true}` 可能晚于已显示的文字。**前端必须以标准拒答文案替换已显示内容**——这是设计取舍，不是缺陷（Dify/Vercel AI SDK 同为「终止事件携带最终元数据」的模型）。
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
