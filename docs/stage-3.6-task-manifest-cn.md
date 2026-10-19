# Stage 3.6 阶段文档：身份地基与会话域统一

> 一句话用途：记录本阶段的**设计目标（Spec）**与**实现进展**，作为实施与验收的唯一台账。
> 维护日期：2026-10-19　状态：**已完成**（3.6a 批次①–④、3.6b 批次⑤–⑥ 全部实施并验收）

> **范围重定说明**：本阶段原定「管理端流式问答工作台」。2026-10-18 产品评审后重定：
> 站长与访客视为**同一类用户**，差别只在「能查多大范围」；管理端**不做聊天 UI**；会话历史归**账号**。
> 因此 3.6 拆为 **3.6a 身份地基** + **3.6b 会话域统一**。原方案未产生任何代码，直接作废，不留迁移负担。

---

## 1. Spec 设计目标

### 1.1 三条决策（来自产品评审）

1. **统一用户模型**：站长不是"另一种东西"，只是"能查到更多内容"的用户；账号、会话、限流共用一套。
2. **范围由入口决定，不由身份决定**：公开站入口永远是 `public`（站长登录也是访客视角），管理端入口要求 `SUPER_ADMIN` 才允许 `all`。一个路径一个上限，便于审计。
3. **会话历史是账号资产**：`chat_conversation.user_id` 统一归属；将来公开站登录（暂记 4.7）复用同一套会话域，不返工。

### 1.2 两个子阶段

| 子阶段 | 目标 | 契约 |
| --- | --- | --- |
| **3.6a 身份地基** | 登录态可信：双令牌、可过期、可撤销、可禁用；注册开放且防刷；为 Google/GitHub 预留结构 | `design-12-identity-and-token-lifecycle.md` |
| **3.6b 会话域统一** | 管理端问答接入会话域（记忆 + 落库 + 回传 id）；会话 CRUD 从公开路径收回到用户域；`scope` 上限按入口 | `design-11` §15.5 |

### 1.3 明确不做

管理端聊天界面（归 Stage 4.3 的复测台/私有问答）、OAuth 具体实现、邮箱验证、找回密码、设备管理、细粒度 RBAC（`@PreAuthorize`）、公开站登录 UI（暂记 4.7）。

---

## 2. 任务拆解

### 3.6a 身份地基（详见 design-12）

| # | 任务 | 交付物 | 验收 |
| --- | --- | --- | --- |
| a0 | **前端请求同源化**（纯前端，可最先做） | 公开站点 `vite.config.ts` 增加 `/api` 代理；`rag.ts` 的默认基地址改为空串（相对路径）；相关测试断言同步 | `npm test --workspace=@mindora/site` 全绿；Network 面板里问答请求是 `/api/...` 同源，不再出现 `localhost:8080` |
| a1 | V9 迁移 + 会话无关的令牌表仓储 | `user_account.token_version`、`auth_refresh_token`、`auth_account` 回填（provider='password'） | 迁移在干净库与现有库各跑一次；回填幂等；现有数据可登录 |
| a2 | 令牌服务换成 JJWT | Access JWT（`sub` 32 位、`roles`、`tv`、`exp`）；Refresh 生成/哈希/轮换/家族撤销 | 单测：签发、验签、过期、篡改、轮换、宽限、重用检测 |
| a3 | 认证端点改造 | `register` / `login` 改造（裸 token + RT Cookie）、新增 `refresh` / `logout` / `me`；统一 401 JSON 体 | 单测 + 集成测试；错误码与 §4.1 一致 |
| a4 | 安全护栏 | 启动期 `JWT_SECRET` 强校验；登录校验 `status`/`deleted`；注册/登录/刷新限流；密码 8–72 | 单测：默认密钥拒绝启动、禁用账号登录被拒、限流触发 |
| a5 | 前端登录态 | session bootstrap；单飞刷新 + 重放；裸 token；登出；后台 403 文案 | 前端单测（拦截器/状态）+ 实机 |

### 3.6b 会话域统一（详见 design-11 §15.5）

