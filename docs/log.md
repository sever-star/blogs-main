# 开发问题解决日志 · blog 系统

> 用途：记录每个 bug 的**现象 → 定位过程 → 根因 → 修复 → 验证**，便于复盘与论文撰写。
> 约定：**新条目追加在最上方**（倒序），每条注明日期。
> 相关文档：`database-review.md`（库表评审）、`enum-dictionary.md`（枚举字典）、`supplementary-schema.sql`（补充 DDL）

---

## 2026-10-24 · AI 对话长时间空闲后提问报 500：`HttpMediaTypeNotAcceptableException` + `ResourceNotFoundException: 会话不存在或无权访问`

### 现象

登录状态下打开一个 AI 会话页面，长时间不操作（>15 分钟）后再提问，后端抛两个异常：

- 日志**第一条**栈：`org.springframework.web.HttpMediaTypeNotAcceptableException: No acceptable representation`（位于 `AbstractMessageConverterMethodProcessor.writeWithMessageConverters`）；
- **root cause**：`com.syt.blog.common.ResourceNotFoundException: 会话不存在或无权访问`（位于 `AiChatService.requireOwnedSession` → `resolveSession` → `streamChat` → `BlogAiController.chat`）。

前端表现为提问失败。

### 定位过程

**第 1 步：先分清哪条是根因、哪条是次生。**
日志先打 `HttpMediaTypeNotAcceptableException`，再以 `ResourceNotFoundException ... with root cause` 收尾——前者是 Spring 在「处理后者」时自己又炸的，真正的根因是 `ResourceNotFoundException`。

**第 2 步：为什么会抛「会话不存在或无权访问」？追 `requireOwnedSession` 的归属判定。**
`AiChatService.isOwnedBy`：

```java
if (owner.userId() != null && owner.userId().equals(session.getUserId())) return true;
return session.getUserId() == null && owner.ip().equals(session.getUserIp());
```

该会话是登录时建的，`blog_ai_sessions.user_id` 非空；而本次请求的 `owner.userId()` 是 null（见第 3 步）→ 两个分支都不命中 → 抛 404。

**第 3 步：`owner.userId()` 为什么是 null？追 token 生命周期。**

- `application-dev.yaml` `access-expiration: 900000` —— access token 只有 **15 分钟**，「长时间不操作」即超过此窗口；
- `JwtAuthenticationFilter`：token 过期时只 `fail(request, TOKEN_EXPIRED, ...)` 写 request attribute、**不写认证**、继续放行（设计上为不误伤游客路径）；
- `SecurityConfig` 把 `/api/ai/**` 设为 `permitAll`，过期 token **不会触发 EntryPoint 返回 401**，请求以匿名继续；
- `BlogAiController.resolveOwner` → `SecurityUtils.currentUser()` 返回 null → `owner.userId = null`。

链路坐实：**access token 过期 → 请求匿名降级 → 匿名 owner 访问不到 user_id 绑定的会话 → 404。**

**第 4 步：那 `HttpMediaTypeNotAcceptableException` 又是哪来的？**
`chat()` 原声明 `@PostMapping(value = "/chat", produces = TEXT_EVENT_STREAM_VALUE)`，把「可生产媒体类型」锁死成 `{text/event-stream}`。而 `ResourceNotFoundException` 在 `new SseEmitter(...)` **之前**抛出（`resolveSession` 在建 emitter 之前跑），响应还处在普通 HTTP 模式，于是走 `GlobalExceptionHandler.handleNotFound` 想把 404 写成 JSON `Result`——但没有转换器能把 `Result` 写成 `text/event-stream` → `HttpMediaTypeNotAcceptableException`。
对比：流式生成**中途**失败（emitter 已建，`runStream` 里 `send("error") + complete()`）从不报这个 500，因为根本不经过 `GlobalExceptionHandler`。

### 根因（两层叠加）

1. **主因**：AI 接口 `permitAll` + 过期 token 静默降级为匿名，匿名 owner 命中不了 user_id 绑定的会话 → `ResourceNotFoundException`；
2. **次生**：`chat()` 的 `produces=text/event-stream` 锁死可生产媒体类型，导致在 emitter 建立前抛出的任何业务异常（404/429/400）都无法被全局处理器序列化成 JSON → `HttpMediaTypeNotAcceptableException`，反而盖住了真正的 root cause。

### 修复（已提交）

1. **Bug 2**：去掉 `chat()` 的 `produces = TEXT_EVENT_STREAM_VALUE`，仅保留 `@PostMapping(value = "/chat")`。SseEmitter 成功路径不受影响（`ResponseBodyEmitterReturnValueHandler` 自行设 `Content-Type: text/event-stream`，不依赖 `produces`）；同时让 `@Valid` 校验失败、限流、空内容等早期异常也能正常序列化为 JSON。

