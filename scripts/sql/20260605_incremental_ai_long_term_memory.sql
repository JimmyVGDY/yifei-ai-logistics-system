-- 用途：为 AI 长期记忆和全链路审计补充表结构，保留现有数据。
set @schema_name = database();

drop procedure if exists add_column_if_missing;
delimiter //
create procedure add_column_if_missing(in p_table varchar(64), in p_column varchar(64), in p_definition text)
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = @schema_name and table_name = p_table and column_name = p_column
    ) then
        set @ddl = concat('alter table ', p_table, ' add column ', p_column, ' ', p_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //
delimiter ;

drop procedure if exists add_index_if_missing;
delimiter //
create procedure add_index_if_missing(in p_table varchar(64), in p_index varchar(64), in p_definition text)
begin
    if not exists (
        select 1 from information_schema.statistics
        where table_schema = @schema_name and table_name = p_table and index_name = p_index
    ) then
        set @ddl = concat('alter table ', p_table, ' add index ', p_index, ' ', p_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //
delimiter ;

create table if not exists ai_user_profile (
    id bigint primary key,
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    memory_enabled tinyint not null default 1 comment '是否启用长期记忆',
    answer_style varchar(255) null comment '回答风格偏好',
    favorite_modules text null comment '常用模块摘要',
    query_habits text null comment '常用查询习惯摘要',
    last_recall_time datetime null comment '最近一次长期记忆召回时间',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_ai_profile_user (user_id, user_code),
    key idx_ai_profile_enabled (memory_enabled, deleted)
) comment='AI账号级长期记忆画像';

create table if not exists ai_user_memory (
    id bigint primary key,
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    memory_type varchar(64) not null comment '记忆类型：回答风格、常用模块、查询习惯等',
    memory_title varchar(255) not null comment '脱敏后的记忆标题',
    memory_summary text not null comment '脱敏后的记忆摘要，不保存敏感原文',
    confidence decimal(6,4) not null default 0.8000 comment '自动抽取置信度',
    qdrant_point_id varchar(64) null comment 'Qdrant 向量点 ID',
    source_conversation_id varchar(64) null comment '来源 AI 会话 ID',
    source_trace_id varchar(64) null comment '来源 traceId',
    recall_count int not null default 0 comment '召回次数',
    last_recall_time datetime null comment '最近召回时间',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_memory_user_type (user_id, user_code, memory_type, deleted),
    key idx_ai_memory_point (qdrant_point_id),
    key idx_ai_memory_recall (last_recall_time, confidence)
) comment='AI账号级长期记忆';

create table if not exists ai_memory_event (
    id bigint primary key,
    memory_id bigint null comment '关联长期记忆 ID',
    event_type varchar(64) not null comment '事件类型：CREATE、RECALL、DELETE、CLEAR、SKIP 等',
    event_source varchar(64) not null comment '事件来源：USER、AI_MEMORY、SYSTEM',
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    trace_id varchar(64) null,
    operation_id varchar(64) null,
    login_session_id varchar(64) null,
    ai_conversation_id varchar(64) null,
    event_summary text null comment '脱敏后的事件摘要',
    created_at datetime not null default current_timestamp,
    key idx_ai_memory_event_user_time (user_id, user_code, created_at),
    key idx_ai_memory_event_trace (trace_id, operation_id, login_session_id),
    key idx_ai_memory_event_memory (memory_id)
) comment='AI长期记忆事件审计';

call add_column_if_missing('sys_operation_log', 'ai_memory_id', 'varchar(64) null comment ''AI 长期记忆 ID'' after ai_result_summary');
call add_column_if_missing('sys_operation_log', 'ai_memory_event_type', 'varchar(64) null comment ''AI 长期记忆事件类型'' after ai_memory_id');
call add_column_if_missing('sys_operation_log', 'ai_memory_source', 'varchar(64) null comment ''AI 长期记忆事件来源'' after ai_memory_event_type');
call add_column_if_missing('sys_operation_log', 'ai_memory_hit_count', 'int null comment ''AI 长期记忆召回命中数'' after ai_memory_source');
call add_column_if_missing('sys_operation_log', 'ai_memory_trace_summary', 'text null comment ''AI 长期记忆链路摘要，已脱敏'' after ai_memory_hit_count');

call add_index_if_missing('sys_operation_log', 'idx_operation_log_ai_memory', '(ai_memory_id, ai_memory_event_type)');

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;
