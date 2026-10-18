# 设计 12：身份与令牌生命周期（双令牌认证）

> 一句话用途：定义账号体系的登录态契约——Access/Refresh 双令牌、轮换与撤销、对外端点、数据迁移与前端约定。
> 状态：**生效（方案定稿，实现见 Stage 3.6a）**　维护日期：2026-10-18
> 相关：产品范围 `design-01`；架构 `design-02` §Authentication；会话域 `design-11` §15.5；阶段台账 `stage-3.6-task-manifest-cn.md`。

## 1. 结论先行

| 决策 | 内容 | 理由 |
| --- | --- | --- |
| 双令牌 | Access Token（JWT，**30 分钟**）+ Refresh Token（**14 天滑动、30 天绝对**） | 既不让用户频繁重登，又不让会话无限延长 |
| Refresh 形态 | **不透明随机串**（32 字节），服务端只存 SHA-256 | 轮换/作废/重用检测都需要状态，JWT 自包含反而是障碍 |
| JWT 库 | **JJWT 0.13.0**（`jjwt-api` / `jjwt-impl` / `jjwt-jackson`） | 成熟、API 简洁；HS256 一个密钥够用 |
| 存放 | Access **只在前端内存**；Refresh 存 **HttpOnly Cookie** | XSS 拿不到 refresh，AT 泄露窗口仅 30 分钟 |
| 撤销 | `user_account.token_version`（JWT 带 `tv`） | 一次 UPDATE 全网失效，登出/禁用共用 |
| 注册 | **开放注册**；身份表预留多 provider | 后续接 Google / GitHub 只加一行 `auth_account`，不迁移 |

## 2. 令牌模型

### 2.1 Access Token（JWT）

| 字段 | 值 |
| --- | --- |
| `alg` / `typ` | `HS256` / `JWT` |
| `sub` | 用户 public id（**32 位无连字符**，与 `PublicIds` 一致） |
| `email` | 登录邮箱 |
| `roles` | 角色数组（如 `["super_admin"]`） |
| `tv` | 签发时的 `token_version` |
| `iat` / `exp` | 签发时间 / 到期时间（TTL 30 分钟） |

> 注意：现状 `SimpleJwtTokenService` 的 `sub` 是 36 位带连字符 UUID，且返回值自带 `"Bearer "` 前缀。本契约统一为 **32 位 public id + 裸 token**（前缀由客户端拼）。

### 2.2 Refresh Token

- 32 字节随机 → base64url（无填充），只回给客户端一次；
- 服务端存 `SHA-256(token)` 十六进制；
- Cookie 属性：

```text
Set-Cookie: mindora_rt=<opaque>; Path=/api/v1/auth; HttpOnly; SameSite=Lax; Max-Age=1209600
（生产环境追加 Secure）
```

## 3. 生命周期与轮换

```text
登录 ──► 新家族(family_id) ──► RT(exp = now+14d)
  │
  ├─ 每 30 分钟（或 401 token_expired）─► POST /auth/refresh
  │        └─ 旧 RT 标记 rotated_at，发新 RT（同 family_id，exp = now+14d）
  │
  ├─ 宽限 60s 内旧 RT 再用 ──► 允许（多标签页并发）
  ├─ 超宽限用已轮换 RT ──────► 判定泄露：撤销整个家族，返回 refresh_reused
  ├─ 家族年龄 > 30 天 ──────► refresh_absolute_expired（强制重登）
  └─ 登出 ──────────────────► token_version+1 + 撤销该用户全部 RT（全端失效）
```

四条规则：

1. **滑动有限度**：`expires_at` 每次刷新重置为 `now + 14d`，但家族出生时间不变，**最长 30 天**；
2. **宽限期**：`rotated_at` 后 60 秒内，旧 token 仍可正常刷新（避免两个标签页同时刷新互相踢掉）；
3. **重用检测**：超过宽限使用已轮换/已撤销 token → 撤销该 `family_id` 全部记录（不动其他设备），返回 `refresh_reused`；
4. **登出 / 禁用 / 删除**：`token_version + 1`（Access 立即失效）+ 撤销该用户所有 RT。

## 4. 端点契约