| # | 任务 | 交付物 | 验收 |
| --- | --- | --- | --- |
| b1 | 抽取共享会话组件 | `ConversationTurns`：`resolve`（取记忆窗口，失败按无会话）+ `record`（终态落库，异常静默 WARN） | 单测 5 项；公开端点测试原样全绿 |
| b2 | 管理端问答接入会话 | 注入历史、终态落库、回传 `conversationId`；新增 `persist`（默认 `true`） | 集成测试：首次下发 id、追问同 id、非法 id 不报错 |
| b3 | 会话 CRUD 迁移 | 从 `/api/v1/public/rag/conversations*` 迁到 `/api/v1/rag/conversations*`（登录态、按 `userId` 隔离） | 集成测试：列表/详情/删除/他人 404；旧路径删除后无残留引用 |
| b4 | 范围上限按入口 | 公开入口固定 `public`；管理端入口要求 `SUPER_ADMIN` 且 `scope` 仅接受 `all`/`public` | 集成测试：公开入口传 `scope` 无效；管理端传非法 `scope` 被拒 |

---

## 3. 契约与数据变更登记

| 类别 | 变更 | 文档 |
| --- | --- | --- |
| HTTP | 新增 `/auth/refresh`、`/auth/logout`、`/auth/me`；`AuthView` 增加 `roles`、`expiresIn`；Access Token 改为裸 token | design-12 §4 |
| 错误码 | `token_expired`、`token_invalid`、`token_revoked`、`account_disabled`、`refresh_*` | design-12 §4.1 |
| 会话 | 会话 CRUD 路径迁移；管理端问答支持 `persist` 与 `conversationId` 回传 | design-11 §15.5 |
| DB | V9：`token_version`、`auth_refresh_token`、`auth_account` 回填 | design-12 §5 |
| 配置 | `mindora.auth.*`（密钥、TTL、宽限、限流） | design-12 §6 |
| 同步 | `npm run api:sync` / `api:generate` / `api:check` | — |

---

## 4. 开发顺序（前后端）

**同源约定**：两个前端一律用相对路径 `/api`，本地由 vite 代理转发、生产由同域反代；因此不走 CORS、Cookie 是第一方 Cookie。这条在 a0 落地，之后所有联调都按它来。

| 天 | 后端 | 前端 | 完成标志（Gate） |
| --- | --- | --- | --- |
| D1 | a1 迁移 → a2 令牌服务 | a0 同源化 | 迁移可跑；令牌单测全绿；站点请求变同源 |
| D2 | a3 认证端点 | ——（等 a3） | **curl 全链路**：login → 拿 Cookie → refresh → me → logout |
| D3 | a4 安全护栏 | a5 登录态（骨架 + 单测） | 禁用账号登录被拒；拦截器单测绿 |
| D4 | b1 共享会话组件 → b2 管理端接入 | a5 联调 | 刷新页面不掉登录；管理端问答产生会话、追问同 id |
| D5 | b3 路径迁移 → b4 范围校验 | 配合回归 | 会话 CRUD 走新路径；他人 404；`persist=false` 不写 |
| D6 | 全量回归 + OpenAPI 同步 | 全量回归 + 构建 | 5 条实机路径全通，文档回填 |

硬约束（不能打乱）：

1. `a1 → a2 → a3` 严格顺序（表 → 令牌 → 端点）；
2. **a5 的联调必须等 a3 合并**（端点先存在再联调）；a5 的骨架与单测可以提前写；
3. `b1 → b2 → (b3 ∥ b4)`；3.6b **不产出界面**（界面归 4.3）；
4. **a3 合并后旧前端不立即失效**：后端过滤器兼容「带 Bearer 前缀」与「裸串」一版，留出前端改造窗口；
5. 3.6a 与 3.6b 文件域不重叠（user/security vs rag），D3 起双线并行是安全的。

### 4.1 建议的「对话批次」

一个对话 = **一个可验证里程碑 + 一套心智模型**，不要跨模块。按此拆成 5 个对话：

| 对话 | 包含任务 | 结束时必须达到 | 预估 |
| --- | --- | --- | --- |
| ① | a0 + a1 | 前端全部同源；V9 迁移可跑、回填幂等；应用仍能正常登录（旧令牌机制不变） | 0.5–1 天 |
| ② | a2 + a4（密钥部分） | JWT 换成 JJWT，带 `exp`/`tv`；启动期密钥强校验；令牌单测全绿，旧前端仍可用（过滤器兼容） | 1 天 |
| ③ | a3 + a4（状态与限流） | `/auth/refresh`、`/auth/logout`、`/auth/me` 可用；登录校验 `status`；三处限流；**curl 全链路通过**；OpenAPI 同步 | 1.5 天 |
| ④ | a5 | 后台刷新页面不掉登录、Access 过期无感续期、登出全端失效、403 文案 | 1–1.5 天 |
| ⑤ | b1 + b2 | `ConversationTurns` 抽取（公开端点测试原样全绿）+ 管理端问答落库并回传 id | 1–1.5 天 |
| ⑥ | b3 + b4 | 会话 CRUD 走新路径、他人 404、`persist=false` 不写、范围按入口校验 | 1 天 |

