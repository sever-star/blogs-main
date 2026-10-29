-- =============================================================
-- 删除 blog_user_settings 表及其自动初始化触发器/函数
-- 说明:
--   blog_user_settings 的数据由 blog_users 上的 AFTER INSERT
--   触发器 trg_after_user_insert 自动写入,应用层不引用该表。
--   删除表前需先拆除触发器与函数,顺序: 触发器 -> 函数 -> 表。
-- 执行前请确认已备份该表数据(如有需要)。
-- =============================================================

-- 1. 删除绑定在 blog_users 表上的触发器(若存在)
DROP TRIGGER IF EXISTS trg_after_user_insert ON blog_users;

-- 2. 删除触发器函数
DROP FUNCTION IF EXISTS trg_after_user_insert();

-- 3. 删除 blog_user_settings 表(同时删除其索引、约束、外键)
DROP TABLE IF EXISTS blog_user_settings;
