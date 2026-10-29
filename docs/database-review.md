# 数据库设计评审 · blog 系统

> 版本：v1.0　|　日期：2026-09-22
> 对象：PostgreSQL `public` schema（jOOQ 已生成 20 张表）
> 结论性质：仅记录评审建议，**不修改任何库表**；落地 SQL 见 `supplementary-schema.sql`

---

## 0. 总体评价

这是一份**超出毕设平均水准**的库表设计，主要亮点：

- 文章表拆分 `content_md` / `content_html` / `toc_html`，预渲染 + 目录，阅读体验好
- 软删除 `deleted_at`、文章版本 `blog_post_versions`、定时发布 `scheduled_at`
- AI 采用「会话 `blog_ai_sessions` + 消息 `blog_ai_messages`」多轮结构，优于单条日志
- 三类日志（登录 / 操作 / 访问）、热词 `blog_search_keywords`、通知、友链、用户偏好齐全
- 刷新令牌带 `device_id` / `revoked`，安全设计到位

核心问题集中在：**RAG 向量化无落点、role 缺失、若干类型/枚举不一致**。

---

## 1. 现有表清单（20 张）

| 分类 | 表 |
|---|---|
| 用户 | blog_users、blog_refresh_tokens |
| 内容 | blog_posts、blog_post_versions |
| 组织 | blog_categories、blog_tags、blog_post_tags |
| 互动 | blog_comments、blog_comment_likes、blog_post_likes、blog_post_favorites |
| AI | blog_ai_sessions、blog_ai_messages |
| 日志/统计 | blog_login_logs、blog_operation_logs、blog_visit_logs、blog_statistics、blog_search_keywords |
| 其他 | blog_links、blog_notifications |

---

## 2. P0 —— 阻塞 RAG 闭环（必须先补）

| # | 问题 | 详情 | 建议 |
|---|---|---|---|
| P0-1 | **缺向量表** | `docs/design.md` 的 RAG 基于 pgvector，但无任何向量/分块存储表 | 新增 `blog_post_embeddings(post_id, chunk_index, content, embedding)` |
| P0-2 | **AI 消息缺「引用来源」** | RAG 回答需带原文出处，`blog_ai_messages` 只有 `role/content/tokens/status` | 给 `blog_ai_messages` 加 `sources JSON`（或独立来源表） |
| P0-3 | **文章缺「向量化状态」** | 异步向量化需要 READY/FAILED 状态标记，`blog_posts` 没有 | 给 `blog_posts` 加 `embedding_status SMALLINT`（0=待处理/1=完成/2=失败） |

> 这三条不补，第三条闭环（RAG 问答）在数据层无法落地。

---

## 3. P1 —— 一致性与正确性

| # | 问题 | 详情 | 建议 |
|---|---|---|---|
| P1-1 | **`blog_users` 无 `role`** | 鉴权需区分「博主/管理员」与「读者」，用户表只有 `status` | 加 `role VARCHAR(20) DEFAULT 'USER'`（或约定 id=1 即博主） |
| P1-2 | **ID 类型不统一** | `blog_login_logs.user_id`、`blog_operation_logs.user_id` 为 **BIGINT**，而 `blog_users.id` 为 **INTEGER**；高频表（comments/ai/likes/logs）用 BIGINT、业务表用 INTEGER 可接受，但**引用 user 的字段应统一 INTEGER** | 统一日志表 `user_id` 为 INTEGER（无 FK，改动安全） |
| P1-3 | **refresh_token 存明文** | `blog_refresh_tokens.token VARCHAR(500)` 直接存 token，泄露即被盗用 | 改为存**哈希值**（代码层改，非 DDL） |
| P1-4 | **SMALLINT 枚举无文档** | `posts.status`(default 0)、`comments.status`(default 0) 等裸数字含义难维护 | 见 `enum-dictionary.md`，统一约定 |

---

## 4. P2 —— 待明确的业务决策