> 进度（2026-10-19）：对话 ①–⑥ 全部完成，Stage 3.6 收口。

为什么这样切：

- **a0 与 a1 都"无行为变更"**，一起做风险最低，且 a0 是后续所有前端联调的前提；
- **a2 单独成对话**：换掉 JWT 实现是"全仓库只此一处"的心智模型，混进端点改造容易两头出错；
- **a3 是最大的块**，端点、Cookie、错误码、OpenAPI 一次性对齐，不拆开；
- **a5 必须独立**：前端域与后端域完全不同，混在一个对话里容易带着后端假设写前端；
- **3.6b 单独两个对话**：rag 模块与 user/security 模块不共享上下文。

每个对话收工必须交付：全量测试结果（真实数字）、独立提交、本文档「实现情况」回填、一段交接说明（下一对话只读 design-12 + 本文档 + 交接说明即可开工）。

---

## 5. 验收口径

```bash
# 后端
cd backend
mvn -s .mvn/mindora-settings.xml -gs .mvn/mindora-settings.xml test
# 前端
npm test --workspace=@mindora/admin-art
npm run typecheck
npm run lint
npm run build --workspace=@mindora/admin-art
# 契约
npm run api:sync && npm run api:check
```

实机必须走通的路径：

1. **登录 → 静默续期**：把 Access TTL 临时改为 60 秒，等过期后继续操作，页面不跳登录；
2. **登出 = 全端失效**：A 标签页登出，B 标签页下一次请求直接跳登录；
3. **禁用即时生效**：后台把账号置 disabled，已登录会话下一个请求被拒；
4. **多标签不互踢**：两个标签页同时触发刷新，都不掉线；
5. **会话归属**：管理端问答产生会话；他人 token 访问该会话返回 404；`persist=false` 的复测不写会话。

---

## 6. 依赖与风险

| 项 | 说明 |
| --- | --- |
| 发布顺序 | a3（后端）与 a5（前端）**同批发布**；后端过滤器兼容一版裸/带前缀 token，留改造窗口 |
| 令牌失效 | 加 `exp`/`tv` 后现有本地令牌全部失效，需重新登录一次（可接受） |
| 部署前提 | RT 走 HttpOnly Cookie，依赖**同站**：本地 vite 代理 `/api`、生产同域反代。改前端请求方式（a0）是这条的前提 |
| 顺序依赖 | 3.6b 与 3.6a 文件域不重叠，可并行；但公开站登录（4.7）必须在两者之后 |
| 未定项 | 「4.7 公开站登录 + 会话 UI」编号尚未写进路线看板，待确认后补 |

---

## 7. 实现情况

### 7.1 批次①（a0 + a1）已完成（2026-10-18）

**a0 前端请求同源化**（提交 `7222ae7`）

- 公开站 vite 增加 `/api` 代理（目标可用 `VITE_API_PROXY_URL` 覆盖，缺省本机 8080）；问答与文章请求改用相对路径，构建产物里不再出现 `localhost:8080`。
- 验证：site 单测 40 个用例全过（3 个文件；`rag.test.ts` 22 个，含新增的 2 条默认基地址断言）、`typecheck` 0 错误、`build` 成功、仓库 `lint` 0 警告。
- 实机（HTTP 级）：经 `http://localhost:5173` 代理请求文章接口返回 200；向问答流端点发空问题返回 400 校验错误，说明请求确实同源转发到后端。本轮未做浏览器手点问答（本机浏览器会话不可用），Network 面板口径留待下次界面联调补记。

**a1 V9 迁移 + 刷新令牌表仓储**（提交 `a66256c`）

