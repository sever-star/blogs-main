# 鉴权迁移 Spring Security · 方案文档

> 版本：v1.0　|　日期：2026-09-24
> 对象：把现有 `JwtInterceptor` 手写鉴权迁移到 Spring Security
> 结论性质：仅记录方案，**不修改任何代码**
> 前置阅读：`docs/auth-review.md`（本方案顺带落地其中的 P0-1 / P0-2 / P2-3 / P2-4）

---

## 0. 结论与范围

### 迁移的动机

现有 `JwtInterceptor` 只能做**路径级**的"有没有登录"，做不了角色判断，也无法按 HTTP 方法区分放行——这正是 `auth-review.md` 里 P0-1「角色能力完全未落地」的根因。Spring Security 提供 `requestMatchers(HttpMethod, ...)` + `hasAuthority` + `@PreAuthorize`，是解决这一层的合适工具。

### 范围

| 做 | 不做 |
|---|---|
| 用 `SecurityFilterChain` 替换 `JwtInterceptor` | **不改 JWT 的签发与解析逻辑**（`JwtUtils` 除类型校验外不动） |
| 授权规则按「方法 + 路径」细化 | **不改登录流程**（`BlogUserService.login` 的 BCrypt 比对保持原样） |
| 认证失败的响应体统一为 `Result` | 不引入 OAuth2 / 社交登录 |
| 顺带落地 P0-2 token 类型校验 | 不引入 Session / 表单登录 |
| 顺带落地 P2-3 账号禁用校验 | 不做方法级细粒度权限（仅预留） |

### 一句话架构

> **Security 负责「授权」，自定义 JWT 过滤器负责「认证」。**

不用 Spring Security 的 `UserDetailsService` / `AuthenticationManager` 做密码校验——登录仍由 `BlogUserService.login` 自己比对 BCrypt。理由见 §3.2。

---

## 1. 现状 vs 目标

### 现状

```
请求 → WebConfig 注册的 JwtInterceptor（HandlerInterceptor）
         │  验签名 + 验过期
         │  路径级放行（excludePathPatterns，无法按方法区分）
         ▼
       DispatcherServlet → Controller
         │  各自手动 authorization.replace("Bearer ", "") 再解析一遍
         ▼
       Service（BlogUserService / BlogPostService 内部再解析 token）
```

**问题**：
1. 放行按路径，`/api/posts` 把写接口一起放掉了
2. 无角色判断
3. token 被重复解析（拦截器一次、Controller/Service 又一次）
4. 放行清单和业务逻辑分离，改一处容易漏另一处

### 目标

```
请求 → SecurityFilterChain（Servlet Filter 链，在 DispatcherServlet 之前）
         │
         ├─ CorsFilter                ← 必须显式配置，否则预检被拦
         ├─ JwtAuthenticationFilter   ← 自定义：解析 token → 构造 Authentication
         │     失败时不写响应、不中断，交给后续阶段统一处理
         ├─ AuthorizationFilter       ← 按「方法 + 路径」判定 + hasAuthority
         │     未认证 → AuthenticationEntryPoint → 401 + Result JSON
         │     无权限 → AccessDeniedHandler      → 403 + Result JSON
         ▼
       DispatcherServlet → Controller
         │  从 SecurityContextHolder 取当前用户，不再解析 token
         ▼
       Service
```

---

## 2. 目标组件清单

| 组件 | 类型 | 职责 |
|---|---|---|
| `SecurityConfig` | `@Configuration @EnableWebSecurity` | 定义 `SecurityFilterChain`、CORS、异常处理、无状态策略 |
| `JwtAuthenticationFilter` | `OncePerRequestFilter` | 从 `Authorization: Bearer` 解析 token，校验后写入 `SecurityContextHolder` |
| `JwtAuthenticationEntryPoint` | `AuthenticationEntryPoint` | 未认证 → 401 + `Result{code:401003}` |
| `JwtAccessDeniedHandler` | `AccessDeniedHandler` | 已认证但无权限 → 403 + `Result{code:403001}` |
| `AuthUser` | record | 认证主体：`{Integer id, String username, String role}` |
| `SecurityUtils` | 工具类 | `currentUserId()` / `currentUser()` / `currentRole()`，封装 `SecurityContextHolder` |
| `CorsConfigurationSource` | `@Bean` | Security 与 MVC 共用同一份 CORS 配置 |
| `BCryptPasswordEncoder` | `@Bean`（已有） | 保留在 `SecurityBeanConfig` |

