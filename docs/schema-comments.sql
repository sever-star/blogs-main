-- =============================================================
-- blog 系统 · 全库表/字段注释（COMMENT ON）
-- 语义依据：docs/enum-dictionary.md（状态枚举统一字典）
-- 执行方式：psql / pgAdmin 直连 postgres 库后运行本脚本
--   docker exec -i pgvector-db psql -U postgres -d postgres < docs/schema-comments.sql
-- 说明：COMMENT ON 可重复执行（后写覆盖前写），天然幂等
-- =============================================================

-- -------------------------------------------------------------
-- blog_users  用户表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_users                     IS '用户表';
COMMENT ON COLUMN blog_users.id                  IS '用户ID';
COMMENT ON COLUMN blog_users.username            IS '用户名（登录账号，唯一）';
COMMENT ON COLUMN blog_users.nickname            IS '昵称';
COMMENT ON COLUMN blog_users.password            IS '密码（BCrypt 加密存储）';
COMMENT ON COLUMN blog_users.email               IS '邮箱';
COMMENT ON COLUMN blog_users.avatar              IS '头像 URL';
COMMENT ON COLUMN blog_users.bio                 IS '个人简介';
COMMENT ON COLUMN blog_users.status              IS '启用状态：0=禁用 1=正常';
COMMENT ON COLUMN blog_users.last_login_at       IS '最后登录时间';
COMMENT ON COLUMN blog_users.website             IS '个人网站';
COMMENT ON COLUMN blog_users.github              IS 'GitHub 主页';
COMMENT ON COLUMN blog_users.weibo               IS '微博主页';
COMMENT ON COLUMN blog_users.created_at          IS '创建时间';
COMMENT ON COLUMN blog_users.updated_at          IS '更新时间';
COMMENT ON COLUMN blog_users.role                IS '角色：ADMIN=博主/管理员 USER=普通读者';

-- -------------------------------------------------------------
-- blog_posts  文章表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_posts                     IS '文章表';
COMMENT ON COLUMN blog_posts.id                  IS '文章ID';
COMMENT ON COLUMN blog_posts.user_id             IS '作者用户ID（blog_users.id）';
COMMENT ON COLUMN blog_posts.category_id         IS '分类ID（blog_categories.id，可空）';
COMMENT ON COLUMN blog_posts.title               IS '标题';
COMMENT ON COLUMN blog_posts.content_md          IS '正文（Markdown 源文）';
COMMENT ON COLUMN blog_posts.content_html        IS '正文（Markdown 渲染后的 HTML）';
COMMENT ON COLUMN blog_posts.summary             IS '摘要';
COMMENT ON COLUMN blog_posts.cover_image         IS '封面图 URL';
COMMENT ON COLUMN blog_posts.view_count          IS '浏览数';
COMMENT ON COLUMN blog_posts.like_count          IS '点赞数';
COMMENT ON COLUMN blog_posts.fav_count           IS '收藏数';
COMMENT ON COLUMN blog_posts.status              IS '发布状态：0=草稿 1=已发布 2=已下架';
COMMENT ON COLUMN blog_posts.scheduled_at        IS '计划发布时间（非空且 status=0 表示定时待发布）';
COMMENT ON COLUMN blog_posts.created_at          IS '创建时间';
COMMENT ON COLUMN blog_posts.updated_at          IS '更新时间';
COMMENT ON COLUMN blog_posts.deleted_at          IS '软删除时间（NULL=未删除）';
COMMENT ON COLUMN blog_posts.embedding_status    IS '向量化状态：0=待处理 1=已完成 2=失败（可重试）';
COMMENT ON COLUMN blog_posts.review_status       IS '审核状态：0=待审核 1=通过 2=拒绝';
COMMENT ON COLUMN blog_posts.reviewer_id         IS '审核人用户ID（blog_users.id，未审核为 NULL）';
COMMENT ON COLUMN blog_posts.reviewed_at         IS '审核时间（未审核为 NULL）';

-- -------------------------------------------------------------
-- blog_categories  文章分类表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_categories                IS '文章分类表';
COMMENT ON COLUMN blog_categories.id             IS '分类ID';
COMMENT ON COLUMN blog_categories.name           IS '分类名';
COMMENT ON COLUMN blog_categories.parent_id      IS '父分类ID（NULL=顶级分类）';
COMMENT ON COLUMN blog_categories.sort_order     IS '排序值（小的在前）';
COMMENT ON COLUMN blog_categories.created_at     IS '创建时间';

