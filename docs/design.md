# 智能个人博客系统 · 功能流程设计文档

> 版本：v1.0　|　日期：2026-09-22
> 范围：三条 P0 核心闭环的流程、时序、数据流转、同步/异步边界与失败降级
> 前置文档：《智能个人博客系统-功能设计文档.md》

---

## 0. 三条闭环总览

| 编号 | 闭环 | 核心角色 | 是否已跑通 |
|---|---|---|---|
| L1 | 文章发布 + AI 辅助写作 | 博主 → 编辑器 → 后端 → DeepSeek → DB | SSE 流式已跑通 |
| L2 | 读者阅读 + 评论 | 访客 → 前端 → 后端 → DB | — |
| L3 | RAG 智能问答 | 访客 → 前端 → 后端 → pgvector → DeepSeek | — |

---

## 1. 闭环一：文章发布 + AI 辅助写作

### 1.1 关键设计决策（先定死）

| 决策 | 结论 |
|---|---|
| 向量化时机 | **异步**：发布事务提交成功后触发 |
| 向量化失败文章能否发布 | **能**，标记 `embedding_status = FAILED`，提供手动重试 |
| 草稿是否向量化 | 否，仅 `PUBLISHED` 状态 |
| 文章更新后向量是否重建 | 是，先删旧 chunk 再插入新 chunk |
| AI 生成内容如何采纳 | 用户**手动确认**后写入正文，不自动替换 |
| AI 调用是否落库 | 是，写 `ai_assist_log` |
| AI 摘要/标题失败是否阻塞发布 | 否 |
| SSE 中途断流 | 前端保留已收内容，可重试 |

### 1.2 时序步骤

**阶段 A：AI 写作辅助（独立于发布，可随时触发）**

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| A1 | 前端编辑器 → 后端 `AiController` | 入参：`type(CONTINUE/POLISH)`、选中文本 `content`、指令 `instruction` | 同步建立连接 | — |
| A2 | `AiController` → `AiService` | 校验登录、拼装 prompt | 同步 | 未登录返回 401 |
| A3 | `AiService` → `LlmClient`（DeepSeek） | 出参：SSE 流式 token | 异步流式（SSE） | 超时/限流 → 返回兜底文案「AI 服务繁忙」 |
| A4 | `AiService` → `AiAssistLogRepository` | 落库：`user_id`、`type`、`prompt`、`response`、`token_used` | 异步（`@Async`） | 落库失败仅打日志，不影响返回 |
| A5 | 后端 → 前端 | SSE 事件流推送生成文本 | 流式 | 断流则前端保留已收内容，提示重试 |
| A6 | 前端 → 用户 | 用户**确认采纳**后写入正文 | — | — |

**阶段 B：发布文章（含向量化）**

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| B1 | 前端 → `PostController` | 入参：`title`、`content`(Markdown)、`summary`、`category_id`、`tag_ids`、`cover_url` | 同步 | 校验失败返回 400 |
| B2 | `PostController` → `PostService` | 组装 `Post` 实体 | 同步 | — |
| B3 | `PostService` → `PostRepository` | 落库 `post`（`status=PUBLISHED`、`published_at=now`） | 同步事务 | 事务回滚，返回失败 |
| B4 | `PostService` → `PostTagRepository` | 落库 `post_tag` 关联 | 同步事务（与 B3 同事务） | 回滚 |
| B5 | `PostService` → **异步触发向量化** | 发布事务提交后，调用 `EmbeddingService.vectorize(postId)` | **异步**（`@Async` 或事务后事件） | 见 B6 |
| B6 | `EmbeddingService` → DeepSeek Embedding | 分块（500~800 字）→ 逐块向量化 | 异步 | **失败则 `embedding_status=FAILED`**，文章已正常发布 |
| B7 | `EmbeddingService` → `PostEmbeddingRepository` | 落库 `post_embedding`（`post_id`、`chunk_index`、`content`、`embedding`）；更新 `post.embedding_status=READY` | 异步 | 部分 chunk 失败重试 N 次，仍失败置 FAILED |
| B8 | 前端 → 用户 | 返回「发布成功」 | 同步返回 | — |

