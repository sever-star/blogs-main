# 登录鉴权评审 · blog 系统

> 版本：v1.0　|　日期：2026-09-23
> 对象：`/api/auth/**` 全链路（Controller → Service → Repository → 拦截器）
> 结论性质：仅记录评审结论与解决方案，**不修改任何代码**；落地顺序见第 5 节
> 关联：`docs/database-review.md`（P1-1 role、P1-3 refresh_token 明文）

---

## 0. 总体评价

这套双 token 鉴权的**骨架是对的**，以下几点应当保持，改造时不要动：

| # | 做对的地方 | 说明 |
|---|---|---|
| G-1 | **登录错误不区分用户名/密码** | 统一 `BusinessException(401, "用户名或密码错误")`，防用户名枚举 |
| G-2 | **refresh token 只走 HttpOnly Cookie** | 不进响应体，JS 读不到，XSS 偷不走 |
| G-3 | **refresh 时轮换旧 token** | 撤销旧的再发新的，降低重放价值 |
| G-4 | **access / refresh 有效期分离** | 15 分钟 / 7 天，短命凭证 + 长命续期，标准做法 |
| G-5 | **`UserMapper` 全部 `ignoreByDefault = true`** | 配合三个分离的 Request DTO，注册/更新路径在**类型层面**就够不到 `role`/`status`/时间戳 |
| G-6 | **注册的 NOT NULL 列交给 DB 默认值** | 代码不显式写 `role='USER'`，少一个可能写错的地方 |

核心问题集中在：**角色能力未落地、token 类型未校验、刷新未查库**。

---

## 1. 现有链路（摘要）

### 1.1 组件与职责

| 组件 | 位置 | 职责 |
|---|---|---|
| `BlogUserController` | `controller/` | `/api/auth` 六个端点：login / register / me(GET) / me(PUT) / refresh / logout |
| `BlogUserService` | `service/` | 登录、注册、取用户、更新资料、登出 |
| `RefreshTokenService` | `service/` | refresh token 落库、校验、撤销 |
| `JwtUtils` | `util/` | 签发 / 解析 / 校验 JWT |
| `JwtInterceptor` | `util/` | **唯一鉴权入口**：验签名 + 验过期 |
| `WebConfig` | `config/` | 注册拦截器 + 放行规则 + CORS |
| `SecurityBeanConfig` | `config/` | 只提供 `BCryptPasswordEncoder`（**未引入 spring-boot-starter-security**） |

> 注意：`pom.xml` 刻意只引了 `spring-security-crypto`，没有 `spring-boot-starter-security`。
> 因此 **`@PreAuthorize` / `@Secured` 等注解不可用**，角色判断只能写在拦截器里。

### 1.2 Token 设计

| | access token | refresh token |
|---|---|---|
| 有效期 | `jwt.access-expiration` = 900000 ms（15 分钟） | `jwt.refresh-expiration` = 604800000 ms（7 天） |
| 存放 | 前端自持（响应体返回） | HttpOnly Cookie，path=`/api/auth` |
| 服务端状态 | **无状态**，不落库 | **有状态**，落 `blog_refresh_tokens` |
| 可否吊销 | ❌ 只能等过期 | ✅ `revoked = 1` |
| claims | `sub`=userId、`username`、`type`、`jti`、`iat`、`exp` | 同左，`type = "refresh"` |

### 1.3 五条链路

```
注册  POST /api/auth/register
      RegisterRequest → UserMapper.register() → 查重 → BCrypt → insert
      → 生成 access token（不生成 refresh、不种 cookie）→ LoginResponse

登录  POST /api/auth/login
      LoginRequest → UserMapper.login() → findByUsername → BCrypt 比对
      → 生成 access + refresh → saveRefreshToken 落库 → 种 cookie
      → LoginResponse{accessToken, user}

访问 任意受保护接口
      请求 → JwtInterceptor.preHandle → OPTIONS 放行 / 取 Authorization
      → 去 "Bearer " → getUserIdFromToken（验签 + 验过期）→ true
      ⚠️ 不查库、不校验角色、不校验 token type

刷新  POST /api/auth/refresh
      @CookieValue 读 refresh → getUserIdFromToken / getUsernameFromToken
      → revokeRefreshToken(旧) → 生成新的 → 落库 → 种新 cookie
      ⚠️ verifyRefreshToken 被注释掉了（BlogUserController 第 90 行）

登出  POST /api/auth/logout
      @CookieValue 读 refresh → revokeRefreshToken → 清 cookie
      ⚠️ access token 无法作废，仍有效至过期（≤15 分钟）
```