2. **Bug 1**：在 `resolveOwner`（所有 AI 接口共用的归属解析）里读 `AuthFailure`，命中即抛业务异常，交全局处理器映射成 HTTP 401，由前端刷新 token 后重试：

```java
private AiChatService.Owner resolveOwner(HttpServletRequest request) {
    AuthFailure failure = AuthFailure.of(request);
    if (failure != null) {
        throw new BusinessException(failure.code(), failure.message());
    }
    AuthUser user = SecurityUtils.currentUser();
    Integer userId = user == null ? null : user.id();
    return new AiChatService.Owner(userId, clientIp(request));
}
```

- 检查放在 `resolveOwner` 而非单个接口里，**`chat` / `listSessions` / `createSession` / `deleteSession` / `listMessages` 全部覆盖**，token 失效时一致返回 401 触发刷新；
- `AuthFailure.of(request)` 仅在「带了 token 但失效」（过期/无效/禁用）时非空；**真游客无 Authorization 头 → 过滤器不写 AuthFailure → 返回 null → 照常以游客身份进入**，AI 对游客开放的设计不受影响；
- `failure.code()` 为 `TOKEN_EXPIRED(401001)` / `TOKEN_INVALID(401002)` / `ACCOUNT_DISABLED(403002)`，经 `toHttpStatus` 映射 401/403，前端 401 拦截器 → `/api/auth/refresh` → 重试，此时 token 有效 → `resolveOwner` 拿到 user → 归属匹配 → 正常对话；
- 同步更新 `BlogAiController` 类与 `resolveOwner` 的 Javadoc：原「全程不返回 401」仅对真游客成立，失效 token 主动 401。

### 验证

```bash
./mvnw -o compile -DskipTests
# BUILD SUCCESS（仅 Lombok/Unsafe 的 deprecation 警告，无错误）
```

预期端到端流程（待浏览器侧确认）：登录 → 会话页空闲 >15min → 提问 → 后端返回 HTTP 401 `{"code":401001,"message":"Token 已过期，请刷新"}` → 前端 401 拦截器自动调 `/api/auth/refresh` → 重试 chat → 200 正常流式回复。编译已过，运行时链路需在前端联调时在 Network 面板确认 401 响应体为 JSON（见下「遗留」）。

### 遗留 / 待确认

- **前端 Accept 头依赖**：401 以 JSON 返回，要求前端 `/api/ai/chat` 的 fetch `Accept` 含 `application/json` 或 `*/*`。fetch 默认 `*/*` 通常满足；若前端显式设了 `Accept: text/event-stream` 且不含 `*/*`，401 仍会触发 `HttpMediaTypeNotAcceptableException`。建议在浏览器 Network 面板确认该 401 响应体是 JSON 而非 406 类错误。

### 同类问题排查

排查全仓 `SecurityUtils.currentUser()`（可空版）的所有调用点，判定标准：**在 permitAll 路径上，过期 token 静默降级为匿名后，是否导致硬报错或访问不到用户绑定资源**。

| 位置 | 路径 | permitAll | `currentUser()` 用途 | 匿名降级后果 | 同类？ |
|---|---|---|---|---|---|
| `BlogAiController.resolveOwner` | `/api/ai/**` | 是 | 会话归属 user_id | 命中不了 user_id 绑定会话 → 404 | ✅ 是（已修） |
| `PostRepository.getPostDetail:254` | `GET /api/posts/**` | 是 | 设 liked/favorited | 两字段不设（NON_NULL 不序列化），详情照常 200 | ❌ 否（代码注释明说设计如此） |
| `BlogPostCommentService.listComments:39` | `GET /api/posts/*/comments` | 是 | 标记「我点过赞的评论」 | likedIds 为空，评论列表照常 200 | ❌ 否 |
| `ViewCountService.recordView:50` | `GET /api/posts/**` | 是 | 作者不计数 + 按 user 去重 | 降为按 IP 去重，且整方法 fail-open | ❌ 否 |

**结论：同类问题只存在于 AI 模块**。其余三处都在 permitAll GET 路径上读公开内容，`currentUser()` 仅做个性化点缀，null 化时良性退化、不报错。写操作（点赞/收藏/发评论/评论点赞）在 `SecurityConfig` 里都是 `authenticated()`，token 过期走 `JwtAuthenticationEntryPoint` 正常 401，不走静默降级，故不受影响。

---

## 2026-10-18 · 开发环境 · Claude Code MCP（context7）启动失败：cmd 无法解析 PATH 中的孤立引号 + npm 镜像源停服

### 现象

`claude mcp list` 中 context7 与 filesystem 均显示
`Failed to connect — -32000: MCP error -32000: Connection closed`。
手动执行 `npx -y context7` 报 `'"node"' is not recognized as an internal or external command`，
但 Git Bash 里 `node --version` 正常（v24.19.0）。

### 定位过程

**第 1 步：区分 bash 与 cmd 的环境差异。**
bash 能找到 node（`which node` → `/d/nodejs/node`），cmd 却不行 → 怀疑问题在 cmd 侧。