> 说明：B5 向量化严格放在**发布事务提交之后**，避免「事务未提交、向量先写入」导致的数据不一致。实现上可用 Spring 事务同步回调（`TransactionSynchronization`）或简单 `@Async` + 短暂延迟。

### 1.3 数据流转字段小结

- 写库（同步）：`post`（title/content/summary/status/published_at/category_id）、`post_tag`
- 写库（异步）：`post_embedding`、`post.embedding_status`、`ai_assist_log`

---

## 2. 闭环二：读者阅读 + 评论

### 2.1 关键设计决策（先定死）

| 决策 | 结论 |
|---|---|
| 评论默认状态 | **待审核** `PENDING`，博主通过后可见 |
| 是否必须登录 | 是，`comment.user_id` 非空 |
| 博主能否回复（嵌套） | 能，一层嵌套，用 `parent_id` |
| 浏览量统计 | 详情请求**异步 +1**，不阻塞返回 |
| 浏览量去重 | 不去重（毕设够用） |
| 文章下架后评论 | 随文章一起隐藏 |
| 评论提交失败 | 前端保留输入，提示重试 |

### 2.2 时序步骤

**阶段 A：浏览文章**

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| A1 | 前端 → `PostController` | GET `/api/posts/{id}` | 同步 | 不存在/下架返回 404 |
| A2 | `PostService` → `PostRepository` | 查询 `post`（过滤 `status=PUBLISHED`） | 同步 | — |
| A3 | `PostService` → 异步浏览量 | `view_count + 1` | **异步** | 失败不影响详情返回 |
| A4 | `PostService` → `PostTagRepository` / `CategoryRepository` | 联查标签、分类 | 同步 | — |
| A5 | 后端 → 前端 | 返回文章详情（正文 Markdown） | 同步 | — |
| A6 | 前端渲染 | markdown-it + highlight.js 渲染 | — | XSS：渲染前转义 HTML |

**阶段 B：发表评论**

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| B1 | 前端 → `CommentController` | 入参：`post_id`、`content`、`parent_id?` | 同步 | 未登录返回 401；内容为空/超长返回 400 |
| B2 | `CommentService` → `CommentRepository` | 落库 `comment`（`status=PENDING`、`user_id`、`created_at`） | 同步 | 失败返回错误提示 |
| B3 | 后端 → 前端 | 返回「评论已提交，待审核」 | 同步 | — |

**阶段 C：博主审核 / 回复**

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| C1 | 前端后台 → `CommentController` | PUT `/api/comments/{id}/status`，入参 `APPROVED/REJECTED` | 同步 | 非博主返回 403 |
| C2 | `CommentService` → `CommentRepository` | 更新 `comment.status` | 同步 | — |
| C3 | 博主回复 | 新建评论，`parent_id = 被回复评论 id`，`status=APPROVED`（博主自审） | 同步 | — |

### 2.3 数据流转字段小结

- 写库：`comment`（content/user_id/parent_id/status）
- 读库：`post`、`post_tag`、`category`、`comment`（按 `status=APPROVED` 过滤）
- 异步：`post.view_count`

---

## 3. 闭环三：RAG 智能问答

### 3.1 关键设计决策（先定死）

| 决策 | 结论 |
|---|---|
| 是否必须登录 | 匿名可用，**限流**（1 分钟 5 次） |
| 召回 Top-K | K=5，相似度阈值 0.5 |
| 召回为空怎么答 | 返回兜底文案「暂未找到相关内容」，**不调用 LLM** |
| 引用来源怎么带 | SSE 末尾推 `sources` 事件（post_id + 标题 + 链接 + 相似度） |
| 问答是否落库 | 是，写 `ai_assist_log`（type=QA） |
| 向量未就绪的文章 | 检索时过滤 `embedding_status != READY`，命中不到走兜底 |
| 多篇文章组织 prompt | 按相似度排序，拼接 Top-K 段落，带标题上下文 |
| 防幻觉 | system prompt 强制「只依据给定段落回答，无依据则明说」 |
| SSE 中断 | 保留已收内容，引用来源照常展示 |