**删除**：`JwtInterceptor`、`WebConfig.addInterceptors(...)` 中对其的注册。

**保留**：`JwtUtils`（新增类型校验方法）、`BlogUserService.login` 的密码比对逻辑、refresh token 那套。

---

## 3. 关键设计决策

### 3.1 不用 `UserDetailsService` / `AuthenticationManager`

| 方案 | 说明 | 评价 |
|---|---|---|
| A. Security 只做授权 | 登录仍走 `BlogUserService.login`，自己 BCrypt 比对；过滤器直接构造 `Authentication` | **推荐** |
| B. 实现 `UserDetailsService` + `DaoAuthenticationProvider` | 登录改走 `authenticationManager.authenticate(...)` | 改动大，收益低 |

**选 A 的理由**：`UserDetailsService` 在 JWT 场景下**只在登录那一刻被调用一次**，之后每个请求的认证都靠 token 完成。为一个只用一次的场景引入 `UserDetails` / `AuthenticationProvider` / `AuthenticationManager` 三件套，还要处理 `AccountNonLocked`、`CredentialsExpired` 等我们并不使用的语义，纯属增加复杂度。登录逻辑保持现状还能避免回归。

**代价**：`BCryptPasswordEncoder` 这个 bean 不会被 Security 自动使用——但我们也确实不需要它被自动使用，过滤器不碰密码。

### 3.2 无状态：`SessionCreationPolicy.STATELESS`

必须显式设置。否则 Spring Security 在认证成功后默认创建 `HttpSession`，与我们"JWT 无状态"的前提冲突，还会让 `SecurityContextHolder` 的来源变得不确定。

```java
http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

### 3.3 CSRF：关闭，但要知道代价

```java
http.csrf(AbstractHttpConfigurer::disable)
```

**为什么可以关**：认证凭证是 `Authorization` 头里的 JWT，浏览器**不会**自动携带，CSRF 的前提不成立。

**但有一个例外**：refresh token 走的是 HttpOnly Cookie，path=`/api/auth`，浏览器**会**自动带上。所以 `POST /api/auth/refresh` 和 `POST /api/auth/logout` 理论上存在 CSRF 面。

**实际危害很低**：攻击者拿不到新 access token（跨域读不到响应体），最多把用户的 refresh cookie 悄悄换成新的，造成"被静默续期"。

**两种处理**：
- (a) 整体 disable，靠 Cookie 的 `SameSite=Lax` 兜住（见 `auth-review.md` P2-2）——**推荐**，与现有技术栈一致
- (b) 只对这两个端点启用 `CookieCsrfTokenRepository`——更严格，但要前端配合传 CSRF token，成本高

个人博客选 (a)。

### 3.4 CORS：必须在 Security 里配，这是当初不用 Security 的原因

`pom.xml` 里那段被注释掉的说明写得很准确：

> 后者会默认对所有 URL 启用 HTTP Basic + CSRF，在 DispatcherServlet 之前拦截请求，导致跨域响应不带 `Access-Control-Allow-Origin` 而被浏览器误报为 CORS 错误。

这个担心是对的，**但可以解决**：Spring Security 的过滤器链在 `DispatcherServlet` 之前，所以 `WebConfig.addCorsMappings` 那套 MVC 层 CORS 对**被 Security 拦下的请求**不生效。必须在 Security 里也配一份：

```java
http.cors(c -> c.configurationSource(corsConfigurationSource()))
```

**做法**：把 CORS 配置从 `WebConfig.addCorsMappings` 抽成一个 `CorsConfigurationSource` bean，Security 和 MVC 共用同一个 bean，避免两处配置漂移。

**注意**：`OPTIONS` 预检请求本身不带 `Authorization` 头。如果 CORS 没配好，预检会被 Security 判为未认证并返回 401，浏览器把 401 当作预检失败，报成 CORS 错误——这正是原注释描述的现象。配好 `http.cors()` 后，`CorsFilter` 会处理预检并直接返回，不会走到授权阶段。

**顺带收紧**（`auth-review.md` P2-4）：`allowedOriginPatterns("*")` + `allowCredentials(true)` 建议改成配置项，dev 用 `http://localhost:5173`，prod 用真实域名。

### 3.5 必须关闭的默认行为

| 配置 | 不关的后果 |
|---|---|
| `httpBasic(disable)` | 浏览器弹原生登录框；未认证响应带 `WWW-Authenticate` |
| `formLogin(disable)` | 生成 `/login` 页面；未认证请求 302 重定向而非 401 |
| `logout(disable)` | Security 默认监听 `POST /logout`，与我们的 `/api/auth/logout` 语义混淆 |
| `sessionManagement(STATELESS)` | 创建无用的 HttpSession |

