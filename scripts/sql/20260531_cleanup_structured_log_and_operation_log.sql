-- 清理已删除的结构化日志菜单，并补齐操作日志异常信息字段。
-- 可重复执行，适用于已经跑过旧增量脚本的本地数据库。

drop procedure if exists add_column_if_missing;

delimiter //
create procedure add_column_if_missing(
    in table_name_param varchar(64),
    in column_name_param varchar(64),
    in column_definition_param text
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = table_name_param
          and column_name = column_name_param
    ) then
        set @ddl = concat('alter table ', table_name_param, ' add column ', column_name_param, ' ', column_definition_param);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //
delimiter ;

call add_column_if_missing('sys_operation_log', 'error_message', 'text null comment ''异常信息，操作失败时记录异常原因便于排障''');

delete rm
from sys_role_menu rm
join sys_menu m on m.id = rm.menu_id
where m.menu_path = '/system/structured-logs';

delete from sys_menu
where menu_path = '/system/structured-logs';

drop procedure if exists add_column_if_missing;
