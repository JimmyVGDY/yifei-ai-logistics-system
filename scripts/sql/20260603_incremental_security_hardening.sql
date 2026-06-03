-- 物流管理系统安全增强增量脚本
-- 用途：保留现有数据，补齐客户数据隔离、手机号查询摘要和逻辑删除所需字段。
-- 说明：可重复执行；旧手机号密文仍保留，mobile_hash 主要用于新写入数据的不可逆查重。

delimiter //

drop procedure if exists add_column_if_missing//
create procedure add_column_if_missing(
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
          and column_name = p_column_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add column ', p_column_name, ' ', p_column_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

drop procedure if exists add_index_if_missing//
create procedure add_index_if_missing(
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

drop procedure if exists migrate_common_fields//
create procedure migrate_common_fields(in p_table_name varchar(128))
begin
    call add_column_if_missing(p_table_name, 'create_by', 'bigint null comment ''创建人ID''');
    call add_column_if_missing(p_table_name, 'update_by', 'bigint null comment ''更新人ID''');
    call add_column_if_missing(p_table_name, 'deleted', 'tinyint not null default 0 comment ''逻辑删除：0未删除，1已删除''');
    call add_column_if_missing(p_table_name, 'version', 'int not null default 0 comment ''乐观锁版本号''');
end//

delimiter ;

call migrate_common_fields('sys_user');
call migrate_common_fields('sys_role');
call migrate_common_fields('sys_menu');
call migrate_common_fields('sys_operation_log');
call migrate_common_fields('sys_uploaded_file');
call migrate_common_fields('logistics_customer');
call migrate_common_fields('logistics_order');
call migrate_common_fields('logistics_waybill');
call migrate_common_fields('logistics_dispatch');
call migrate_common_fields('logistics_task');
call migrate_common_fields('logistics_track');
call migrate_common_fields('logistics_exception');
call migrate_common_fields('logistics_fee');
call migrate_common_fields('logistics_driver');
call migrate_common_fields('logistics_vehicle');

call add_column_if_missing('sys_user', 'mobile_hash', 'varchar(128) null comment ''手机号不可逆查询摘要，用于查重''');
call add_index_if_missing('sys_user', 'idx_sys_user_mobile_hash', '(mobile_hash)');

call add_column_if_missing('logistics_order', 'customer_id', 'bigint null comment ''客户ID，用于客户角色数据隔离''');
call add_index_if_missing('logistics_order', 'idx_order_customer_time', '(customer_id, created_at)');

update logistics_order o
join logistics_customer c on c.customer_name = o.customer_name
set o.customer_id = c.id
where o.customer_id is null;

drop procedure if exists migrate_common_fields;
drop procedure if exists add_index_if_missing;
drop procedure if exists add_column_if_missing;