| 端点 | 鉴权 | 请求 | 响应 | 说明 |
| --- | --- | --- | --- | --- |
| `POST /api/v1/auth/register` | 匿名 | `{email, password}` | `AuthView` + RT Cookie | 开放注册；IP 限流 5 次/小时；自动登录 |
| `POST /api/v1/auth/login` | 匿名 | `{email, password}` | `AuthView` + RT Cookie | IP 限流 10 次/分钟；新建家族 |
| `POST /api/v1/auth/refresh` | RT Cookie | 无请求体 | `AuthView` + 新 RT Cookie | 轮换；不需要 Access Token |
| `POST /api/v1/auth/logout` | RT Cookie | 无请求体 | 空 | 全端失效 + 清 Cookie；Cookie 缺失也返回成功（幂等） |
| `GET /api/v1/auth/me` | AT | — | `{userId, email, displayName, roles}` | 前台用户获取自身信息；匿名 401 |

`AuthView`：

```json
{ "userId": "…", "email": "…", "roles": ["user"], "accessToken": "…", "expiresIn": 1800 }
```

### 4.1 401 错误码（前端据此决定「续期」还是「跳登录」）

| code | 触发 | 前端动作 |
| --- | --- | --- |
| `token_expired` | AT 的 `exp` 已过 | 触发一次 refresh 并重放原请求 |
| `token_invalid` | 签名错 / 格式错 / 缺少前缀 | 清状态 → 登录页 |
| `token_revoked` | `tv` 与库中不一致 | 清状态 → 登录页 |
| `account_disabled` | 账号被禁用或已删除 | 清状态 → 登录页 + 提示 |
| `refresh_missing` / `refresh_invalid` / `refresh_expired` | RT 缺失 / 查不到 / 过期 | 清状态 → 登录页 |
| `refresh_absolute_expired` | 家族超过 30 天 | 同上，提示「登录已超时」 |
| `refresh_reused` | 重用已轮换的 RT | 同上，撤销家族 |
| `invalid_credentials` / `account_disabled` | 登录失败 | 停留在登录页 |

所有 401/403 响应必须是**统一 JSON 体**（含 `code`），不能是空体——现状由 Spring 默认 `AuthenticationEntryPoint` 返回空，需替换。

## 5. 数据模型（V9，只追加）

```sql
ALTER TABLE user_account ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE auth_refresh_token (
  id               CHAR(32)     NOT NULL,
  user_id          CHAR(32)     NOT NULL,
  token_hash       CHAR(64)     NOT NULL,
  family_id        CHAR(32)     NOT NULL,
  family_issued_at TIMESTAMP(6) NOT NULL,
  token_version    INT          NOT NULL,
  issued_at        TIMESTAMP(6) NOT NULL,
  expires_at       TIMESTAMP(6) NOT NULL,
  rotated_at       TIMESTAMP(6) NULL,
  revoked_at       TIMESTAMP(6) NULL,
  revoked_reason   VARCHAR(32)  NULL,
  replaced_by      CHAR(32)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_family (family_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 密码身份回填（幂等）：一个账号一条 provider='password'
INSERT INTO auth_account (id, user_id, provider, provider_subject, password_hash, created_at, updated_at, deleted)
SELECT REPLACE(UUID(), '-', ''), u.id, 'password', u.id, u.password_hash, u.created_at, u.updated_at, FALSE
FROM user_account u
WHERE u.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM auth_account a WHERE a.user_id = u.id AND a.provider = 'password');
```

- `user_account.password_hash` **保留一版**（回滚安全），代码停止写入后标注 deprecated；
- `auth_account` 之前建好但零使用，本次开始承载密码身份；将来 OAuth 只增加 `provider='google'|'github'` 行。

## 6. 配置项

| 配置 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `mindora.auth.jwt.secret` | `JWT_SECRET` | 无（本地自动生成） | ≥32 字符；非 local 环境为空/默认值/过短 → **拒绝启动** |
| `mindora.auth.access-ttl` | `AUTH_ACCESS_TTL` | `PT30M` | Access 有效期 |
| `mindora.auth.refresh-ttl` | `AUTH_REFRESH_TTL` | `P14D` | Refresh 滑动有效期 |
| `mindora.auth.refresh-absolute-ttl` | `AUTH_REFRESH_ABSOLUTE_TTL` | `P30D` | 家族绝对上限 |
| `mindora.auth.refresh-grace` | `AUTH_REFRESH_GRACE` | `PT60S` | 轮换宽限 |
| `mindora.auth.cookie.secure` | `AUTH_COOKIE_SECURE` | `false`（prod 建议 true） | Cookie Secure 标记 |
| `mindora.auth.rate-limit.register-per-hour` | — | `5` | 按 IP |
| `mindora.auth.rate-limit.login-per-minute` | — | `10` | 按 IP |
| `mindora.auth.rate-limit.refresh-per-hour` | — | `60` | 按用户 |