**第 2 步：一个矛盾的现象。**
`cmd //c "where node"` 能定位到 `D:\nodejs\node.exe`，但同一会话里 `node --version` 报"不是内部或外部命令"——
PATH 查询（where.exe）成功而执行查找失败，说明 PATH 字符串本身存在，但 cmd 解析它时出了问题。

**第 3 步：逐项 dump Machine PATH。**
发现 3 处孤立引号：

| 条目 | 问题 |
|---|---|
| `"D:\kafka_2.13-3.9.0\...\bin\windows` | 开头多了 `"` |
| `%PATH%"` | 字面量 `%PATH%` + 尾引号（setx 误用的典型产物） |
| `C:\Program Files\Git\usr\bin\core_perl"` | 尾部多了 `"` |

cmd 的 PATH 搜索按引号状态感知切分，孤立引号使后续条目（含 `D:\nodejs`）解析错乱；
where.exe 按分号简单切分故不受影响——完美解释了第 2 步的矛盾。

**第 4 步：控制变量验证。**
`cmd //c "set PATH=C:\Windows\system32;D:\nodejs&& node --version"` 正常输出 v24.19.0，
确认根因就是 PATH 中的孤立引号。

**第 5 步：顺带发现第二个坑。**
npm registry 指向 `registry.npm.taobao.org`（该域名已停服，HTTP 连通性 000），
即使 PATH 修复，`npx` 也拉不到 context7 包。

**第 6 步：PATH 修复重启后仍然失败——还有第三层根因。**
环境修好后 `claude mcp list` 依旧 Connection closed。手动执行启动命令 `npx -y context7`，
发现它打印 `Not enough non-option arguments: got 0, need at least 1` 后立即退出——
原来 npm 上的 `context7` 包是"查询用 CLI"，不是 MCP 服务器，不带参数就会退出；
而真正的 MCP 服务器包名是 **`@upstash/context7-mcp`**。MCP 客户端刚拉起进程它就死了，
于是报 Connection closed。

### 根因（三层叠加）

1. Machine PATH 被 setx 误用破坏（3 处孤立引号）→ cmd 解析 PATH 错乱 → 找不到 node/npx；
2. npm 镜像源为已下线的 taobao 域名；
3. MCP 配置的包名错误：`context7`（CLI，需参数，秒退）≠ `@upstash/context7-mcp`（真正的 MCP 服务器）。

### 修复

1. `npm config set registry https://registry.npmmirror.com`；
2. 提权脚本清理 Machine PATH（原值备份至 `C:\Users\19670\machine-path-backup-*.txt`）：
   删除 `%PATH%"` 与空条目、用 `.Trim('"')` 剥离首尾引号、保留其余有效条目。
   注：首版脚本正则误写为 `'+$'`（少写了 `"` 字符）导致运行时崩溃，改为 `.Trim('"')` 后成功；
3. 重注册 MCP（用户级配置）：
   `claude mcp remove context7 -s user` → `claude mcp add context7 -s user -- npx -y @upstash/context7-mcp`。

### 验证

- PATH 修复后：`node --version` → v24.19.0，cmd 可正常解析 PATH；
- 包名修正后：`claude mcp list` 显示 `context7: npx -y @upstash/context7-mcp - √ Connected`。

---

## 2026-09-24 · 创建文章 400：`null value in column "created_at"` 违反非空约束

### 现象

`POST /api/posts`（管理员身份）返回 400：

```
DataIntegrityViolationException: jOOQ; SQL [insert into "public"."blog_posts"
  ("user_id", "category_id", "title", "content_md", "summary", "cover_image",
   "reading_time", "status", "allow_comment", "created_at", "updated_at")
  values (...) returning "public"."blog_posts"."id"];
ERROR: null value in column "created_at" of relation "blog_posts" violates not-null constraint
详细：Failing row contains (12, 10, 3, 验收用文章标题, AAA..., null, null, null, null,
       0, 0, 0, 15, 2, 0, 1, null, null, null, 0).
```

### 定位过程

**第 1 步：把生成的 SQL 和 `blog_posts` 的列定义并排看。**
SQL 里 `created_at` / `updated_at` 被**显式列出并传了 NULL**，而 DDL 是
`created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP`——显式 NULL 会盖掉 DEFAULT，
所以触发 NOT NULL 违规。

**第 2 步：为什么是显式 NULL，而不是被优化掉？**
对照同一份 SQL 里的 `view_count / like_count / fav_count / is_top / embedding_status`——
它们同样 `NOT NULL DEFAULT 0`，却**根本没出现在 INSERT 里**。
jOOQ 的 `Tools.resetTouchedOnNotNull` 只会剔除「NOT NULL 且**从未被 set 过**」的字段；
被 `setXxx(null)` 触碰过的字段会被当成"用户就是要写这个值"，原样写进 SQL。

