-- =============================================================
-- 精简 blog_posts 字段 + 合并 blog_scheduled_posts
-- 对应 docs/database-review.md 的表精简决策
-- 执行方式：psql / pgAdmin 直连 postgres 库后运行本脚本
-- 执行后：需重跑 jOOQ codegen  `mvn generate-sources`
--         使字段变更生成到 com.syt.blog.jooq
-- =============================================================

-- -------------------------------------------------------------
-- 1. 先处理依赖 is_top 的复合索引（supplementary-schema.sql 中所建）
--    删 is_top 列会使该索引失效，故先删后重建
-- -------------------------------------------------------------
DROP INDEX IF EXISTS idx_posts_status_top_created;

-- -------------------------------------------------------------
-- 2. 重建列表查询索引（去掉 is_top，保留 status + 时间倒序）
-- -------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_posts_status_top_created
    ON blog_posts (status, created_at DESC);

-- -------------------------------------------------------------
-- 3. 删除 4 个冗余字段
--    toc_html     仅 DTO 残留的死字段
--    is_top       仅查询返回，从未真正用于置顶排序
--    reading_time 纯展示字段
--    allow_comment 移除「禁评」功能（评论服务的禁评检查已同步删除）
-- -------------------------------------------------------------
ALTER TABLE blog_posts
    DROP COLUMN IF EXISTS toc_html,
    DROP COLUMN IF EXISTS is_top,
    DROP COLUMN IF EXISTS allow_comment,
    DROP COLUMN IF EXISTS reading_time;

-- -------------------------------------------------------------
-- 4. 合并定时发布：给 blog_posts 加计划发布时间（可空）
--    约定：status=0（草稿）且 scheduled_at 非空 = 定时待发布
--    （定时发布功能尚未实现，此为预留字段）
-- -------------------------------------------------------------
ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;

COMMENT ON COLUMN blog_posts.scheduled_at IS '计划发布时间（非空且 status=0 表示定时待发布）';

-- -------------------------------------------------------------
-- 5. 把旧表数据回填到 posts.scheduled_at
--    blog_scheduled_posts 业务代码零引用，通常为空；稳妥起见保留数据。
--    仅回填时间，不回填状态，避免污染 posts.status 语义
-- -------------------------------------------------------------
UPDATE blog_posts p SET scheduled_at = s.scheduled_at
    FROM blog_scheduled_posts s WHERE p.id = s.post_id;

-- -------------------------------------------------------------
-- 6. 删除定时发布表（其 uk_scheduled_posts_post_id 唯一索引、
--    trg_scheduled_posts_updated_at 触发器、外键随表一并移除）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS blog_scheduled_posts;

-- =============================================================
-- 执行后检查：
--   \d blog_posts        -- 确认 4 字段已删、scheduled_at 已加
--   SELECT to_regclass('blog_scheduled_posts');  -- 应返回 NULL
--   SELECT indexname FROM pg_indexes WHERE tablename='blog_posts';
--     -- 确认 idx_posts_status_top_created 仍在（已重建）
-- =============================================================
