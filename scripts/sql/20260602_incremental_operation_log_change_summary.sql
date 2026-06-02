-- 操作日志变更摘要字段增量脚本。
-- 用途：保留现有操作日志数据，在 sys_operation_log 中补齐脱敏后的操作前后变化摘要。

set @schema_name = database();

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add column change_summary text null comment ''脱敏后的操作前后变化摘要'' after target_id',
        'select ''sys_operation_log.change_summary already exists'''
    )
    from information_schema.columns
    where table_schema = @schema_name and table_name = 'sys_operation_log' and column_name = 'change_summary'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