**第 3 步：谁 set 的？** 看 MapStruct 生成的 `PostMapperImpl`：
`PostMapper` 的 `@Mapping(ignore = true)` 列表里有 `viewCount / likeCount / favCount /
isTop / deletedAt / userId / contentHtml / tocHtml`——唯独**没有 `createdAt` / `updatedAt`**，
于是生成了 `blogPostsRecord.setCreatedAt(postRequest.getCreatedAt())`，传入值是 null。

### 根因

`PostMapper` 漏 ignore `createdAt` / `updatedAt`，MapStruct 把 null 写进 Record，
jOOQ 把"被 set 过的 null"当有效值送进 INSERT，显式 NULL 覆盖了 DB 默认值。
与 `blog_users.username` 那次是**同一类坑**（映射层不该碰交给数据库维护的列）。

### 修复

`DTO/Mapper/PostMapper.java` 补两条 ignore：

```java
// created_at/updated_at 都是 NOT NULL + DEFAULT CURRENT_TIMESTAMP，且 updated_at 由
// trg_posts_updated_at 维护。若映射进来会 setCreatedAt(null)——jOOQ 把「被 set 过的 null」
// 当作已触碰字段写进 INSERT，显式 NULL 会盖掉默认值 → 违反 NOT NULL。同 blog_users.username 的坑。
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
```

`blog_posts` 上有 `trg_posts_updated_at` 触发器，更新时间交给它即可，mapper 不必也不该管。

### 验证

`POST /api/posts` → 200，落库的 `created_at / updated_at` 由 DB 填入 `CURRENT_TIMESTAMP`。
`mvn clean test` 通过。

---

## 2026-09-24 · 创建文章 500：`No JDBC Connection configured`

### 现象

`POST /api/posts` 返回 500，响应体 `{"code":401,"message":"Cannot execute query. No JDBC Connection configured"}`。
（`code:401` 是 `GlobalExceptionHandler.handleRuntime` 硬编码的，与实际错误无关，见文末"未修"清单。）

### 定位过程

**第 1 步：看完整堆栈。** `jOOQ; SQL [insert into ...blog_posts...]` 上抛的是
`DataAccessException`，说明 SQL 都没到 JDBC 就在 jOOQ 内部挂了。

**第 2 步：区分"连接池没配"还是"这条记录没配"。**
同一次请求里 `PostRepository` 的查询是能跑通的（`getPostAndCategory` 等都走注入的 `DSLContext`），
说明 Spring 侧的 `DSLContext` 是好的——**问题出在这个 `BlogPostsRecord` 实例本身**。

**第 3 步：看记录是谁造的。** `BlogPostService.createPost` 调
`PostMapper.INSTANCE.postRequestToBlogPostsRecord(postRequest)`，
而 `PostMapper` 是 `@Mapper` + `Mappers.getMapper(...)`（componentModel 为默认的 `default`）。
翻 `target/generated-sources/.../PostMapperImpl.java`：

```java
BlogPostsRecord blogPostsRecord = new BlogPostsRecord();
```

**裸 `new` 出来的 Record 没有绑定 `Configuration`**，`record.insert()` 找不到连接。

### 根因

MapStruct 默认用无参构造器造 Record，而 jOOQ 的 `Record.insert()` 依赖实例上挂的
`Configuration`（其中才有 DataSource / 事务绑定）。同样手 `new` 的 `BlogPostTagsRecord`
也有这个问题，只是前一条先炸所以还没轮到它。

### 修复

`BlogPostService` 注入 `DSLContext`，对 MapStruct / 手 `new` 出来的两个 Record 各
`attach` 一次（`org.jooq.Record` 实现了 `Attachable`）：

```java
blogPostsRecord.attach(dsl.configuration());
blogPostsRecord.insert();
...
BlogPostTagsRecord blogPostTagsRecord = new BlogPostTagsRecord();
blogPostTagsRecord.attach(dsl.configuration());   // 同上，否则第一条 tag 就 500
```

> 备选方案是把 insert 整体搬进已有 `DSLContext` 的 `PostRepository`，改动更大；
> `attach` 是最小侵入的修法。

### 验证

`POST /api/posts` 越过 500（随后撞上 `created_at` 那个新问题，见上一条），
修完 `created_at` 后 → 200。

---

## 2026-09-24 · 禁用账号仍能登录并拿到 access token

### 现象

`UPDATE blog_users SET status = 0` 后调 `POST /api/auth/login`，**返回 200 并签发了
accessToken**。而 `docs/security-migration.md` §9 验收要求「禁用账号登录 → 403」。

（同一轮里，用这张**已签发**的 token 调接口 → 正确返回 403 `ACCOUNT_DISABLED`，
说明过滤器那半是好的，缺的是登录这半。）

### 定位过程