- V9 只追加：`user_account.token_version`、`auth_refresh_token` 表、`auth_account` 密码身份幂等回填。仓储已就绪但尚未被业务调用，旧 JWT 登录链路零改动。
- 干净库：V1→V9 顺序执行成功；`token_version` 为 `int NOT NULL DEFAULT 0`，`auth_refresh_token` 的主键、唯一键与两个普通索引齐全。
- 现有开发库：启动时 Flyway 校验 9 个迁移通过并应用 V9；回填后 `provider='password'` 身份为 4 条，手工重跑同一段回填仍为 4 条（幂等）。删除态账号不参与回填。
- 旧登录未变：`admin@mindora.local` 登录返回 `success`，Access Token 仍是带 `Bearer ` 前缀的旧格式（换裸 token 属 a2/a3）。
- 后端全量：11 模块 `BUILD SUCCESS`，249 个测试 0 失败（mindora-user 10 个，其中新增仓储单测 7 个；mindora-app 46 个，含新增的 V9 迁移契约断言）。

### 7.2 批次②（a2 + a4 密钥部分）已完成（2026-10-18）

- 令牌实现换成 JJWT 0.13.0：Access JWT 带 `sub`（32 位 public id）、`email`、`roles`、`tv`、`iat`、`exp`（默认 30 分钟），返回值改为裸 JWS；过滤器同时接受 `Bearer <t>` 与裸 `<t>`，旧前端把登录串原样当请求头仍然可用。
- 刷新令牌生命周期落地（暂未接 HTTP）：32 字节随机串只存 SHA-256；轮换、60 秒宽限、超宽限重用检测并撤销整族、绝对 30 天上限、按用户撤销。
- 启动期密钥强校验：非 local 环境密钥为空、仍是占位符或短于 32 字符会直接拒绝启动；local 未配置则生成本次进程临时密钥并打 WARN（重启后需重新登录）。
- 验证：11 模块 `BUILD SUCCESS`，268 个测试 0 失败（mindora-user 29 个，新增令牌、密钥策略、刷新令牌共 19 个；mindora-app 46 个）。
- 实机：local 无 `JWT_SECRET` 启动出现临时密钥 WARN；登录返回裸 JWT（三段、`sub` 32 位、`tv=0`、`exp−iat=1800`、角色 `super_admin`）；`/api/v1/admin/me` 用裸串与 `Bearer` 前缀均 200，匿名 403。
- 实机：`prod` + 短密钥启动失败，报错明确要求 `JWT_SECRET` 至少 32 字符；`prod` + 48 字符合法密钥可正常启动。
- 已知影响：升级后旧令牌全部失效需重新登录；本地每次重启都会换临时密钥（design-12 §6 的既定行为）。

### 7.3 批次③（a3 + a4 状态与限流）已完成（2026-10-18）

**a3 认证端点与会话校验**（提交 `085f83c` / `2040b5f`）

- 新增 `refresh` / `logout` / `me`：登录与刷新下发 `mindora_rt`（HttpOnly、SameSite=Lax、Path=/api/v1/auth、Max-Age=14 天）；刷新先校验令牌、再校验账户状态与 `tv`、最后轮换；登出 `token_version+1` 并撤销全部 RT，Cookie 缺失也幂等成功。
- 每请求校验补上账户回查：账户不存在 → `token_invalid`，禁用/删除 → `account_disabled`，`tv` 不一致 → `token_revoked`。
- 401/403 统一 JSON：`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`，匿名访问管理端由 403 空体改为 401 `token_missing`；`/auth/me` 回到认证链。
- 过渡期规则：`register/login/refresh/logout` 不因过期 AT 失败（否则旧 token 会把修复动作本身挡住），其余路径按 401 处理。

**a4 登录状态、密码边界与限流**（提交 `0b48fd0`）

- 登录先验密码再查状态：密码错 → `invalid_credentials`，禁用/删除 → `account_disabled`（避免用错误码枚举邮箱）；注册密码 8–72。
- 认证限流：注册 5/小时/IP、登录 10/分钟/IP、刷新 60/小时/用户；Redis 后端不可用时 fail-open（与公开问答一致），测试与无 Redis 场景用进程内实现。

**验证（真实执行）**

