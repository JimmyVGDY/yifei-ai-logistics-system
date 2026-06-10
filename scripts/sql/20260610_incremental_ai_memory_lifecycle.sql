-- ============================================
-- AI 长期记忆生命周期管理（2026-06-10）
-- 执行方式：mysql -f -uroot logistics_management < this_file.sql
-- 使用 -f (force) 确保字段已存在时跳过错误继续执行
-- ============================================

ALTER TABLE ai_user_memory
    ADD COLUMN reinforce_count int not null default 0
    COMMENT '强化计数' AFTER recall_count;

ALTER TABLE ai_user_memory
    ADD COLUMN last_reinforced_at datetime null
    COMMENT '最后强化时间' AFTER reinforce_count;

ALTER TABLE ai_user_memory
    ADD COLUMN status varchar(20) not null default 'ACTIVE'
    COMMENT '生命周期状态' AFTER last_reinforced_at;

UPDATE ai_user_memory SET status = 'ACTIVE' WHERE status IS NULL OR status = '';

ALTER TABLE ai_user_memory
    ADD INDEX idx_ai_memory_status (status, last_reinforced_at);