**第 1 步：确认过滤器已拦。** 带 token 调 `/api/auth/me` → 403 `{"code":403002}`，
`JwtAuthenticationFilter` 第 95 行 `fail(request, ACCOUNT_DISABLED, "账号已被禁用")` 生效。
→ 结论：P2-3 只做了一半。

**第 2 步：读 `BlogUserService.login`。** 全方法只有一次判断：

```java
if (users == null || !bCryptPasswordEncoder.matches(blogUsers.getPassword(), users.getPassword())) {
    throw new BusinessException(401, "用户名或密码错误");
}
```

`status` 字段**从头到尾没被读过**，校验通过就直接 `generateToken(...)`。

**第 3 步：确认不是"先发 token 再被拦"。** 返回体里确实带 accessToken 和 refreshToken
Cookie，说明凭证真的发出去了——只是拿去调任何接口都会 403。

### 根因

`security-migration.md` §8 阶段 3.2 只写了「过滤器内加 `status == 1` 校验」，
漏了「登录入口也要校验」；代码照文档执行，于是出现"token 发得出来但用不了"的半吊子状态。

### 修复

在密码校验**之后**、独立抛 403：

```java
// 禁用账号必须在密码校验**之后**单独抛 403：并进上面那行会被报成「用户名或密码错误」，
// 前端无法区分「密码输错」和「账号被封」。此时密码已验证通过，泄露禁用状态是可接受的。
if (users.getStatus() == null || users.getStatus() != STATUS_ACTIVE) {
    throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
}
```

- 不能合并进密码那行：否则报「用户名或密码错误」，语义丢失；
- 放在密码之后：避免未认证者靠 403 枚举出"该账号存在且已被封"；
- `403002` 经 `GlobalExceptionHandler.toHttpStatus` → `403002/1000 = 403`。

### 验证

- 禁用后登录 → **403** `{"code":403002,"message":"账号已被禁用"}`
- 已签发 token + 账号被禁用 → **403** `{"code":403002,...}`
- 恢复 `status=1` → 登录 200、`/api/auth/me` 200

---

## 2026-09-24 · `/api/auth/refresh` 失败恒返回 HTTP 200，并泄漏 JJWT 内部消息

### 现象

`POST /api/auth/refresh` 无论怎么失败，HTTP 状态码都是 **200**：

| 请求 | 期望 | 实际 |
|---|---|---|
| access token 当 refresh 用 | 401 | **200** `{"code":401001,...}` |
| 不带 refreshToken cookie | 401 | **200** `{"code":401001,...}` |

且 message 是 `"CharSequence cannot be null or empty."`——JJWT 内部的
`Assert.hasText` 断言文案，直接漏给了前端。

### 定位过程

**第 1 步：`body.code` 是对的，只有 HTTP 状态错。** `code:401001`（TOKEN_EXPIRED）
说明业务码传对了，问题在**响应是怎么发出去的**。

**第 2 步：读 controller 的 catch 块。**

```java
} catch (RuntimeException e) {
    clearRefreshTokenCookie(response);
    return Result.error(ErrorCode.TOKEN_EXPIRED, e.getMessage());
}
```

方法返回类型是 `Result<?>` 而非 `ResponseEntity`——`return Result.error(...)` 走的是
正常成功路径，Spring MVC 默认就发 **HTTP 200**，状态码全靠 body 里的 `code` 表达。

**第 3 步：为什么 message 是 JJWT 的内部文案。** `e.getMessage()` 直接透传，
而 null token 会让 `Jwts.parser().parseSignedClaims(null)` 抛
`IllegalArgumentException("CharSequence cannot be null or empty.")`。

**第 4 步：对照验收清单。** `security-migration.md` §9 明确要求
「HTTP 状态码分别是 401 / 403（**不是 200** 也不是 500）」和
「拿 access token 调 `/api/auth/refresh` → 401（P0-2）」——两条都挂。

### 根因

异常被 catch 后降级成"成功返回一个带错误码的 Result"，绕过了
`GlobalExceptionHandler.toHttpStatus` 的状态码映射；同时 `e.getMessage()`
无差别透传，把框架实现细节暴露给客户端。

### 修复

改回**抛异常**，交给 `GlobalExceptionHandler` 映射状态码；并按异常类型给准确的业务码：

```java
} catch (RuntimeException e) {
    clearRefreshTokenCookie(response);
    log.debug("刷新令牌失败: {}", e.toString());
    if (e instanceof ExpiredJwtException) {
        throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token 已过期，请刷新");
    }
    throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效，请重新登录");
}
```

- `clearRefreshTokenCookie` 仍在 throw 之前调用，清 Cookie 的副作用保住；
- 原方法里那段被注释掉的 null 判空（`refreshToken == null`）**保持注释状态未动**——
  null 会一路走进 JJWT 抛异常，落到同一个 catch，行为等价且不擅自恢复已删代码；
- 文案与 `JwtAuthenticationFilter` 对齐（`401002 → "Token 无效，请重新登录"`）。

### 验证