- 全量：11 模块 `BUILD SUCCESS`，**290 个测试 0 失败**（批次②的 268 + 新增 22；mindora-user 40、mindora-app 57）。
- 实机全链路：登录返回 `roles/expiresIn` 且 Cookie 属性符合契约 → `me` 200（`displayName=admin`）→ refresh 换到新 AT/RT → logout → 旧 AT 得 401 `token_revoked`，旧 RT 得 401 `refresh_reused`。
- 实机 401/403：匿名管理端 401 `token_missing`；乱串 401 `token_invalid`；普通 USER 令牌访问管理端 403 `access_denied`。
- 实机禁用：数据库置 `disabled` 后，正确密码登录 401 `account_disabled`、错误密码仍是 `invalid_credentials`；该账号旧 AT 立即 401 `account_disabled`。
- 实机限流：`login-per-minute=1` 时第二次登录 429 `auth_rate_limited`。
- 契约：`api:sync` / `api:check` 通过，新增三条 auth 路径与 `AuthView.roles/expiresIn`、`AuthMeView`；`npm run typecheck`、`lint`、admin-art `build` 全绿。

**已知限制**

- 每请求 2 次查询（账户 + 角色），design-12 §7 已如实记录；后续可合并。
- 限流 fail-open：Redis 故障期间额度失效，属既定取舍。
- 本地临时密钥每次重启都会变，需重新登录（design-12 §6）。

### 7.4 批次④（a5 前端登录态）已完成（2026-10-19）

- Access Token 只在内存：新增 `auth/session.ts`（transport 注入、单飞刷新）与 `auth/http-transport.ts`（裸 axios 访问 refresh/logout，避免拦截器递归）；Pinia 不再持有任何令牌，localStorage 里的旧字段被忽略。
- 页面刷新靠 bootstrap：守卫首行 `await ensureSessionBootstrapped()`（模块级 Promise 记忆化），`main.ts` 提前触发；`isLogin` 每轮按内存令牌校正，旧持久化状态不会再把未续期页面误判为已登录。
- 请求统一 `Authorization: Bearer <at>`；401 `token_expired` 自动刷新并**重放一次**，其余 401 清会话并提示；登录/注册自身的 401 不触发全局登出，由登录页按业务码提示（凭据错/禁用/限流）。
- 登出先 best-effort 调 `/auth/logout` 再本地清理；403 `access_denied` 明确提示「该账号无后台权限」后回登录页。
- 登录页移除硬编码密码；富文本编辑器上传头改为上传前读取最新令牌，避免续期后快照过期。

**验证（真实执行）**

- 前端单测：新增 2 个文件 **11 个用例全过**（会话状态 5、拦截器 6）；`typecheck`、`lint`、admin-art `build` 全绿。
- 实机（Chromium，后端 `--mindora.auth.access-ttl=PT15S`）：
  1. 登录后刷新页面仍停留在原页面（bootstrap 用 Cookie 换回令牌）；
  2. 等 15 秒令牌过期后直接访问 `/#/system/user` 成功，无登录跳转（401 → 单飞刷新 → 重放）；
  3. 退出登录回登录页；再访问受保护地址被守卫拦回 `/#/auth/login?redirect=...`，证明 Cookie 已失效；
  4. 普通 USER 账号登录后台：控制台出现 `HttpError: 该账号无后台权限`（toast 同文案），随后回登录页，不再静默。

**已知限制**

- localStorage 中是否仍残留旧版 token 未做自动化断言（旧字段已不再读写，升级后首次登录即被覆盖/忽略）。
- 多标签页仍依赖后端 60 秒宽限，不做跨标签协调（design-12 §10）。
- 运行 admin-art 单测/构建会重写 `src/types/import` 与 `.eslintrc-auto-import.json`（上游 AutoImport 行为），提交前需还原这些生成物。

### 7.5 批次⑤（3.6b b1 + b2）已完成（2026-10-19）

**b1 共享会话组件**

- 新增 `ConversationTurns`：`resolve`（取记忆窗口；匿名、非法/非本人 id 一律按无会话）、`record` / `recordQuietly`（终态落库，异常 WARN 不阻断响应）、`effectiveId`（只回显真正属于当前用户的 id）。
- `ChatConversationService` 增加 `resolveHistoryIfOwned`，把「非法 id」与「合法但暂无历史」分开；原 `resolveHistory` 行为不变。
- 公开控制器删掉四处私有会话实现，改用共享组件。两处行为修正：非法 id 在流式路径不再变成 `error`（按新会话处理）；落库失败不再中断 `done`。

**b2 管理端接入**

- `RagAdminController` 请求体新增 `persist`（缺省 `true`）：合法 `conversationId` 注入历史；`persist=true` 落库并回传 id；`false` 只读历史、不写库，合法续聊仍回显同一 id。
- OpenAPI `RagAnswerRequestView` 与生成类型同步增加 `persist`。