**这四项在草稿 `SecurityConfig` 里全部没关**，见 §7。

### 3.6 过滤器失败时不要自己写响应

`JwtAuthenticationFilter` 在 token 无效/缺失时，**不能**直接写 401 并 return。必须：

```java
// 校验失败时：不设置 SecurityContext，直接放行到下一环
filterChain.doFilter(request, response);
```

由 `AuthorizationFilter` 在授权阶段统一判定：需要认证的路径 → `AuthenticationEntryPoint`；`permitAll` 的路径 → 正常执行。

**为什么**：游客访问 `GET /api/posts` 时本来就没有 token。如果过滤器直接 401，**游客可读的接口会被误伤**。这是迁移时最常见的功能回归。

### 3.7 角色判断用 `hasAuthority` 而非 `hasRole`

**这是最容易踩的坑**：`hasRole("ADMIN")` 会**自动加 `ROLE_` 前缀**，实际要求 authority 是 `ROLE_ADMIN`。而我们的 `blog_users.role` 存的是 `ADMIN`（见 `supplementary-schema.sql` 的 P1-1）。

两种选择：
- **用 `hasAuthority("ADMIN")`**，authority 直接取 `user.getRole()` —— **推荐**，与库中数据一致，没有隐藏约定
- 用 `hasRole("ADMIN")`，则在构造 `GrantedAuthority` 时手工拼 `"ROLE_" + role`

草稿里写的是 `hasRole("ADMIN")`，按当前数据会**永远拒绝所有 ADMIN**。

### 3.8 要不要在过滤器里查库？

| 方案 | 优点 | 缺点 |
|---|---|---|
| 只信 token 里的 claim | 零查询 | **降权有 ≤15 分钟窗口**；无法感知 `status` 变更 |
| **每个请求查库取 user** | 降权立即生效；可校验 `status == 1` | 每请求多一次主键查询 |

**推荐查库**。个人博客量级下一次主键查询无感，换来两个能力：
1. 把用户从 ADMIN 降为 USER 后**立即生效**，不用等 token 过期
2. 顺带落地 `auth-review.md` 的 **P2-3**（`status=0` 禁用账号立即失去访问权）

若要优化，可加一层带短 TTL（如 60 秒）的本地缓存——**但先不要做**，等真的有性能问题再说。

### 3.9 异步与 SSE 的上下文传递

`AiChatService` 用 `Executors.newVirtualThreadPerTaskExecutor()` 起线程跑流式生成（`streamExecutor.execute(() -> runStream(...))`）。

`SecurityContextHolder` 默认是 `MODE_THREADLOCAL`，**不会**传播到新线程。当前 `resolveOwner(request)` 在**请求线程**上调用（在 `streamChat` 之前），所以暂时没问题。但要注意：

- 若将来在异步线程里需要 `SecurityUtils.currentUser()`，会拿到 null
- 届时需要 `DelegatingSecurityContextExecutor` 包装 `streamExecutor`，或设 `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)`

**本方案不处理**，但要在代码里留注释说明这个边界。

### 3.10 可选：`@EnableMethodSecurity`

加上 `@EnableMethodSecurity` 后即可用 `@PreAuthorize("hasAuthority('ADMIN')")`，为将来"文章作者本人或 ADMIN 可改"这类**细粒度**判断留口子——这类判断在过滤器/路径规则层面做不到，必须下沉到方法级。

**建议现在加上注解但先不用**，将来需要时不必再改配置。

---

## 4. 授权规则矩阵

这是整个迁移的**核心产出**，所有放行/拦截都以此为准。

### 4.1 公开（`permitAll`）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | |
| `POST` | `/api/auth/register` | |
| `POST` | `/api/auth/refresh` | 靠 refresh cookie 自证 |
| `POST` | `/api/auth/logout` | 幂等，见 `auth-review.md` P1-2 |
| `GET` | `/api/posts`、`/api/posts/*` | 游客可读 |
| `GET` | `/api/categories`、`/api/categories/**` | 游客可读 |
| `GET` | `/api/tags`、`/api/tags/**` | 游客可读 |
| `GET` | `/api/ai/config` | 前端据此提示"AI 未配置" |
| `ALL` | `/api/ai/**` | 游客可用，**成本控制靠 `AiRateLimiter` 按 IP 限流** |