### 1.4 放行规则（`WebConfig`）

```
addPathPatterns("/**")
excludePathPatterns(
    /api/auth/login      无需登录
    /api/auth/register   无需登录
    /api/auth/refresh    靠 refresh cookie ✅
    /api/auth/logout     同上 ✅
    /api/tags            游客可读
    /api/categories      游客可读
    /api/posts           ⚠️ 见 P0-1
    /api/posts/*         ⚠️ 见 P0-1
    /api/ai/**           游客可用，改用 AiRateLimiter 按 IP 限流
)
```

**放行是按路径的，不是按 HTTP 方法的。** 而 `BlogPostController` 的写接口正好落在这两个模式里：

| 方法 | 路径 | 现状 | 应该 |
|---|---|---|---|
| `GET` | `/api/posts` | 放行 | ✅ 游客可读 |
| `GET` | `/api/posts/{id}` | 放行 | ✅ 游客可读 |
| `POST` | `/api/posts` | 放行 | ❌ 应要求 ADMIN |
| `PUT` | `/api/posts/{id}` | 放行 | ❌ 应要求 ADMIN |
| `DELETE` | `/api/posts/{id}` | 放行 | ❌ 应要求 ADMIN |

---

## 2. 问题分级

| 级别 | # | 问题 | 后果 |
|---|---|---|---|
| **P0** | P0-1 | **角色能力完全未落地** | 任何人（甚至不带 token）可发/改/删文章 |
| **P0** | P0-2 | **access / refresh token 可混用** | refresh token 可当 Bearer 用，7 天有效期绕过 15 分钟设计 |
| **P1** | P1-1 | **刷新时不查库** | `verifyRefreshToken` 被注释；已撤销 token 仍可续期（靠副作用侥幸挡住） |
| **P1** | P1-2 | **登出非幂等，重复登出返回 500** | 旧 token 登出 → `RuntimeException` → 500 |
| **P1** | P1-3 | **refresh token 明文落库** | 库泄露 = 所有活跃 token 被盗，且叠加 P0-2 可直接访问接口 |
| **P1** | P1-4 | **`last_login_at` 从未写入** | 列存在、接口返回，但永远为 null |
| **P1** | P1-5 | **`updated_at` 永不更新** | 无触发器，`DEFAULT CURRENT_TIMESTAMP` 只在 INSERT 生效 |
| **P2** | P2-1 | **注册不返回 refresh token** | 前端若按登录处理，注册用户 15 分钟后被登出且无法续期 |
| **P2** | P2-2 | **Cookie `secure=false` 硬编码** | 生产环境 refresh cookie 明文传输 |
| **P2** | P2-3 | **`status` 有值但无人读** | 账号禁用（`status=0`）后仍可登录、仍可访问接口 |
| **P2** | P2-4 | **CORS `allowedOriginPatterns("*")` + `allowCredentials(true)`** | 配合 cookie 认证过于宽松 |
| **P3** | P3-1 | 无登录日志写入 | `blog_login_logs` 表存在但从未写入 |
| **P3** | P3-2 | `device_id` 恒为 null | `saveRefreshToken` 调用处传 null，多端管理无数据基础 |

---

## 3. 解决方案

### P0-1　角色能力落地

**目标**：让 `role` 真正参与判断，且 ADMIN 无法通过注册接口获得。

**改法分三块。**

**① 拦截器内做三级判断**（`JwtInterceptor`）