**验证（真实执行）**

- 单测 6 项（`ConversationTurnsTest`）+ 集成测试 5 项（`RagAdminConversationTest`）；`PublicRagControllerTest` 8 项原样全绿。
- 全量后端 `mvn test`：**BUILD SUCCESS**（app 模块 62 项、rag 模块 106 项，0 失败）；`npm run api:check` 通过。
- 实机 curl（dev MySQL）：
  1. 管理端首问返回 `conversationId=c055…`，会话详情 2 条消息；
  2. 带该 id 追问：同 id 返回，详情变 4 条（历史被注入）；
  3. `persist=false` 带合法 id：同 id 返回，消息数保持 4（未写库）；
  4. `conversationId=not-a-uuid`：200，返回新 id，列表新增 1 个会话（不报错）；
  5. `persist=false` 不带 id：`conversationId` 为空，会话列表不增加；验收产生的会话已软删清理。

**已知限制**

- 会话 CRUD 仍在 `/api/v1/public/rag/conversations*`，迁移与旧路径删除归 b3。
- 管理端 `scope` 仍只做缺省值填充，未限制取值（b4）。
- 本批不产出管理端界面（界面归 4.3 复测台）。

### 7.6 批次⑥（3.6b b3 + b4）已完成（2026-10-19）

**b3 会话 CRUD 迁移**

- 新增 `RagConversationController`：`GET /api/v1/rag/conversations`、`GET /api/v1/rag/conversations/{id}/messages`、`DELETE /api/v1/rag/conversations/{id}`；登录态、按 `userId` 行级隔离，他人/不存在统一 404。
- 公开控制器删除三端点、三个视图与 `ChatConversationService` 依赖；`/api/v1/rag/**` 在安全链显式要求登录。
- 语义修正：列表匿名由「返回空」改为 401 `token_missing`；`chat_conversation_not_found` 映射 404、`chat_user_required` 映射 401。

**b4 scope 白名单**

- 管理端 `scope` 仅接受 `all`/`public`（trim 后严格匹配），非法值在触达领域服务前返回 400 `rag_scope_invalid`；公开入口仍无 scope 字段（客户端传入被忽略）。

**验证（真实执行）**

- 新增 `RagConversationControllerTest` 5 项、`RagAdminScopeTest` 5 项；全量后端 `mvn test` **BUILD SUCCESS**（app 模块 72 项、rag 模块 106 项，0 失败）。
- `npm run api:sync` + `api:check` 通过；OpenAPI 旧会话路径已消失、新路径 3 个。
- 实机 curl：新路径列表/详情正常；匿名 401 `token_missing`；旧路径 404；USER 访问他人会话详情/删除均 404；USER 调管理端 403 `access_denied`；`scope=secret` 400；`scope=public` 200；`persist=false` 不写库；验收会话已清理。

### 7.7 Stage 3.6 最终收口（2026-10-19）

**全量门禁**

- 后端：`mvn test` BUILD SUCCESS（app 72、rag 106，其余模块全过）。
- 前端：admin-art 11 项、site 40 项单测全过；`typecheck`、`lint`、admin-art `build` 全绿。
- 契约：`api:sync` 后 `api:check` 通过。

**§5 五条实机路径（Chromium + curl）**

1. **登录 → 静默续期**：后端 `access-ttl=15s`；登录后 F5 仍停留原页；等过期后访问 `/#/system/user` 成功（401 → 单飞刷新 → 重放）。
2. **登出 = 全端失效**：标签 A 调用登出后，标签 B 下一次数据导航被拦回 `/#/auth/login?redirect=...`。
3. **禁用即时生效**：测试账号置 disabled 后，同一 Access Token 的下一请求返回 401 `account_disabled`；恢复后 200。
4. **多标签不互踢**：两个标签页在令牌过期后分别导航，均保持登录（60 秒宽限 + 前端单飞）。
5. **会话归属**：管理端问答产生会话；新路径列表/详情正常；他人 token 404；`persist=false` 不写；旧公开路径 404。

**收口状态**

- Stage 3.6 标记完成；`stage/3.6` 标签随收口提交打上。
- 已知限制：开发库残留批次④/⑥验收用的普通账号与测试数据；4.7 公开站登录与会话 UI 尚未开始。