> **`/api/ai/**` 的说明**：设计上游客可用（见 `智能个人博客系统-功能设计文档.md` §8.3 与 `WebConfig` 的注释）。会话归属由 `resolveOwner` 决定——登录则归用户，未登录则按 IP。放开的同时**必须确认限流生效**，否则是开放的成本敞口。

### 4.2 需登录（`authenticated`）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/auth/me` | |
| `PUT` | `/api/auth/me` | |
| `POST` | `/api/upload/avatar` | 上传自己的头像 |

### 4.3 需 ADMIN（`hasAuthority("ADMIN")`）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/posts` | |
| `PUT` | `/api/posts/*` | |
| `DELETE` | `/api/posts/*` | |
| `POST` | `/api/categories` | |
| `PUT` | `/api/categories/*` | |
| `DELETE` | `/api/categories/*` | |
| `POST` | `/api/tags` | |
| `PUT` | `/api/tags/*` | |
| `DELETE` | `/api/tags/*` | |

### 4.4 兜底

```java
.anyRequest().authenticated()
```

**注意**：`/api/posts` 和 `/api/posts/*` 的 `GET` 规则必须写在 `POST/PUT/DELETE` 规则**之前**或明确用方法区分。Spring Security 的 `requestMatchers` **按声明顺序匹配，先匹配者胜**——顺序写反会导致游客读不了文章。

### 4.5 行为变化提示

以下路径的**行为会收紧**，是本次迁移的预期变化：

| 路径 | 迁移前 | 迁移后 |
|---|---|---|
| `POST/PUT/DELETE /api/posts/**` | **完全放行**（任何人可写） | 需 ADMIN |
| `POST/PUT/DELETE /api/categories/**`、`/api/tags/**` | 需登录（任何 USER 可写） | 需 ADMIN |
| 任意受保护接口 | 未登录 → 拦截器写 401 | 未登录 → EntryPoint 写 401（**响应体格式需保持一致**） |

---

## 5. 现有代码的清理清单

改造后不再需要手动解析 token。以下位置都要清理，否则会出现"两套认证并存"的混乱。

| 文件 | 现状 | 改为 |
|---|---|---|
| `BlogUserController.me/update` | `@RequestHeader("Authorization") String authorization` | 去掉参数，用 `SecurityUtils.currentUserId()` |
| `BlogUserService.getUser/update` | `authorization.replace("Bearer ", "")` + `getUserIdFromToken` | 从 `SecurityContextHolder` 取 |
| `BlogPostController.createPost/updatePost` | `@RequestHeader("Authorization")` | 去掉参数 |
| `BlogPostService.resolveUserId(authorization)` | 私有方法手动解析 | **删除**，改 `SecurityUtils.currentUserId()` |
| `BlogFileController.uploadFile` | 收了 `authorization` 但**从未使用**（死参数） | 去掉参数 |
| `BlogAiController.resolveOwner(request)` | 宽松解析：有 token 则解析，无则回退 IP | **保留宽松语义**，但改为「SecurityContext 有用户则用，否则回退 IP」 |
| `WebConfig.addInterceptors` | 注册 `JwtInterceptor` | 删除 |
| `JwtInterceptor` | 整个类 | 删除 |

> `BlogAiController` 是唯一的例外：它需要"游客也能访问"，所以**不能**改成强依赖 `SecurityUtils.currentUserId()`（那样游客会抛异常）。必须先判断是否已认证，再回退。

---

## 6. 需要新增的错误码

现有 `ErrorCode` 里没有 403 相关码，`auth-review.md` 的 P2-3（账号禁用）也需要一个码。建议补充：

| 常量 | 值 | 用途 |
|---|---|---|
| `ACCESS_DENIED` | `403001` | 已认证但权限不足 |
| `ACCOUNT_DISABLED` | `403002` | 账号被禁用（`status=0`） |

`GlobalExceptionHandler.toHttpStatus` 会把 `403001` 映射为 HTTP 403，与现有约定一致。

---

## 7. 现有 `SecurityConfig` 草稿的问题清单

当前 `config/SecurityConfig.java` 的内容有 **8 处问题**，其中 3 处会导致功能直接不可用：