`excludePathPatterns` 不支持按 HTTP 方法区分，所以把 `/api/posts`、`/api/posts/*` **从放行列表移除**，改在拦截器里按方法判断：

```
preHandle:
  1. OPTIONS                              → 放行（CORS 预检）
  2. 路径 ∈ PUBLIC_PATHS                  → 放行（login/register/refresh/logout/tags/categories/ai）
  3. 路径 ∈ PUBLIC_READ_PATHS 且方法=GET  → 放行（/api/posts/**）
  4. 其余：
       验 token → 取 userId
       查库取 user
       校验 status == 1        （见 P2-3）
       若 方法 ∈ {POST, PUT, PATCH, DELETE} 且 路径 ∈ ADMIN_ONLY_PATHS
           → 校验 role == "ADMIN"，否则 403
       放行
```

建议把路径规则集中成常量表放在 `JwtInterceptor` 或独立的 `AuthRules` 类里，别散在 `WebConfig` 和拦截器两处。

**② 角色的取得方式：查库，而不是塞进 token**

| 方案 | 优点 | 缺点 |
|---|---|---|
| (a) 拦截器里 `blogUsersDao.findById(userId)` 查库 | 降权**立即生效**；可顺带校验 `status` | 每请求多一次主键查询 |
| (b) role 放进 access token claim | 省一次查询 | **降权有 ≤15 分钟窗口**，期间旧 token 仍是 ADMIN |

**推荐 (a)**。个人博客量级下一次主键查询完全无感，换来"改完立即生效"和"能顺便校验账号状态"。方案 (b) 的窗口在提权时无害，但降权时是真实的越权窗口。

**③ ADMIN 的来源：配置白名单 + DB 默认值兜底**

```yaml
blog:
  admin-usernames: [syt]
```

- 注册路径：`BlogUserService.register` 中判断用户名是否命中白名单，命中才 `setRole("ADMIN")`，否则**不设 role**，交给 `DEFAULT 'USER'`。
- 兜底：即使配置写错/漏配，DB 默认值也保证注册产物永远是 USER。
- 运维路径：`UPDATE blog_users SET role = 'ADMIN' WHERE username = 'syt';`

**明确不建议"首个注册用户即 ADMIN"**：把权限绑在注册顺序上，库被清空重建或部署到公网后自己尚未注册时，博主身份会落到别人手里。

**④ 顺手修掉一个重复解析**

拦截器验完 token 后把 `userId` 放进 `request.setAttribute("userId", id)`，`BlogUserService.getUser/update` 就不必再 `authorization.replace("Bearer ", "")` 解析一遍。

**影响面**：`WebConfig`、`JwtInterceptor`、`BlogUserService`、`application-dev.yaml`
**注意**：改造后现有 `syt` 账号必须先提权成 ADMIN，否则本地发不了文章。

---

### P0-2　token 类型校验

**问题**：`generateToken` 与 `generateRefreshToken` 签发的 token 结构完全相同、同一密钥签名，`type` claim 也写了，但**全链路没有任何地方读它**。

**后果**：
- refresh token 当 Bearer 用 → `JwtInterceptor` 只验签名和过期 → **通过**。于是 7 天有效期的 refresh token 直接变成了接口访问凭证，15 分钟的设计被绕过。
- 反方向（access token 拿去 `/refresh`）**侥幸不可用**：因为 `revokeRefreshToken` 会 `findByToken` 查库，不在库里就抛异常。但这是"副作用挡住的"，不是设计。

**改法**：在 `JwtUtils` 里把"解析"和"校验类型"绑在一起，不暴露裸的解析方法：

```java
// 新增
public Integer getUserIdFromAccessToken(String token)   // 解析后校验 type == "access"，否则抛
public Integer getUserIdFromRefreshToken(String token)  // 解析后校验 type == "refresh"，否则抛
```

- `JwtInterceptor` 改用 `getUserIdFromAccessToken`
- `BlogUserController.refresh` 改用 `getUserIdFromRefreshToken`
- 原来的 `getUserIdFromToken` / `getUsernameFromToken` 可保留为私有实现，不再对外暴露