| 请求 | HTTP | 响应体 |
|---|---|---|
| access token 当 refresh cookie | **401** | `{"code":401002,"message":"Token 无效，请重新登录"}` |
| 不带 cookie | **401** | `{"code":401002,"message":"Token 无效，请重新登录"}` |
| 正常 refresh（带 cookie） | 200 | 正常签发新 access token |
| refresh token 当 Bearer 调 `/api/auth/me` | 401 | `{"code":401002,...}` |

JJWT 内部文案不再出现在任何响应里（只进 debug 日志）。

---

## 2026-09-23 · 注册接口 500：`null value in column "username"` 违反非空约束

### 现象

调用 `POST /api/auth/register` 注册新用户，后端抛异常：

```
org.postgresql.util.PSQLException: ERROR: null value in column "username"
of relation "blog_users" violates not-null constraint
详细：Failing row contains (7, null, syt, null, null,
       https://api.dicebear.com/7.x/fun-emoji/svg?seed=avatar-05,
       null, 1, null, null, null, null,
       2026-09-23 09:13:24.794668, 2026-09-23 09:13:24.794668, USER).
```

关键信息：失败行里 **`username` 是 null，而 `nickname` 反而是 "syt"**；
`password`、`email` 也都是 null；`status / created_at / updated_at / role` 有值。

### 定位过程

**第 1 步：从"哪些列有值"反推 INSERT 到底带了什么。**
把失败行按 `blog_users` 的物理列顺序对齐：

| 列 | 值 | 说明 |
|---|---|---|
| id | 7 | 自增，正常 |
| username | **null** | 违反 NOT NULL |
| nickname | `syt` | 有值 |
| password | **null** | 也是 NOT NULL，但被 username 抢先报错 |
| email | **null** | 也是 NOT NULL |
| avatar | dicebear URL | 有值 |
| status / created_at / updated_at / role | 1 / 时间 / 时间 / USER | **数据库默认值** |

`status`、`created_at`、`updated_at`、`role` 这四列都取到了 DB 默认值，
说明 jOOQ 的 DAO `insert` **会把值为 null 的字段整个从 INSERT 列表里省略**，
而不是显式写 null —— 所以这一行就是"哪些字段没被赋值"的直接证据。
结论：**有值的是 nickname / avatar，没值的是 username / password / email。**

**第 2 步：反查这三个字段在哪被丢掉的。**
全项目 `grep insert`，写 `blog_users` 的只有一处：`BlogUserService.register()`
→ `blogUsersDao.insert(blogUsers)`。而它的入参来自 `BlogUserController.register()`：

```java
BlogUsers blogUsers = UserMapper.INSTANCE.userDTOToBlogUsers(UserRequest);
```

看 `UserMapper.userDTOToBlogUsers` 的注解，一眼就明白了：

```java
@Mapping(target = "username", ignore = true)
@Mapping(target = "password", ignore = true)
@Mapping(target = "email",    ignore = true)
BlogUsers userDTOToBlogUsers(UserRequest userRequest);
```

这个方法本来是给 `PUT /api/auth/me`（**仅修改资料**）用的，
它的语义就是"用户名/密码/邮箱不允许通过请求修改"。
但 `register` 复用了它 → 注册时这三个字段全被置为 null，
和"有值的是 nickname / avatar"完全吻合。**根因锁定。**

**第 3 步：为什么没在更早的地方报错？（一个容易误判的点）**
按常识 `bCryptPasswordEncoder.encode(null)` 应该抛
`IllegalArgumentException: rawPassword cannot be null`，
那样异常就会停在 Service 层、根本到不了数据库。但实际异常来自 PostgreSQL。
查依赖发现项目用的是 **Spring Security 7.1.0**，
`BCryptPasswordEncoder` 的 `encode` 已被提到父类
`AbstractValidatingPasswordEncoder`，字节码里第一句就是：

```java
public final String encode(CharSequence rawPassword) {
    if (rawPassword == null) return null;   // ← 7.x 起不再抛异常
    return encodeNonNullPassword(rawPassword.toString());
}
```

即 **`encode(null)` 现在静默返回 null**。
于是 `setPassword(encode(null))` 把密码也写成了 null，
异常被一路推迟到数据库层才爆出来，报错点离真正的错误点很远。

**第 4 步：本地复现，确认不是猜测。**
`curl` 打正在运行的 8081 实例：

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"claude_debug_tmp","nickname":"claude_debug_tmp",
       "password":"123456","email":"tmp@debug.local","avatar":"..."}'