| # | 问题 | 后果 | 严重度 |
|---|---|---|---|
| 1 | 缺 `@Configuration`（和 `@EnableWebSecurity`） | **整个类不会被 Spring 加载**，配置完全不生效 | 🔴 致命 |
| 2 | `.requestMatchers("")` | 空字符串匹配不到任何路径，`hasRole("ADMIN")` 形同虚设 | 🔴 致命 |
| 3 | `hasRole("ADMIN")` | 自动加 `ROLE_` 前缀，与库中的 `ADMIN` 不匹配 → **永远拒绝** | 🔴 致命 |
| 4 | `.requestMatchers("/api/posts").permitAll()` | 按路径放行，`POST/PUT/DELETE` 一并放掉（P0-1 原样复现） | 🟠 高 |
| 5 | 未配 `http.cors(...)` | 预检 `OPTIONS` 被 Security 拦下 → 浏览器报 CORS 错误 | 🟠 高 |
| 6 | `formLogin(form -> form.defaultSuccessUrl(""))` | 启用表单登录并重定向到空 URL | 🟠 高 |
| 7 | `logout(logout -> logout.permitAll())` | 启用 Security 自带的 logout，与 `/api/auth/logout` 混淆 | 🟡 中 |
| 8 | 未定义 `EntryPoint` / `AccessDeniedHandler` | 未认证/无权限时返回 Spring 默认响应，**前端拿不到 `Result{code,message}`** | 🟠 高 |

另外，第 4 条还漏了 `/api/posts/*`（详情/更新/删除路径），`/api/categories/**` 和 `/api/tags/**` 的写接口也没纳入规则。

**建议**：这个文件重写而不是修补。

---

## 8. 分阶段迁移步骤

每阶段结束都应能编译并跑通，**不要一次性全切**。

### 阶段 0 · 准备（不改行为）

| 步骤 | 内容 |
|---|---|
| 0.1 | `ErrorCode` 补 `ACCESS_DENIED` / `ACCOUNT_DISABLED` |
| 0.2 | `JwtUtils` 增加带类型校验的解析方法（`auth-review.md` P0-2）：`getUserIdFromAccessToken` / `getUserIdFromRefreshToken`，旧方法降为私有 |
| 0.3 | 新增 `AuthUser` record、`SecurityUtils` 工具类 |
| 0.4 | 把 CORS 配置从 `WebConfig.addCorsMappings` 抽成 `CorsConfigurationSource` bean，`WebConfig` 改为引用它 |

> 阶段 0 结束时，`JwtInterceptor` 仍在工作，功能不变。

### 阶段 1 · 引入 Security，但只做"透传"

| 步骤 | 内容 |
|---|---|
| 1.1 | 重写 `SecurityConfig`：`@Configuration @EnableWebSecurity`，关闭 formLogin/httpBasic/logout，`STATELESS`，`csrf.disable`，配 `http.cors(...)`，`anyRequest().permitAll()` |
| 1.2 | 新增 `JwtAuthenticationFilter`，注册进链；**同时保留 `JwtInterceptor`** |
| 1.3 | 新增 `JwtAuthenticationEntryPoint` / `JwtAccessDeniedHandler`（此时还用不到，先建好） |

> **验证点**：此时所有接口行为**与迁移前完全一致**（因为 `permitAll`）。重点是确认**跨域和预检没坏**——这是 pom 注释里担心的那个坑。如果跨域坏了，说明 `http.cors()` 没配好。

### 阶段 2 · 切换认证来源

| 步骤 | 内容 |
|---|---|
| 2.1 | 移除 `WebConfig.addInterceptors`，删除 `JwtInterceptor` |
| 2.2 | 清理 §5 表格里的手动解析代码，改用 `SecurityUtils` |
| 2.3 | `BlogAiController.resolveOwner` 改为「SecurityContext 有则用，无则回退 IP」 |

> **验证点**：登录 → 访问 `/api/auth/me` → 刷新 → 再访问，全链路通；游客访问 `/api/posts`、`/api/ai/**` 仍正常（**重点验证 §3.6 那个误伤**）。

### 阶段 3 · 收紧授权

| 步骤 | 内容 |
|---|---|
| 3.1 | 按 §4 的矩阵替换 `anyRequest().permitAll()` |
| 3.2 | 过滤器内加 `status == 1` 校验（P2-3） |
| 3.3 | **把现有 `syt` 账号提权为 ADMIN**：`UPDATE blog_users SET role = 'ADMIN' WHERE username = 'syt';` |

> **验证点**：§9 的功能验收清单。这一步是行为变化最大的一步，**提权必须在切换前完成**，否则你自己会被锁在外面。

### 阶段 4 · 加固（可与 `auth-review.md` 批次三合并）