**关键点**：类型校验必须在**解析 token 的那一步**做掉，而不是让调用方记得去查。前者漏不掉，后者一定会漏。

---

### P1-1　刷新时查库

**改法**：把 `BlogUserController.refresh` 第 90 行注释掉的 `verifyRefreshToken` 恢复，并调整顺序为**先查库、再解析**：

```
1. verifyRefreshToken(token)
     ├─ 校验 type == "refresh"     （P0-2）
     ├─ 查库：存在？revoked == 0？expires_at > now？
     └─ 校验签名与过期
2. 取 userId / username
3. revokeRefreshToken(旧)  → 轮换
4. 生成新的 access + refresh，落库，种 cookie
```

同时把 `RefreshTokenService` 里所有 `throw new RuntimeException(...)` 换成 `BusinessException`：

| 场景 | 现状 | 应改为 |
|---|---|---|
| token 不存在 | `RuntimeException` | `BusinessException(TOKEN_INVALID, "刷新令牌无效")` |
| 已被撤销 | `RuntimeException` | `BusinessException(TOKEN_INVALID, "刷新令牌已失效")` |
| 已过期 | `RuntimeException` | `BusinessException(TOKEN_EXPIRED, "刷新令牌已过期")` |
| 签名无效 | `RuntimeException` | `BusinessException(TOKEN_INVALID, "刷新令牌无效")` |

原因：`GlobalExceptionHandler.handleRuntime` 会把这些兜成 **HTTP 500 + code 401**，语义错误且会污染错误监控。业务失败不该走 500。

---

### P1-2　登出改为幂等

**问题**：`logout` → `revokeRefreshToken` → token 不在库中 → `throw RuntimeException` → **500**。
触发场景很常见：刷新后旧 token 已被轮换，用户拿旧 cookie 再登出。

**改法**：`revokeRefreshToken` 改成幂等——查不到就静默返回，不抛异常。

登出的目标状态是"这个 token 不再有效"，**已经无效就是成功**。幂等也意味着重复点击登出不会报错。

---

### P1-3　refresh token 存哈希

**问题**：`blog_refresh_tokens.token` 存 JWT 原文（`VARCHAR(500)`），库被拖走 = 所有活跃 refresh token 泄露。叠加 P0-2 后可直接当 access token 用。

**改法**：落库前取 SHA-256 摘要，`findByToken` 改为按摘要查。

```
写入：token_hash = SHA-256(jwt 原文)
查询：SELECT ... WHERE token_hash = SHA-256(传入的 jwt)
```

**不需要加盐**：JWT 是签名过的高熵随机串，不是弱口令，加盐无收益。普通的 SHA-256 即可。

**DDL**：`blog_refresh_tokens` 增加 `token_hash CHAR(64)`，建唯一索引；确认数据迁移后删除或废弃 `token` 列。

> 这一条与 `docs/database-review.md` 的 P1-3 是同一条，那边记的是"代码层改，非 DDL"，实际落地需要一次 DDL 配合。

---

### P1-4　写入 `last_login_at`

**改法**：`BlogUserService.login` 中 BCrypt 比对成功后：

```java
existing.setLastLoginAt(LocalDateTime.now());
blogUsersDao.update(existing);
```

**注意**：必须**从 `existing` 出发**再更新，不能直接 `update(登录时构造的 pojo)`。原因见 `docs/log.md` 中「jOOQ 对可空列会写 NULL」那条——直接 update 部分字段的 pojo 会把 `bio`/`website`/`github`/`weibo` 等可空列清空。

---

### P1-5　`updated_at` 自动更新

**问题**：`blog_users` 等表没有触发器，`DEFAULT CURRENT_TIMESTAMP` 只在 INSERT 生效。所有 UPDATE 路径都不会刷新 `updated_at`。

**改法二选一**：

