-- 物流管理系统补齐逻辑删除与乐观锁字段（第四批）
-- 用途：为前三批迁移未覆盖的系统权限表和登录历史表补齐字段。
-- 说明：可重复执行。

delimiter //

drop procedure if exists add_col_if_missing//
create procedure add_col_if_missing(
    in p_table_name varchar(128),
    in p_column_name varchar(128),
    in p_column_definition varchar(512)
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = p_table_name
          and column_name = replace(p_column_name, '`', '')
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add column ', p_column_name, ' ', p_column_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

drop procedure if exists add_idx_if_missing//
create procedure add_idx_if_missing(
    in p_table_name varchar(128),
    in p_index_name varchar(128),
    in p_index_definition varchar(512)
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

drop procedure if exists migrate_del_ver//
create procedure migrate_del_ver(in p_table_name varchar(128))
begin
    call add_col_if_missing(p_table_name, 'deleted', 'tinyint not null default 0 comment ''logic_delete_flag''');
    call add_col_if_missing(p_table_name, '`version`', 'int not null default 0 comment ''optimistic_lock_version''');
end//

delimiter ;

call migrate_del_ver('sys_permission');
call migrate_del_ver('sys_role_permission');
call migrate_del_ver('sys_user_permission');
call migrate_del_ver('sys_login_history');

call add_idx_if_missing('sys_permission', 'idx_sp_deleted', '(deleted)');
call add_idx_if_missing('sys_role_permission', 'idx_srp_deleted', '(deleted)');
call add_idx_if_missing('sys_user_permission', 'idx_sup_deleted', '(deleted)');
call add_idx_if_missing('sys_login_history', 'idx_slh_deleted', '(deleted)');

drop procedure if exists migrate_del_ver;
drop procedure if exists add_idx_if_missing;
drop procedure if exists add_col_if_missing;