-- -------------------------------------------------------------
-- blog_tags  标签表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_tags                      IS '标签表';
COMMENT ON COLUMN blog_tags.id                   IS '标签ID';
COMMENT ON COLUMN blog_tags.name                 IS '标签名';
COMMENT ON COLUMN blog_tags.created_at           IS '创建时间';

-- -------------------------------------------------------------
-- blog_post_tags  文章-标签关联表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_post_tags                 IS '文章-标签关联表（多对多）';
COMMENT ON COLUMN blog_post_tags.post_id         IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_post_tags.tag_id          IS '标签ID（blog_tags.id）';

-- -------------------------------------------------------------
-- blog_post_versions  文章版本表（历史修订快照）
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_post_versions             IS '文章版本表（每次修订留档）';
COMMENT ON COLUMN blog_post_versions.id          IS '版本记录ID';
COMMENT ON COLUMN blog_post_versions.post_id     IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_post_versions.version     IS '版本号（从 1 递增）';
COMMENT ON COLUMN blog_post_versions.title       IS '该版本标题';
COMMENT ON COLUMN blog_post_versions.content_md  IS '该版本正文（Markdown）';
COMMENT ON COLUMN blog_post_versions.content_html IS '该版本正文（HTML）';
COMMENT ON COLUMN blog_post_versions.change_note IS '变更说明';
COMMENT ON COLUMN blog_post_versions.created_at  IS '版本创建时间';

-- -------------------------------------------------------------
-- blog_post_embeddings  文章向量分块表（RAG）
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_post_embeddings           IS '文章向量分块表（RAG 向量检索）';
COMMENT ON COLUMN blog_post_embeddings.id        IS '分块记录ID';
COMMENT ON COLUMN blog_post_embeddings.post_id   IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_post_embeddings.chunk_index IS '分块序号（从 0 开始）';
COMMENT ON COLUMN blog_post_embeddings.content   IS '分块文本';
COMMENT ON COLUMN blog_post_embeddings.embedding IS '向量 vector(1024)（余弦相似度检索）';
COMMENT ON COLUMN blog_post_embeddings.created_at IS '创建时间';

-- -------------------------------------------------------------
-- blog_comments  评论表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_comments                  IS '评论表';
COMMENT ON COLUMN blog_comments.id               IS '评论ID';
COMMENT ON COLUMN blog_comments.post_id          IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_comments.parent_id        IS '父评论ID（NULL=顶级评论）';
COMMENT ON COLUMN blog_comments.reply_to_id      IS '被回复的评论ID（楼中楼）';
COMMENT ON COLUMN blog_comments.user_id          IS '评论用户ID（blog_users.id，游客为 NULL）';
COMMENT ON COLUMN blog_comments.nickname         IS '昵称（游客评论用）';
COMMENT ON COLUMN blog_comments.email            IS '邮箱（游客评论用）';
COMMENT ON COLUMN blog_comments.avatar           IS '头像 URL';
COMMENT ON COLUMN blog_comments.content          IS '评论内容';
COMMENT ON COLUMN blog_comments.is_admin         IS '是否博主回复：0=普通 1=博主';
COMMENT ON COLUMN blog_comments.status           IS '审核状态：0=待审核 1=通过 2=拒绝';
COMMENT ON COLUMN blog_comments.created_at       IS '创建时间';

-- -------------------------------------------------------------
-- blog_comment_likes  评论点赞表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_comment_likes             IS '评论点赞表';
COMMENT ON COLUMN blog_comment_likes.id          IS '点赞记录ID';
COMMENT ON COLUMN blog_comment_likes.comment_id  IS '评论ID（blog_comments.id）';
COMMENT ON COLUMN blog_comment_likes.user_id     IS '用户ID（blog_users.id，游客为 NULL）';
COMMENT ON COLUMN blog_comment_likes.user_ip     IS '点赞者 IP（游客按 IP 去重）';
COMMENT ON COLUMN blog_comment_likes.created_at  IS '创建时间';

-- -------------------------------------------------------------
-- blog_post_likes  文章点赞表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_post_likes                IS '文章点赞表';
COMMENT ON COLUMN blog_post_likes.id             IS '点赞记录ID';
COMMENT ON COLUMN blog_post_likes.post_id        IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_post_likes.user_id        IS '用户ID（blog_users.id，游客为 NULL）';
COMMENT ON COLUMN blog_post_likes.user_ip        IS '点赞者 IP（游客按 IP 去重）';
COMMENT ON COLUMN blog_post_likes.created_at     IS '创建时间';