| # | 问题 | 详情 | 建议 |
|---|---|---|---|
| P2-1 | ~~**`posts.status` 默认 2 反常**~~ **已排除** | 经 `pg_dump` 核对真实库结构，实际为 `DEFAULT 0`，符合「0=草稿」直觉 | 无需改动。此前"默认 2"系 `target/generated-sources` 中过期 jOOQ 代码所致 |
| P2-2 | **评论同时支持匿名+登录** | 既有 `user_id`（可空）+`is_admin`，又有 `nickname`(非空)/`email`/`avatar` | 与 design.md「必须登录」冲突，二选一；保留匿名则当前设计合理 |
| P2-3 | **`parent_id`/`reply_to_id` 用 0 作空哨兵** | 默认 0 而非 NULL，join 需额外 `> 0` 过滤 | 可保留，注释「0=顶级评论」 |
| P2-4 | **计数冗余一致性** | `posts.like_count/fav_count/view_count` 与明细表并存 | 明确「计数器为准、明细为流水」，事务内同步 |

---

## 5. P3 —— 索引 / 性能

| # | 问题 | 详情 | 建议 |
|---|---|---|---|
| P3-1 | **缺列表查询索引** | `blog_posts` 仅 `category_id`、`user_id` 索引；最常跑的「已发布按时间倒序」无索引 | 加 `(status, is_top, created_at DESC)` 复合索引 |
| P3-2 | **评论列表索引可复合** | 已有 `post_id` 单独索引；查询是 `WHERE post_id=? AND status=?` | 加 `(post_id, status)` 复合索引 |
| P3-3 | **全文搜索无索引** | 有热词表但正文检索走 `ILIKE '%kw%'` 会全表扫 | 中文场景可接受小数据量；若要正规化用 pg_trgm 或 tsvector（中文分词需额外处理） |
| P3-4 | **5 组重复唯一索引** | 同一列组合上同时存在「表约束」与「独立唯一索引」：`blog_comment_likes(comment_id,user_ip)`、`blog_comment_likes(comment_id,user_id)`、`blog_post_likes(post_id,user_ip)`、`blog_post_favorites(post_id,user_id)`、`blog_users(username)` | 每组**删掉一个**（保留约束版 `uk_*`，删除索引版 `uk_*_*`）。重复索引使每次写入多维护一棵 B+ 树，纯浪费 |

---

## 6. 环境观察（评审时发现，非库表问题）

| # | 观察 | 建议 |
|---|---|---|
| E-1 | `application-dev.yaml` 内 **OSS AccessKey 明文提交** | 改用环境变量注入，并从 git 历史清除该密钥 |
| E-2 | `pom.xml` 仍含 `spring-boot-starter-data-jpa`、`mysql-connector-j`（已迁移 jOOQ/PostgreSQL 的遗留） | 可清理，避免依赖混淆 |
| E-3 | `application-dev.yaml` 的 `jpa.ddl-auto: update` 虽无 `@Entity` 已失效 | 改为 `none`，防止未来误引入实体触发改表 |
| E-4 | **JDK 23+ 不再自动运行 classpath 上的注解处理器**，本项目用 JDK 25 且 `pom.xml` 未显式声明处理器，导致 Lombok/MapStruct **静默失效**，全量编译报 307 个「找不到符号 log / getXxx / setXxx / 构造器」错误 | **已修复**：`pom.xml` 增加 `maven-compiler-plugin` 的 `annotationProcessorPaths`（lombok + lombok-mapstruct-binding + mapstruct-processor）。修复后 `mvn clean compile` 通过 |
| E-5 | `pom.xml` 的 jOOQ codegen 参数 `interfaceOnly` 非法（`org.jooq.meta.jaxb.Generate` 无此字段），`mvn generate-sources` 一直失败，`target/generated-sources` 长期为**过期生成代码** | **已修复**：改为合法参数 `<interfaces>true</interfaces>`。这也是此前误判「`posts.status` 默认 2」的根源 |

---

## 7. 落地清单（对应 supplementary-schema.sql）

| 优先级 | 动作 |
|---|---|
| P0 | 建 `blog_post_embeddings` + pgvector 扩展；`ai_messages` 加 `sources`；`posts` 加 `embedding_status` |
| P1 | `users` 加 `role`；统一日志表 `user_id` 类型 |
| P3 | `posts` 复合索引；`comments` 复合索引 |
| 事后 | 重跑 jOOQ codegen（`mvn generate-sources`）使新列/新表生成到 `com.syt.blog.jooq` |
