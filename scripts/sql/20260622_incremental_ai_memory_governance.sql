-- ============================================
-- AI 长期记忆治理字段（2026-06-22）
-- 说明：可重复执行；只补字段和索引，不清空、不覆盖现有长期记忆。
-- ============================================

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;

delimiter //

create procedure add_column_if_missing(
    in p_table_name varchar(64),
    in p_column_name varchar(64),
    in p_column_definition text
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = p_table_name
          and column_name = p_column_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add column ', p_column_name, ' ', p_column_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

create procedure add_index_if_missing(
    in p_table_name varchar(64),
    in p_index_name varchar(64),
    in p_index_definition text
)
begin
    if not exists (
        select 1
        from information_schema.statistics
        where table_schema = database()
          and table_name = p_table_name
          and index_name = p_index_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add index ', p_index_name, ' ', p_index_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

delimiter ;

-- 记忆治理字段：让一条长期记忆具备作用域、冲突组、证据和替代关系。
call add_column_if_missing('ai_user_memory', 'memory_key',
    'varchar(128) null comment ''记忆稳定键，用于同类记忆去重和画像编译'' after memory_type');

call add_column_if_missing('ai_user_memory', 'memory_scope',
    'varchar(32) not null default ''GLOBAL'' comment ''作用域：GLOBAL/MODULE/SCENARIO/ENTITY'' after memory_key');

call add_column_if_missing('ai_user_memory', 'scope_value',
    'varchar(128) null comment ''作用域取值，例如 tasks、task_exception、answer_style'' after memory_scope');

call add_column_if_missing('ai_user_memory', 'conflict_group',
    'varchar(128) null comment ''冲突组，同组只能有有限条记忆参与召回'' after scope_value');

call add_column_if_missing('ai_user_memory', 'priority',
    'int not null default 50 comment ''优先级，数值越大越优先'' after conflict_group');

call add_column_if_missing('ai_user_memory', 'evidence_count',
    'int not null default 1 comment ''正向证据次数'' after priority');

call add_column_if_missing('ai_user_memory', 'negative_count',
    'int not null default 0 comment ''负向反馈次数'' after evidence_count');

call add_column_if_missing('ai_user_memory', 'superseded_by',
    'bigint null comment ''替代该记忆的新记忆ID'' after negative_count');

call add_column_if_missing('ai_user_memory', 'effective_from',
    'datetime null comment ''生效开始时间'' after superseded_by');

call add_column_if_missing('ai_user_memory', 'effective_until',
    'datetime null comment ''生效截止时间，为空表示长期有效'' after effective_from');

call add_column_if_missing('ai_user_memory', 'last_applied_at',
    'datetime null comment ''最近一次真正应用到回答的时间'' after last_recall_time');

call add_column_if_missing('ai_user_memory', 'policy_json',
    'text null comment ''脱敏后的策略JSON，用于画像编译和前端解释'' after last_applied_at');

-- 历史记忆补默认值，保持旧数据可参与治理。
update ai_user_memory
set memory_scope = ifnull(nullif(memory_scope, ''), 'GLOBAL'),
    memory_key = ifnull(memory_key, concat(memory_type, ':', left(sha2(memory_summary, 256), 24))),
    conflict_group = ifnull(conflict_group, memory_type),
    priority = ifnull(priority, 50),
    evidence_count = greatest(ifnull(evidence_count, 1), 1),
    effective_from = ifnull(effective_from, created_at),
    status = case
        when status is null or status = '' then 'ACTIVE'
        else status
    end
where deleted = 0;

-- 画像治理字段：画像是由有效记忆编译出来的缓存，不作为不可追溯真值。
call add_column_if_missing('ai_user_profile', 'profile_version',
    'int not null default 1 comment ''画像版本号'' after query_habits');

call add_column_if_missing('ai_user_profile', 'answer_style_json',
    'text null comment ''回答风格画像JSON，脱敏'' after profile_version');

call add_column_if_missing('ai_user_profile', 'query_strategy_json',
    'text null comment ''查询策略画像JSON，脱敏'' after answer_style_json');

call add_column_if_missing('ai_user_profile', 'module_affinity_json',
    'text null comment ''模块偏好画像JSON，脱敏'' after query_strategy_json');

call add_column_if_missing('ai_user_profile', 'profile_confidence',
    'decimal(6,4) not null default 0.8000 comment ''画像总体置信度'' after module_affinity_json');

call add_column_if_missing('ai_user_profile', 'compiled_at',
    'datetime null comment ''画像最近编译时间'' after profile_confidence');

-- 事件扩展字段：记录治理过程中的结构化摘要，正文仍保持脱敏。
call add_column_if_missing('ai_memory_event', 'event_detail_json',
    'text null comment ''脱敏后的事件明细JSON'' after event_summary');

call add_index_if_missing('ai_user_memory', 'idx_ai_memory_governance',
    '(user_id, user_code, status, memory_scope, conflict_group, deleted)');

call add_index_if_missing('ai_user_memory', 'idx_ai_memory_effective',
    '(effective_from, effective_until, last_applied_at)');

call add_index_if_missing('ai_user_profile', 'idx_ai_profile_compile',
    '(compiled_at, profile_version)');

drop procedure if exists add_index_if_missing;
drop procedure if exists add_column_if_missing;
