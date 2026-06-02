-- 操作日志上下文字段增量脚本。
-- 用途：保留现有数据，在 sys_operation_log 中补齐客户端和操作对象信息，便于审计排查。
-- 执行方式：mysql -uroot logistics_management < scripts/sql/20260602_incremental_operation_log_context.sql

set @schema_name = database();

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add column client_ip varchar(45) null comment ''客户端 IP，优先取代理头中的真实 IP'' after error_message',
        'select ''sys_operation_log.client_ip already exists'''
    )
    from information_schema.columns
    where table_schema = @schema_name and table_name = 'sys_operation_log' and column_name = 'client_ip'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add column user_agent varchar(255) null comment ''浏览器或客户端标识'' after client_ip',
        'select ''sys_operation_log.user_agent already exists'''
    )
    from information_schema.columns
    where table_schema = @schema_name and table_name = 'sys_operation_log' and column_name = 'user_agent'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add column request_params text null comment ''安全请求参数摘要，不记录密码和 token'' after user_agent',
        'select ''sys_operation_log.request_params already exists'''
    )
    from information_schema.columns
    where table_schema = @schema_name and table_name = 'sys_operation_log' and column_name = 'request_params'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add column target_id varchar(64) null comment ''操作对象 ID，例如记录 ID、角色 ID、订单号'' after request_params',
        'select ''sys_operation_log.target_id already exists'''
    )
    from information_schema.columns
    where table_schema = @schema_name and table_name = 'sys_operation_log' and column_name = 'target_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        count(*) = 0,
        'alter table sys_operation_log add index idx_operation_log_target (target_id)',
        'select ''idx_operation_log_target already exists'''
    )
    from information_schema.statistics
    where table_schema = @schema_name and table_name = 'sys_operation_log' and index_name = 'idx_operation_log_target'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