## 7. 服务端校验顺序（每个受保护请求）

1. 无 `Authorization` → 按匿名继续（是否放行由 `SecurityConfiguration` 决定）；
2. 解析并验签（JJWT）：过期 → `token_expired`；其他异常 → `token_invalid`；
3. 取 `sub` / `tv` → 查 `user_account`：
   - 不存在 → `token_invalid`；
   - `status != 'active'` 或 `deleted` → `account_disabled`；
   - `tv != token_version` → `token_revoked`；
4. 写入 `Authentication`（`ROLE_*`），继续链路。

> 每请求一次主键查询，换取「禁用/登出立即生效」；这是有意取舍，不引入缓存。

## 8. 前端约定（admin-art 先落地，`site` 在 4.7 复用同一逻辑）

| 时机 | 行为 |
| --- | --- |
| 应用启动 | **session bootstrap**：`POST /auth/refresh`（携带 Cookie）。成功 → 把 Access 放内存、标记已登录；失败 → 匿名。**必须早于路由守卫的登录判断**，否则刷新页面会被误踢 |
| 每个请求 | 注入 `Authorization: Bearer <access>`；**请求地址一律用相对路径 `/api/...`**（同源，见 §9） |
| 收到 401 `token_expired` | **单飞** refresh（并发请求共享同一个 Promise）→ 成功后重放原请求（**最多一次**） |
| 任意其他 401 | 清空用户状态 → 登录页 |
| 登出 | `POST /auth/logout` → 清状态 → 登录页 |
| 后台 403 | USER 账号进入后台时明确提示「该账号无后台权限」，不再静默回到登录页 |

Access Token **不写 localStorage**，只存内存；刷新页面靠 bootstrap 静默续期。

## 9. 安全与护栏

| 项 | 值 |
| --- | --- |
| 密码 | 8–72 位（现状仅 `@NotBlank`） |
| 邮箱 | trim + 小写归一（已有） |
| Refresh 熵 | 32 字节随机，库里只有 SHA-256 |
| 登录限流 | 10 次/分钟/IP |
| 注册限流 | 5 次/小时/IP |
| 刷新限流 | 60 次/小时/用户 |
| CORS | **不需要**：前端统一相对路径 `/api`，浏览器视为同源（本地靠 vite 代理、生产靠同域反代）。跨源直连是明确不走的路 |
| 部署前提 | **前后端同站**：本地 `vite` 把 `/api` 代理到 `http://localhost:8080`；生产由 Nginx 同域反代 `/api`。Cookie 因此是**第一方 Cookie**，无 `SameSite=None`、无 CORS 凭证问题 |

## 10. 已知限制与取舍

1. **多标签页并发**依赖 60 秒宽限 + 前端单飞，极端情况下仍可能要求重新登录；跨标签协调（`BroadcastChannel`）暂不做；
2. **每请求一次 `tv` 查询**，接受；若成为瓶颈再加 5–10 秒短缓存（代价是禁用生效延迟）；
3. **Cookie 绑定同站部署**，这是明确前提，不是隐藏假设；
4. **不做设备管理**（每设备撤销、登录设备列表），登出就是全端失效；
5. **不做 OAuth 实现**：本轮只保留 `auth_account` 多 provider 结构，Google / GitHub 接入另开阶段。

## 11. 验收要点

| 类型 | 用例 |
| --- | --- |
| 单测 | AT 过期 → `token_expired`；`tv` 变更 → `token_revoked`；禁用账号登录被拒；轮换成功且旧串宽限内可用；超宽限重用 → 家族撤销；无 Cookie → `refresh_missing`；限流触发；密码/邮箱边界 |
| 实机 | 把 AT TTL 配成 60 秒：过期后页面无感续上；登出后另一标签页下次请求跳登录；禁用账号立即失效；两个标签页同时刷新不掉线 |
| 回归 | 后端 11 模块全绿；前端 `typecheck` / `lint` / `build` |

## 12. 发布与迁移

- **前后端必须同批发布**：Token 由「含 Bearer 前缀」改为裸 token，旧前端会拼错；
- 为留缓冲，后端过滤器**兼容一版**：接受 `Bearer <t>` 与裸 `<t>`，下个阶段移除；
- 加 `exp` / `tv` 后，现有本地令牌全部失效，需要重新登录一次（可接受）；
- V9 迁移只追加，回填幂等。
