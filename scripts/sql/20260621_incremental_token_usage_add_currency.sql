-- =========================================================
-- 增量迁移：ai_token_usage 表增加币种和缓存命中字段
-- 日期：2026-06-21
-- 说明：DeepSeek 输入/输出分开计价 + 缓存命中 98% 折扣
-- =========================================================

-- Step 1: 添加币种列
ALTER TABLE ai_token_usage
    ADD COLUMN IF NOT EXISTS estimated_cost_currency VARCHAR(10) NULL COMMENT '费用币种 (CNY / USD)';

-- Step 2: 添加缓存命中 Token 列
ALTER TABLE ai_token_usage
    ADD COLUMN IF NOT EXISTS cached_tokens INT NULL DEFAULT 0 COMMENT '缓存命中的输入 Token 数';

-- Step 3: 回填旧数据的币种（deepseek 系列 → CNY，其他 → USD）
UPDATE ai_token_usage
SET estimated_cost_currency = 'CNY'
WHERE estimated_cost_currency IS NULL
  AND (model_name LIKE 'deepseek%' OR model_name LIKE 'deepseek-%');

UPDATE ai_token_usage
SET estimated_cost_currency = 'USD'
WHERE estimated_cost_currency IS NULL;

-- Step 4: 补充索引
CREATE INDEX IF NOT EXISTS idx_token_usage_currency
    ON ai_token_usage (estimated_cost_currency);