| 步骤 | 内容 |
|---|---|
| 4.1 | Cookie 改 `ResponseCookie`，支持 `Secure` / `SameSite=Lax`（`auth-review.md` P2-2） |
| 4.2 | CORS 白名单配置化（P2-4） |
| 4.3 | refresh token 存哈希（P1-3） |

---

## 9. 验收清单

### 授权

- [ ] 游客 `GET /api/posts` → 200
- [ ] 游客 `GET /api/posts/{id}` → 200
- [ ] 无 token `POST /api/posts` → **401**
- [ ] USER token `POST /api/posts` → **403**
- [ ] ADMIN token `POST /api/posts` → 成功
- [ ] USER token `DELETE /api/tags/{id}` → **403**
- [ ] 无 token `GET /api/auth/me` → **401**
- [ ] `PUT /api/auth/me` 只改自己的资料（无法越权改他人）

### 响应体一致性

- [ ] 401 响应体是 `Result{code:401003, message:"..."}`，**不是** Spring 默认的空 body
- [ ] 403 响应体是 `Result{code:403001, ...}`
- [ ] HTTP 状态码分别是 401 / 403（不是 200 也不是 500）

### 跨域（重点）

- [ ] 前端（`localhost:5173`）发起的预检 `OPTIONS` 返回 **200**，且带 `Access-Control-Allow-Origin`
- [ ] 带 `Authorization` 头的跨域请求正常返回，不报 CORS 错误
- [ ] 带 Cookie 的跨域请求（`/api/auth/refresh`）`Access-Control-Allow-Credentials: true` 正常返回

### 认证链路回归

- [ ] 登录 → `/api/auth/me` → 刷新 → 再访问，全链路通
- [ ] 拿 refresh token 当 Bearer 用 → **401**（P0-2）
- [ ] 拿 access token 调 `/api/auth/refresh` → **401**（P0-2）
- [ ] 禁用账号（`status=0`）登录 → 403；已登录用户被禁用后调接口 → 403（P2-3）
- [ ] 游客 `/api/ai/**` 全部接口仍可用（`resolveOwner` 回退到 IP 未坏）
- [ ] 游客 AI 会话列表、创建、删除均正常

### 其他

- [ ] 启动日志**不再出现** `Using generated security password`
- [ ] `mvn clean test` 通过
- [ ] `JwtInterceptor` 已删除且无残留引用

---

## 10. 已知的坑（速查）

| 坑 | 现象 | 解法 |
|---|---|---|
| `hasRole` 前缀 | ADMIN 用户被判 403 | 用 `hasAuthority("ADMIN")` |
| `requestMatchers` 顺序 | 游客读不了文章 | `GET` 规则写在写方法规则之前 |
| 过滤器直接写 401 | 游客可读接口被误伤 | 失败时 `doFilter` 放行，交给授权阶段 |
| CORS 只在 MVC 配 | 预检 401，浏览器报 CORS 错误 | `http.cors(c -> c.configurationSource(...))` |
| `@RestControllerAdvice` 失效 | Security 的异常不走 `GlobalExceptionHandler` | 自定义 `EntryPoint` + `AccessDeniedHandler` |
| 引入 starter 后的随机密码 | 启动日志出现 `Using generated security password` | 提供 `UserDetailsService` bean 或设 `spring.security.user.*`，或忽略（无害但易误导） |
| 异步/虚拟线程丢上下文 | `SecurityUtils` 在 SSE 线程返回 null | `DelegatingSecurityContextExecutor` 包装（§3.9） |
| 未设 `STATELESS` | 无端创建 HttpSession | `sessionCreationPolicy(STATELESS)` |
| 忘了关 `formLogin` | 未认证被 302 重定向而非 401 | `formLogin(AbstractHttpConfigurer::disable)` |
| 切换后才提权 | 自己发不了文章 | 阶段 3.3 在切换**前**执行 |

---

## 11. 与既有文档的关系

| 本文件 | `auth-review.md` | 关系 |
|---|---|---|
| §3.7 角色判断 | P0-1 | 用 Security 的方式落地 P0-1 |
| §8 阶段 0.2 | P0-2 | token 类型校验，迁移前先做 |
| §3.8、§8 阶段 3.2 | P2-3 | 账号禁用校验，随查库顺带落地 |
| §3.4 | P2-4 | CORS 收紧 |
| §8 阶段 4 | P1-3、P2-2 | 留到加固阶段 |
| — | P1-1、P1-2、P1-4、P1-5、P2-1 | **与本迁移正交**，可在迁移前后独立完成 |
