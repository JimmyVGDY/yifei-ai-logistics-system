-- 用途：为权限目录补齐列级权限敏感标记。
-- 说明：可重复执行；不清空、不重建现有业务数据。

set @schema_name = database();

drop procedure if exists add_column_if_missing;
delimiter //
create procedure add_column_if_missing(in p_table varchar(64), in p_column varchar(64), in p_definition text)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = @schema_name
          and table_name = p_table
          and column_name = p_column
    ) then
        set @ddl = concat('alter table ', p_table, ' add column ', p_column, ' ', p_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //
delimiter ;

call add_column_if_missing(
    'sys_permission',
    'sensitive_flag',
    'tinyint not null default 0 comment ''敏感列标记：1敏感，0普通'''
);

-- 历史权限默认按普通权限处理；应用启动时会根据 StandardColumnRegistry 修正列权限的敏感标记。
update sys_permission
set sensitive_flag = coalesce(sensitive_flag, 0)
where sensitive_flag is null;

alter table sys_permission
    modify column sensitive_flag tinyint not null default 0 comment '敏感列标记：1敏感，0普通';

drop procedure if exists add_column_if_missing;