```

返回 `{"code":400,"message":"标签名已存在"}` —— 复现成功，但**提示信息是错的**，
暴露了第二个问题（见下）。

### 根因

1. **主因**：`register` 复用了为"仅修改资料"设计的 `userDTOToBlogUsers`，
   而该方法 `ignore` 了 `username` / `password` / `email` 三个字段，
   这三列在 `blog_users` 中都是 `NOT NULL` → INSERT 时插入 null → 违反非空约束。
2. **放大因素**：Spring Security 7.1 的 `encode(null)` 返回 null 而不抛异常，
   导致密码为 null 的问题没能被提前拦下，错误现场被推到数据库层。
3. **掩盖因素**：`GlobalExceptionHandler` 把**所有** `DataIntegrityViolationException`
   硬编码成 `"标签名已存在"`，注册失败时前端看到的是标签重名，误导排查方向。

### 修复

1. `UserMapper` 新增专用的注册映射，显式带上 `username / password / email`，
   只忽略由 DB 默认值或服务层维护的字段：

   ```java
   @Mapping(target = "id",          ignore = true)
   @Mapping(target = "status",      ignore = true)
   @Mapping(target = "lastLoginAt", ignore = true)
   @Mapping(target = "createdAt",   ignore = true)
   @Mapping(target = "updatedAt",   ignore = true)
   @Mapping(target = "role",        ignore = true)
   BlogUsers register(UserRequest userRequest);
   ```

2. `BlogUserController.register()` 改用 `UserMapper.INSTANCE.register(...)`。
   `PUT /me` 仍用 `userDTOToBlogUsers`，语义不变（用户名/密码/邮箱依旧不可改）。
3. `GlobalExceptionHandler.handleDataIntegrity` 改为通用提示
   `"数据冲突，请检查提交内容是否重复或缺失必填项"`，
   重名这类有业务语义的场景由 Service 层用 `DuplicateNameException` 精确区分。

### 验证

```bash
./mvnw -o compile                      # 通过
# 起一个 8082 的独立实例验证，不动开发中的 8081
curl -X POST http://localhost:8082/api/auth/register -d '{...}'
# → 200 {"code":200,"data":{"user":{"id":9,"username":"claude_verify_01",
#          "nickname":"验证账号","email":"verify01@debug.local", ...}}}
curl -X POST http://localhost:8082/api/auth/login -d '{...}'
# → 200，返回 accessToken，bcrypt 密码校验通过
```

> 验证用的临时账号 `claude_verify_01`（id=9）留在开发库里了，需要时手工删掉。

### 遗留问题（本次未改，待确认）

`UserRequest` 被 login / register / update 三个接口共用，但 `email` 上挂了 `@NotBlank`，
导致 **登录接口也必须传 email** 才能过校验：

```bash
curl -X POST .../login -d '{"username":"...","password":"..."}'
# → 400 "邮箱不能为空"
```

（带上 email 后登录正常，见上面的验证。）
修法有两种：① 把 login 的校验规则从共用 DTO 里拆出去（如恢复独立的 `LoginRequest`）；
② 共享 DTO 去掉 `@NotBlank`，把"注册必须有邮箱"挪到 Service 层校验。

---

## 2026-09-23 · 编译全量报错 307 处：Lombok / MapStruct 静默失效

### 现象

`./mvnw compile` 首次执行失败，报 **307 个编译错误**，集中在 10 个文件：

```
GlobalExceptionHandler.java   Result.java        JwtInterceptor.java
OssUtil.java                  PostRepository.java CategoryRepository.java
TagRepository.java            BlogPostService.java BlogTagService.java
BlogUserService.java
```

错误形态高度一致，全是「找不到符号」：

```
BlogTagService.java:[49,9]  找不到符号  变量 log
PostResponse.java           找不到符号  方法 setStatus(short)
PageResult                  无法将构造器应用到给定类型  需要: 没有参数
```

### 定位过程

**第 1 步：排除 jOOQ 嫌疑。**
本次改动涉及 jOOQ codegen，需先确认不是它引入的。检查全部错误 → **没有一条是"找不到 jOOQ 类"**，所有 jOOQ import 均正常解析。jOOQ 排除。

**第 2 步：发现"注解处理器没跑"的特征。**
抽查源码，`BlogTagService` 明明写了 `@Slf4j`，`PageResult` 明明写了 `@AllArgsConstructor`，但 `log` 和 5 参构造器都"找不到"。
→ 这不是代码写错，是**注解处理器根本没执行**。

**第 3 步：排除版本不兼容。**
```
Lombok 解析版本 = 1.18.46   （支持 JDK 25，无问题）
JDK 版本        = 25.0.4.1
```
版本没问题，那问题只可能出在"处理器有没有被调用"。

**第 4 步：一行命令坐实根因。**
```bash
./mvnw clean compile -DskipTests -Dmaven.compiler.proc=full
# 错误数：307 → 0
```
强制开启注解处理后构建直接通过。**根因确认。**

### 根因

> **JDK 23 起，`javac` 不再自动运行仅存在于 classpath 上的注解处理器。**
> 本项目使用 **JDK 25**，而 `pom.xml` 中没有任何 `annotationProcessorPaths` 配置，
> 因此 Lombok 与 MapStruct **静默失效**（不报错、不警告），
> 直接表现为所有 Lombok 生成的成员（`log` / getter / setter / 构造器）全部"找不到"。

### 修复

在 `pom.xml` 的 `<build><plugins>` 中新增 `maven-compiler-plugin` 配置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.5.3.Final</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

> 说明：显式声明 `annotationProcessorPaths` 后，classpath 自动发现机制即被取代，
> 因此 **Lombok、MapStruct 必须全部列出**，缺一会导致对应框架失效。
> `lombok-mapstruct-binding` 用于保证 MapStruct 在 Lombok **之后**运行，
> 否则 MapStruct 编译期看不到 Lombok 生成的 getter/setter，映射会静默丢字段。

### 验证

```bash
./mvnw clean compile -DskipTests
# BUILD SUCCESS   Total time: 21.405 s   错误数: 0
```

并确认三件事**真的发生了**（而非 javac 放弃处理）：

| 验证项 | 命令 | 结果 |
|---|---|---|
| MapStruct 生成实现类 | `find target/classes -name "*MapperImpl.class"` | ✅ 4 个：Post/Tag/User/CategoryMapperImpl |
| Lombok 生成 getter/setter | `javap -p -cp target/classes ...PostResponse` | ✅ `getStatus()` / `setStatus()` / `getReadingTime()` |
| jOOQ 新表新字段 | `javap -p -cp target/classes ...BlogPosts` | ✅ `EMBEDDING_STATUS` / `ROLE` / `BlogPostEmbeddings` |

### 附带教训

⚠️ **首次执行 `./mvnw compile` 时曾返回 `EXIT=0`，但那是假成功。**
Maven 增量编译误判"无需重编"而跳过，掩盖了真实错误。
**只有 `clean compile` 的结果才可信**，排查编译问题一律先 `clean`。

---

## 2026-09-23 · jOOQ codegen 长期失败，`target/generated-sources` 为过期代码

### 现象

执行 `./mvnw generate-sources` 直接报错：

```
[ERROR] Unable to parse configuration of mojo org.jooq:jooq-codegen-maven:3.21.8:generate
        for parameter interfaceOnly:
        Cannot find 'interfaceOnly' in class org.jooq.meta.jaxb.Generate