-- -------------------------------------------------------------
-- blog_post_favorites  文章收藏表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_post_favorites            IS '文章收藏表';
COMMENT ON COLUMN blog_post_favorites.id         IS '收藏记录ID';
COMMENT ON COLUMN blog_post_favorites.post_id    IS '文章ID（blog_posts.id）';
COMMENT ON COLUMN blog_post_favorites.user_id    IS '用户ID（blog_users.id）';
COMMENT ON COLUMN blog_post_favorites.created_at IS '创建时间';

-- -------------------------------------------------------------
-- blog_notifications  站内通知表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_notifications            IS '站内通知表';
COMMENT ON COLUMN blog_notifications.id         IS '通知ID';
COMMENT ON COLUMN blog_notifications.user_id    IS '接收用户ID（blog_users.id）';
COMMENT ON COLUMN blog_notifications.type       IS '通知类型（comment/like/reply 等）';
COMMENT ON COLUMN blog_notifications.title      IS '通知标题';
COMMENT ON COLUMN blog_notifications.content    IS '通知内容';
COMMENT ON COLUMN blog_notifications.link       IS '跳转链接';
COMMENT ON COLUMN blog_notifications.is_read    IS '是否已读：0=未读 1=已读';
COMMENT ON COLUMN blog_notifications.created_at IS '创建时间';

-- -------------------------------------------------------------
-- blog_links  友情链接表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_links                     IS '友情链接表';
COMMENT ON COLUMN blog_links.id                  IS '链接ID';
COMMENT ON COLUMN blog_links.name                IS '链接名';
COMMENT ON COLUMN blog_links.url                 IS '链接地址';
COMMENT ON COLUMN blog_links.logo                IS 'logo URL';
COMMENT ON COLUMN blog_links.description         IS '描述';
COMMENT ON COLUMN blog_links.sort_order          IS '排序值（小的在前）';
COMMENT ON COLUMN blog_links.status              IS '启用状态：0=禁用 1=启用';
COMMENT ON COLUMN blog_links.created_at          IS '创建时间';
COMMENT ON COLUMN blog_links.updated_at          IS '更新时间';

-- -------------------------------------------------------------
-- blog_refresh_tokens  刷新令牌表（JWT 续签）
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_refresh_tokens            IS '刷新令牌表（JWT 续签）';
COMMENT ON COLUMN blog_refresh_tokens.id         IS '记录ID';
COMMENT ON COLUMN blog_refresh_tokens.user_id    IS '用户ID（blog_users.id）';
COMMENT ON COLUMN blog_refresh_tokens.token      IS '刷新令牌';
COMMENT ON COLUMN blog_refresh_tokens.device_id  IS '设备标识';
COMMENT ON COLUMN blog_refresh_tokens.user_agent IS '客户端 UA';
COMMENT ON COLUMN blog_refresh_tokens.ip         IS '签发 IP';
COMMENT ON COLUMN blog_refresh_tokens.expires_at IS '过期时间';
COMMENT ON COLUMN blog_refresh_tokens.revoked    IS '是否已吊销：0=有效 1=已吊销';
COMMENT ON COLUMN blog_refresh_tokens.created_at IS '创建时间';
COMMENT ON COLUMN blog_refresh_tokens.updated_at IS '更新时间';

-- -------------------------------------------------------------
-- blog_login_logs  登录日志表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_login_logs                IS '登录日志表';
COMMENT ON COLUMN blog_login_logs.id             IS '日志ID';
COMMENT ON COLUMN blog_login_logs.user_id        IS '用户ID（blog_users.id，失败时可能为空/0）';
COMMENT ON COLUMN blog_login_logs.username       IS '登录用户名';
COMMENT ON COLUMN blog_login_logs.login_type     IS '登录方式：0=账号密码 1=第三方/OAuth';
COMMENT ON COLUMN blog_login_logs.ip             IS '登录 IP';
COMMENT ON COLUMN blog_login_logs.user_agent     IS '客户端 UA';
COMMENT ON COLUMN blog_login_logs.status         IS '登录结果：0=失败 1=成功';
COMMENT ON COLUMN blog_login_logs.fail_reason    IS '失败原因';
COMMENT ON COLUMN blog_login_logs.created_at     IS '创建时间';