### 3.2 时序步骤

| 步骤 | 调用方 → 被调方 | 数据流动 | 同步/异步 | 失败处理 |
|---|---|---|---|---|
| S1 | 前端 → `AiController` | POST `/api/ai/chat`，入参 `question` | 同步建立连接 | 匿名超限 → 返回 429「请求过于频繁」 |
| S2 | `AiService` → `EmbeddingService` | 将 `question` 向量化 | 同步 | 向量化失败 → 走关键词兜底或直接兜底文案 |
| S3 | `EmbeddingService` → `PostEmbeddingRepository` | pgvector 相似度检索，`WHERE embedding_status=READY`，取 Top-K(5)，阈值 0.5 | 同步 | 无结果 → 跳 S7 兜底 |
| S4 | `AiService` → 拼装 prompt | system（角色+防幻觉约束）+ 召回段落 + 用户问题 | 同步 | — |
| S5 | `AiService` → `LlmClient`（DeepSeek） | SSE 流式生成回答 | 异步流式（SSE） | 超时/限流 → 返回兜底文案 |
| S6 | 后端 → 前端 | 先推 `content` 事件（正文），末尾推 `sources` 事件（引用来源） | 流式 | 断流保留已收内容 |
| S7 | 后端 → 前端（兜底） | 无召回命中时返回固定文案，**不调 LLM** | 同步 | — |
| S8 | `AiService` → `AiAssistLogRepository` | 落库：`user_id`、`type=QA`、`prompt=question`、`response`、`token_used` | 异步（`@Async`） | 失败仅打日志 |

### 3.3 引用来源数据结构约定

SSE `sources` 事件返回：
```json
[
  { "post_id": 12, "title": "Redis 持久化配置", "url": "/posts/12", "score": 0.82 },
  { "post_id": 7,  "title": "缓存踩坑记录",   "url": "/posts/7",  "score": 0.71 }
]
```

### 3.4 数据流转字段小结

- 读库：`post_embedding`（向量检索）、`post`（取标题/链接）
- 写库（异步）：`ai_assist_log`
- 外部调用：DeepSeek Embedding + DeepSeek Chat

---

## 4. 流程 → 关键决策 → 数据库字段影响 汇总表

| 流程 | 关键决策 | 对数据库字段的影响 |
|---|---|---|
| L1 发布+AI | 异步向量化；失败仍可发布；更新重建向量 | `post.embedding_status`（新增，枚举 PENDING/READY/FAILED）；`post_embedding`（post_id/chunk_index/content/embedding） |
| L1 发布+AI | AI 调用落库 | `ai_assist_log`（type/prompt/response/token_used） |
| L2 阅读+评论 | 默认待审核；一层嵌套回复 | `comment.status`（PENDING/APPROVED/REJECTED）；`comment.parent_id` |
| L2 阅读+评论 | 浏览量异步 +1 | `post.view_count` |
| L3 RAG 问答 | Top-K=5、阈值 0.5；来源落库 | `post_embedding.embedding`（vector 维度）；`ai_assist_log.type=QA` |
| L3 RAG 问答 | 向量未就绪过滤 | `post.embedding_status` 参与检索过滤 |

---

## 5. 对数据库设计的增量影响（相对功能设计文档 v1.0）

| 字段 | 实体 | 类型 | 说明 |
|---|---|---|---|
| `embedding_status` | `post` | varchar(20) | PENDING / READY / FAILED，新增 |
| `parent_id` | `comment` | bigint | 父评论，一层嵌套，新增 |
| `embedding` | `post_embedding` | vector(1024) | 向量，需 pgvector 扩展，维度待定（DeepSeek 通常 1024/1536） |
| `chunk_index` | `post_embedding` | int | 分块序号，新增 |

> 结论：相对 v1.0 数据模型，主要新增 **`post.embedding_status`** 字段与 **`comment.parent_id`** 字段，其余沿用。向量维度建议在联调 DeepSeek Embedding 时实测确认（常见 1024 维）。
