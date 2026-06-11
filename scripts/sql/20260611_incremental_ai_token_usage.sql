-- AI Token usage tracking table
-- Records token consumption per model call for cost monitoring.

create table if not exists ai_token_usage (
    id bigint primary key comment 'snowflake ID',
    model_name varchar(64) not null comment 'model name, e.g. deepseek-chat, gpt-4o-mini',
    purpose varchar(32) not null comment 'call purpose: chat, sql_generate, sql_self_check, sql_repair, memory_extract',
    prompt_tokens int not null default 0 comment 'input token count',
    completion_tokens int not null default 0 comment 'output token count',
    total_tokens int not null default 0 comment 'prompt + completion',
    user_id varchar(64) null comment 'caller user ID',
    user_code varchar(64) null comment 'caller user code',
    conversation_id varchar(64) null comment 'AI conversation ID for chat calls',
    estimated_cost decimal(12, 6) not null default 0 comment 'estimated cost in USD',
    model_base_url varchar(255) null comment 'model API base URL',
    duration_ms bigint null comment 'API call duration in milliseconds',
    created_at timestamp not null default current_timestamp comment 'record creation time',
    deleted tinyint not null default 0 comment 'logic delete flag',
    version int not null default 0 comment 'optimistic lock version',
    index idx_token_usage_model (model_name, created_at),
    index idx_token_usage_user (user_id, created_at),
    index idx_token_usage_purpose (purpose, created_at),
    index idx_token_usage_created (created_at),
    index idx_token_usage_deleted (deleted)
) comment='AI Token usage tracking table for cost monitoring';