| 方案 | 做法 | 评价 |
|---|---|---|
| (a) 代码层 | 在 Repository 的 update 方法里统一 `setUpdatedAt(now())` | 每张表每处都要记得写，容易漏 |
| **(b) 数据库触发器**（推荐） | `BEFORE UPDATE ... FOR EACH ROW EXECUTE FUNCTION set_updated_at()` | **一处搞定所有表、所有写入路径**，包括将来手写的运维 SQL |

推荐 (b)：写一个通用函数，给所有带 `updated_at` 的表挂上触发器。追加到 `docs/supplementary-schema.sql`。

---

### P2-1　注册后自动登录

**问题**：`register` 返回 `LoginResponse{accessToken, user}`，结构上和登录一致，但**不种 refresh cookie**。前端若同等对待，注册用户会在 15 分钟后被登出，且没有 refresh token 可换。

**改法二选一**：

| 方案 | 做法 | 评价 |
|---|---|---|
| **(a) 注册即登录**（推荐） | `register` 里也生成 refresh token、落库、种 cookie，返回结构与 login 完全一致 | 前端体验最好，语义自洽 |
| (b) 注册只返回 user | 不返回 token，前端跳登录页 | 语义最清晰，但多一步操作 |

选 (a) 需要给 `BlogUserController.register` 补 `HttpServletRequest` / `HttpServletResponse` 参数，并复用登录那套 cookie 逻辑——建议把 `setRefreshTokenCookie` / `clearRefreshTokenCookie` 从 Controller 私有方法提到一个 `AuthCookieHelper`，避免两处重复。

---

### P2-2　Cookie `secure` 与 `SameSite`

**问题**：`setRefreshTokenCookie` 硬编码 `secure=false`；且用的是 `jakarta.servlet.http.Cookie`，**该类不支持 SameSite**。

**改法**：

```yaml
jwt:
  cookie-secure: false   # dev；prod 覆盖为 true
```

改用 `ResponseCookie`（`org.springframework.http`）替代 `Cookie`，以便同时设置：
- `httpOnly(true)`
- `secure(${jwt.cookie-secure})`
- `sameSite("Lax")`
- `path("/api/auth")`

`SameSite=Lax` 足以挡住跨站 POST 携带 cookie，且不影响正常导航。

---

### P2-3　账号禁用校验

**问题**：`blog_users.status`（0=禁用 / 1=正常，见 `enum-dictionary.md` 2.3）有值，但**没有任何地方读它**。禁用账号仍可登录、仍可访问接口。

**改法**两处都要加：

1. **登录时**：`BlogUserService.login` 比对密码通过后，`status != 1` → `BusinessException(403, "账号已被禁用")`
2. **拦截器里**：P0-1 已经要查库取 role，顺手校验 `status == 1`，否则 403

第 2 处是关键——否则已登录用户被禁用后，手上的 token 在过期前仍能访问。

---

### P2-4　CORS 收紧

**问题**：`allowedOriginPatterns("*")` + `allowCredentials(true)`。浏览器默认 `SameSite=Lax` 挡掉了一部分跨站 POST，但这是"靠默认值兜着"，不是显式设计。

**改法**：改成配置项。

```yaml
blog:
  cors:
    allowed-origins: [http://localhost:5173]   # dev；prod 换成真实域名
```

配合 P2-2 的 `SameSite=Lax`，形成双层防护。

---

### P3-1　登录日志

`blog_login_logs` 表已存在但从未写入。建议在 `BlogUserService.login` 的成功/失败分支各记一条（成功记 userId，失败记尝试的用户名 + IP + UA），用于异常登录排查。

**注意**：失败分支的记录**不要记录密码**，哪怕加密后的也不要。

### P3-2　`device_id`

`saveRefreshToken` 的 `deviceId` 参数在三处调用（login、refresh、以及 P2-1 新增的 register）中**全部传 null**。表结构留了 `device_id VARCHAR(100)`，显然是为了多端会话管理（"查看我的登录设备"、"踢掉某台设备"）。当前无数据基础。

