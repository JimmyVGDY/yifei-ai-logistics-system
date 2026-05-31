-- 物流管理系统增量迁移脚本
-- 用途：保留现有数据，为系统表和物流表补齐审计字段、逻辑删除字段、版本字段和常用索引。
-- 执行方式：连接 logistics_management 数据库后手动执行。本脚本不由 Spring Boot 启动自动执行。

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

drop procedure if exists modify_column_if_exists//
create procedure modify_column_if_exists(
    in p_table_name varchar(128),
    in p_column_name varchar(128),
    in p_column_definition varchar(512)
)
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = p_table_name
          and column_name = p_column_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' modify column ', p_column_name, ' ', p_column_definition);
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

call migrate_common_fields('demo_user');
call migrate_common_fields('sys_role');
call migrate_common_fields('sys_user');
call migrate_common_fields('sys_menu');
call migrate_common_fields('sys_user_role');
call migrate_common_fields('sys_role_menu');
call migrate_common_fields('sys_operation_log');
call migrate_common_fields('sys_uploaded_file');
call migrate_common_fields('logistics_customer');
call migrate_common_fields('logistics_warehouse');
call migrate_common_fields('logistics_driver');
call migrate_common_fields('logistics_vehicle');
call migrate_common_fields('logistics_route');
call migrate_common_fields('logistics_order');
call migrate_common_fields('logistics_waybill');
call migrate_common_fields('logistics_dispatch');
call migrate_common_fields('logistics_task');
call migrate_common_fields('logistics_track');
call migrate_common_fields('logistics_exception');
call migrate_common_fields('logistics_fee');
call migrate_common_fields('logistics_order_tracking');
call migrate_common_fields('logistics_inventory');
call migrate_common_fields('logistics_freight_bill');

call add_column_if_missing('sys_user', 'user_code', 'varchar(32) null comment ''用户业务编号''');
update sys_user set user_code = concat('U-', id) where user_code is null or user_code = '';

call add_column_if_missing('sys_operation_log', 'operation_id', 'varchar(32) null comment ''单次操作唯一ID''');
call add_column_if_missing('sys_operation_log', 'trace_id', 'varchar(64) null comment ''请求链路ID''');
call add_column_if_missing('sys_operation_log', 'user_id', 'varchar(32) null comment ''登录用户ID''');
call add_column_if_missing('sys_operation_log', 'user_code', 'varchar(32) null comment ''登录用户业务编号''');
call add_column_if_missing('sys_operation_log', 'role_code', 'varchar(64) null comment ''角色编码''');
call add_column_if_missing('sys_operation_log', 'cost_ms', 'bigint null comment ''接口耗时毫秒''');
call add_column_if_missing('sys_operation_log', 'error_message', 'text null comment ''异常信息，操作失败时记录异常原因便于排障''');

delete rm
from sys_role_menu rm
join sys_menu m on m.id = rm.menu_id
where m.menu_path = '/system/structured-logs';

delete from sys_menu
where menu_path = '/system/structured-logs';

call add_index_if_missing('sys_role', 'idx_sys_role_status_time', '(status, update_time)');
call add_index_if_missing('sys_user', 'idx_sys_user_status_time', '(status, update_time)');
call add_index_if_missing('sys_user', 'idx_sys_user_mobile', '(mobile)');
call add_index_if_missing('sys_user', 'idx_sys_user_code', '(user_code)');
call add_index_if_missing('logistics_customer', 'idx_customer_status_time', '(status, updated_at)');
call add_index_if_missing('logistics_customer', 'idx_customer_phone', '(contact_phone)');
call add_index_if_missing('logistics_order', 'idx_order_status_time', '(status, created_at)');
call add_index_if_missing('logistics_order', 'idx_order_customer_time', '(customer_id, created_at)');
call add_index_if_missing('logistics_order', 'idx_order_no_time', '(order_no, created_at)');
call add_index_if_missing('logistics_waybill', 'idx_waybill_status_time', '(transport_status, create_time)');
call add_index_if_missing('logistics_dispatch', 'idx_dispatch_status_time', '(dispatch_status, create_time)');
call add_index_if_missing('logistics_task', 'idx_task_status_time', '(task_status, create_time)');
call add_index_if_missing('logistics_driver', 'idx_driver_status_time', '(status, updated_at)');
call add_index_if_missing('logistics_driver', 'idx_driver_phone', '(phone)');
call add_index_if_missing('logistics_vehicle', 'idx_vehicle_status_time', '(status, updated_at)');
call add_index_if_missing('logistics_exception', 'idx_exception_status_time', '(exception_status, report_time)');
call add_index_if_missing('logistics_fee', 'idx_fee_status_time', '(payment_status, create_time)');
call add_index_if_missing('sys_operation_log', 'idx_operation_status_time', '(operation_status, operation_time)');
call add_index_if_missing('sys_operation_log', 'idx_operation_trace_id', '(trace_id)');
call add_index_if_missing('sys_operation_log', 'idx_operation_operation_id', '(operation_id)');
call add_index_if_missing('sys_operation_log', 'idx_operation_user_code', '(user_code)');

-- 运单允许先录入草稿性质的基础信息，非核心字段可暂缺；客户和收发地址仍由代码层保持必填。
call modify_column_if_exists('logistics_order', 'cargo_name', 'varchar(128) null comment ''货物名称，可后补''');
call modify_column_if_exists('logistics_order', 'cargo_weight', 'decimal(12, 3) null comment ''货物重量，可后补''');
call modify_column_if_exists('logistics_order', 'cargo_volume', 'decimal(12, 3) null default 0 comment ''货物体积，可后补''');
call modify_column_if_exists('logistics_order', 'planned_pickup_time', 'timestamp null comment ''计划提货时间''');
call modify_column_if_exists('logistics_order', 'planned_delivery_time', 'timestamp null comment ''计划送达时间''');
call modify_column_if_exists('logistics_order', 'route_id', 'bigint null comment ''线路ID，可后补''');
call modify_column_if_exists('logistics_order', 'warehouse_id', 'bigint null comment ''仓库ID，可后补''');
call modify_column_if_exists('logistics_order', 'vehicle_id', 'bigint null comment ''车辆ID，可后补''');
call modify_column_if_exists('logistics_order', 'driver_id', 'bigint null comment ''司机ID，可后补''');

drop procedure if exists migrate_common_fields;
drop procedure if exists modify_column_if_exists;
drop procedure if exists add_index_if_missing;
drop procedure if exists add_column_if_missing;