-- -------------------------------------------------------------
-- blog_operation_logs  操作日志表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_operation_logs            IS '后台操作日志表';
COMMENT ON COLUMN blog_operation_logs.id         IS '日志ID';
COMMENT ON COLUMN blog_operation_logs.user_id    IS '操作人用户ID（blog_users.id）';
COMMENT ON COLUMN blog_operation_logs.username   IS '操作人用户名';
COMMENT ON COLUMN blog_operation_logs.action     IS '操作类型（如 create/update/delete）';
COMMENT ON COLUMN blog_operation_logs.target_type IS '目标类型（如 post/comment/user）';
COMMENT ON COLUMN blog_operation_logs.target_id  IS '目标ID';
COMMENT ON COLUMN blog_operation_logs.detail     IS '操作详情 JSON';
COMMENT ON COLUMN blog_operation_logs.ip         IS '操作 IP';
COMMENT ON COLUMN blog_operation_logs.user_agent IS '客户端 UA';
COMMENT ON COLUMN blog_operation_logs.created_at IS '创建时间';

-- -------------------------------------------------------------
-- blog_visit_logs  访问日志表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_visit_logs                IS '页面访问日志表';
COMMENT ON COLUMN blog_visit_logs.id             IS '日志ID';
COMMENT ON COLUMN blog_visit_logs.post_id        IS '文章ID（blog_posts.id，非文章页为 NULL）';
COMMENT ON COLUMN blog_visit_logs.user_id        IS '用户ID（blog_users.id，游客为 NULL）';
COMMENT ON COLUMN blog_visit_logs.user_ip        IS '访问者 IP';
COMMENT ON COLUMN blog_visit_logs.user_agent     IS '客户端 UA';
COMMENT ON COLUMN blog_visit_logs.referer        IS '来源页';
COMMENT ON COLUMN blog_visit_logs.visit_url      IS '访问地址';
COMMENT ON COLUMN blog_visit_logs.stay_duration  IS '停留时长（秒）';
COMMENT ON COLUMN blog_visit_logs.created_at     IS '创建时间';

-- -------------------------------------------------------------
-- blog_search_keywords  搜索关键词表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_search_keywords           IS '搜索关键词统计表';
COMMENT ON COLUMN blog_search_keywords.id        IS '记录ID';
COMMENT ON COLUMN blog_search_keywords.keyword   IS '搜索关键词';
COMMENT ON COLUMN blog_search_keywords.search_count IS '搜索次数';
COMMENT ON COLUMN blog_search_keywords.last_search_at IS '最后搜索时间';

-- -------------------------------------------------------------
-- blog_statistics  站点统计表（KV 计数）
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_statistics                IS '站点统计表（键值计数）';
COMMENT ON COLUMN blog_statistics.id             IS '记录ID';
COMMENT ON COLUMN blog_statistics.stat_key       IS '统计键（如 total_posts、total_users）';
COMMENT ON COLUMN blog_statistics.stat_value     IS '统计值';
COMMENT ON COLUMN blog_statistics.updated_at     IS '更新时间';

-- -------------------------------------------------------------
-- blog_ai_sessions  AI 对话会话表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_ai_sessions               IS 'AI 助手对话会话表';
COMMENT ON COLUMN blog_ai_sessions.id            IS '会话ID';
COMMENT ON COLUMN blog_ai_sessions.user_id       IS '用户ID（blog_users.id，游客为 NULL）';
COMMENT ON COLUMN blog_ai_sessions.user_ip       IS '游客 IP（游客会话用）';
COMMENT ON COLUMN blog_ai_sessions.title         IS '会话标题';
COMMENT ON COLUMN blog_ai_sessions.created_at    IS '创建时间';
COMMENT ON COLUMN blog_ai_sessions.updated_at    IS '更新时间';

-- -------------------------------------------------------------
-- blog_ai_messages  AI 对话消息表
-- -------------------------------------------------------------
COMMENT ON TABLE  blog_ai_messages               IS 'AI 助手对话消息表';
COMMENT ON COLUMN blog_ai_messages.id            IS '消息ID';
COMMENT ON COLUMN blog_ai_messages.session_id    IS '会话ID（blog_ai_sessions.id）';
COMMENT ON COLUMN blog_ai_messages.role          IS '角色：user=用户 assistant=AI system=系统';
COMMENT ON COLUMN blog_ai_messages.content       IS '消息内容';
COMMENT ON COLUMN blog_ai_messages.tokens        IS 'token 消耗量';
COMMENT ON COLUMN blog_ai_messages.status        IS '消息状态：0=失败 1=成功 2=生成中（流式中断）';
COMMENT ON COLUMN blog_ai_messages.created_at    IS '创建时间';
COMMENT ON COLUMN blog_ai_messages.sources       IS 'RAG 引用来源 JSON：[{post_id,title,url,score}]';