**建议**：要么补上（登录时由前端传一个稳定的设备标识，或用 `User-Agent` + IP 派生一个指纹），要么明确记为暂不实现。**别留着一个恒为 null 的列让人误以为功能已就绪。**

---

## 4. 落地顺序

分四批，每批独立可验证。

### 批次一 · 安全底线（必须最先做）

| 顺序 | 项 | 理由 |
|---|---|---|
| 1 | P0-2 token 类型校验 | 改动小、风险低，且是 P1-1 的前置 |
| 2 | P1-1 刷新时查库 | 依赖 P0-2 的类型校验 |
| 3 | P1-2 登出幂等 | 一行改动，顺手修掉 500 |
| 4 | P0-1 角色落地 | 本批次最大项，会改变现有行为 |

> **做完批次一后，现有 `syt` 账号必须提权成 ADMIN**，否则本地发不了文章。

### 批次二 · 数据与状态正确性

| 顺序 | 项 |
|---|---|
| 5 | P1-5 `updated_at` 触发器（DDL，追加到 `supplementary-schema.sql`） |
| 6 | P1-4 `last_login_at` 写入 |
| 7 | P2-3 账号禁用校验 |

### 批次三 · 凭证与传输加固

| 顺序 | 项 | 备注 |
|---|---|---|
| 8 | P1-3 refresh token 存哈希 | 需要 DDL + 数据迁移，建议单独一个提交 |
| 9 | P2-2 Cookie `secure` / `SameSite` | 依赖 `ResponseCookie` 改造 |
| 10 | P2-4 CORS 收紧 | |

### 批次四 · 体验与补全

| 顺序 | 项 |
|---|---|
| 11 | P2-1 注册后自动登录 |
| 12 | P3-1 登录日志 |
| 13 | P3-2 `device_id`（补上或明确标注不实现） |

---

## 5. 验收清单

### 功能验收

- [ ] 带**任意字符串**（如 `Authorization: x`）调 `POST /api/posts` → **401**
      （当前：成功。该路径在放行列表里，拦截器根本不跑）
- [ ] 带 USER 角色 token 调 `POST /api/posts` → **403**
- [ ] 带 ADMIN 角色 token 调 `POST /api/posts` → 成功
- [ ] 不带 token 调 `GET /api/posts` → **200**（游客可读，不能误伤）
- [ ] 注册接口无论请求体怎么构造，产物 `role` 恒为 `USER`
- [ ] 拿 **refresh token** 当 Bearer 调 `/api/auth/me` → **401**（当前：成功）
- [ ] 拿 **access token** 调 `/api/auth/refresh` → **401**
- [ ] 登出后拿旧 refresh token 调 `/api/auth/refresh` → **401**
- [ ] **重复登出**（同一 token 连点两次）→ 两次都返回成功（当前：第二次 500）
- [ ] 禁用账号（`status=0`）登录 → 403；已登录用户被禁用后调接口 → 403
- [ ] 登录成功后 `blog_users.last_login_at` 有值
- [ ] 更新资料后 `updated_at` 有变化
- [ ] 生产 profile 下 refresh cookie 带 `Secure` 与 `SameSite=Lax`

### 回归验收

- [ ] 登录 → 访问受保护接口 → 刷新 → 再访问，全链路通
- [ ] `BlogUserService.update()` 仍不会清空未传的资料字段（`docs/log.md` 中那条回归）
- [ ] CORS 预检 `OPTIONS` 仍正常放行
- [ ] AI 接口游客访问不受影响

---

## 6. 附：与既有文档的对应关系

| 本文件 | `database-review.md` | 说明 |
|---|---|---|
| P1-3 refresh token 存哈希 | P1-3 | 同一条，本文件补充了 DDL 需求 |
| P0-1 角色落地 | P1-1 | 那边只加了 `role` 列，**能力落地在本文件** |
| P2-3 账号禁用 | — | `status` 列早已存在，从未被读取 |
| P2-4 CORS | E-1 同类 | 都属于"配置层加固" |
| — | E-4 / E-5 | 注解处理器与 jOOQ codegen 问题，已修复，与本文件无关 |
