-- 用途：保留现有数据，在 sys_operation_log 中补齐 AI 分层审计字段。
-- 执行方式：mysql -uroot logistics_management < scripts/sql/20260605_incremental_ai_operation_audit.sql
-- 说明：字段用于区分“用户问 AI”“AI 调用只读工具”“AI 生成回答”，不会清空或覆盖旧日志。

set @schema_name = database();

drop procedure if exists add_column_if_missing;
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
        where table_schema = @schema_name
          and table_name = p_table_name
          and column_name = p_column_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add column ', p_column_name, ' ', p_column_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//
delimiter ;

drop procedure if exists add_index_if_missing;
delimiter //
create procedure add_index_if_missing(
    in p_table_name varchar(64),
    in p_index_name varchar(64),
    in p_index_definition text
)
begin
    if not exists (
        select 1
        from information_schema.statistics
        where table_schema = @schema_name
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

call add_column_if_missing('sys_operation_log', 'operation_source', 'varchar(32) null comment ''操作来源：USER、USER_TO_AI、AI_TOOL、AI_RESPONSE、SYSTEM'' after change_summary');
call add_column_if_missing('sys_operation_log', 'executor_type', 'varchar(32) null comment ''执行者类型：USER、AI、SYSTEM'' after operation_source');
call add_column_if_missing('sys_operation_log', 'ai_conversation_id', 'varchar(64) null comment ''AI 会话 ID，用于串联一次 AI 对话'' after executor_type');
call add_column_if_missing('sys_operation_log', 'ai_message_id', 'varchar(64) null comment ''AI 单条消息 ID，预留给后续消息级审计'' after ai_conversation_id');
call add_column_if_missing('sys_operation_log', 'ai_tool_name', 'varchar(64) null comment ''AI 工具名称，例如业务数据查询、全局只读查找、日志排障'' after ai_message_id');
call add_column_if_missing('sys_operation_log', 'ai_tool_target', 'varchar(128) null comment ''AI 工具目标，例如客户管理、运单管理'' after ai_tool_name');
call add_column_if_missing('sys_operation_log', 'ai_readonly', 'tinyint null comment ''AI 工具是否只读，1=只读，0=非只读'' after ai_tool_target');
call add_column_if_missing('sys_operation_log', 'ai_prompt_summary', 'text null comment ''脱敏后的用户问题摘要'' after ai_readonly');
call add_column_if_missing('sys_operation_log', 'ai_result_summary', 'text null comment ''脱敏后的 AI 工具或回答结果摘要'' after ai_prompt_summary');

call add_index_if_missing('sys_operation_log', 'idx_operation_log_ai_source', '(operation_source, executor_type)');
call add_index_if_missing('sys_operation_log', 'idx_operation_log_ai_conversation', '(ai_conversation_id)');

drop procedure if exists add_index_if_missing;
drop procedure if exists add_column_if_missing;