```

### 根因

`pom.xml` 中使用了 `<interfaceOnly>true</interfaceOnly>`，
但 **jOOQ 的 `org.jooq.meta.jaxb.Generate` 类中根本没有 `interfaceOnly` 这个字段**，
该参数不合法，导致 codegen 阶段直接中断。

**影响面比报错本身更大**：由于 codegen 从未成功执行，
`target/generated-sources/jooq` 中一直是**早期生成的过期代码**，
与真实数据库结构脱节。

> 这直接导致了一次误判：上一轮评审时依据过期生成代码，
> 认为「`blog_posts.status` 默认值为 2，疑似笔误」，
> 而 `pg_dump` 核对真实库结构后发现**实际就是 `DEFAULT 0`，完全正确**。
> 详见 `database-review.md` 的 P2-1（已标记为排除）。

### 修复

```xml
<!-- 修改前 -->
<interfaceOnly>true</interfaceOnly>

<!-- 修改后：jOOQ 无 interfaceOnly 参数，正确项为 interfaces -->
<interfaces>true</interfaces>
```

### 验证

```bash
./mvnw generate-sources -DskipTests
# EXIT=0
```
生成物核对：
- `BlogPostEmbeddings.java`（新表）
- `BlogPosts.EMBEDDING_STATUS`（新字段）
- `BlogUsers.ROLE`（新字段）
- `BlogAiMessages.sources`（新字段）

---

## 2026-09-23 · 数据库迁移：旧库 → pgvector 容器

> 非 bug，属于环境搭建，一并记录备查。

### 背景

RAG 闭环需要 `vector` 扩展，旧容器 `postgres:latest` 不含 pgvector。
新容器 `pgvector/pgvector:pg18-trixie`（容器名 `pgvector-db`，宿主机端口 **5433**）启动时是**空库**。

### 处理

1. 从旧容器（`competent_bartik`，5432）`pg_dump` 全量导出
2. 恢复到 `pgvector-db`（5433）→ **22 张表 + 数据完整**（1 用户 / 11 文章 / 18 标签 / 25 关联 / 16 令牌）
3. 执行 `supplementary-schema.sql`：启用 `vector` 0.8.6、建 `blog_post_embeddings` + HNSW 索引、
   加 `posts.embedding_status` / `users.role` / `ai_messages.sources`、统一日志表 `user_id` 类型
4. 配置指向新库：`application-dev.yaml` 与 `pom.xml` codegen 均改为 **5433**
5. 备份留存：`docs/_backup-old-db-5432.sql`

### 遗留

- 新发现 **5 组重复唯一索引**（详见 `database-review.md` P3-4），清理语句已在
  `supplementary-schema.sql` 中以**注释态**备好，待确认后执行。
