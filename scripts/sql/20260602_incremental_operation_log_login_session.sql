-- 增量迁移：给操作日志补充登录会话审计ID。
-- 说明：脚本可重复执行，不清空现有数据，不放入应用启动自动初始化流程。

set @schema_name = database();

set @column_exists = (
    select count(1)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'sys_operation_log'
      and column_name = 'login_session_id'
);
set @ddl = if(
    @column_exists = 0,
    'alter table sys_operation_log add column login_session_id varchar(32) null comment ''登录会话审计ID，串联一次登录期间的所有操作'' after trace_id',
    'select ''sys_operation_log.login_session_id already exists'''
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @index_exists = (
    select count(1)
    from information_schema.statistics
    where table_schema = @schema_name
      and table_name = 'sys_operation_log'
      and index_name = 'idx_operation_log_login_session_id'
);
set @ddl = if(
    @index_exists = 0,
    'alter table sys_operation_log add index idx_operation_log_login_session_id (login_session_id)',
    'select ''idx_operation_log_login_session_id already exists'''
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
