-- 2026-06-01 权限配置企业级细化增量脚本
-- 目标：保留现有数据，补齐权限定义、角色权限、用户特殊权限三张表。

create table if not exists sys_permission (
    id bigint primary key,
    permission_code varchar(128) not null unique,
    permission_name varchar(128) not null,
    permission_type varchar(32) not null,
    module_code varchar(64) not null,
    action_code varchar(64) not null,
    menu_id bigint null,
    sort_no int not null,
    status tinyint not null default 1,
    create_time timestamp not null default current_timestamp,
    update_time timestamp not null default current_timestamp,
    index idx_sys_permission_menu (menu_id),
    index idx_sys_permission_module (module_code, action_code),
    index idx_sys_permission_status (status)
);

create table if not exists sys_role_permission (
    id bigint primary key,
    role_id bigint not null,
    permission_id bigint not null,
    unique key uk_sys_role_permission (role_id, permission_id),
    index idx_sys_role_permission_role (role_id)
);

create table if not exists sys_user_permission (
    id bigint primary key,
    user_id bigint not null,
    permission_id bigint not null,
    grant_type varchar(16) not null,
    create_time timestamp not null default current_timestamp,
    update_time timestamp not null default current_timestamp,
    unique key uk_sys_user_permission (user_id, permission_id),
    index idx_sys_user_permission_user (user_id),
    index idx_sys_user_permission_grant (grant_type)
);

-- 旧库可能只有少量演示菜单，先补齐物流后台标准菜单。
insert into sys_menu (id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select t.id, t.parent_id, t.menu_name, t.menu_path, t.permission_code, t.sort_no, 1, current_timestamp, current_timestamp
from (
    select 260601000300001 id, 0 parent_id, '运营看板' menu_name, '/dashboard' menu_path, 'dashboard:view' permission_code, 10 sort_no union all
    select 260601000300002, 0, '运单管理', '/orders', 'order:manage', 20 union all
    select 260601000300003, 0, '客户管理', '/customers', 'customer:manage', 30 union all
    select 260601000300004, 0, '运单中心', '/waybills', 'waybill:manage', 40 union all
    select 260601000300005, 0, '调度管理', '/dispatches', 'dispatch:manage', 50 union all
    select 260601000300006, 0, '运输任务', '/tasks', 'task:manage', 60 union all
    select 260601000300007, 0, '物流轨迹', '/tracks', 'track:view', 70 union all
    select 260601000300008, 0, '司机管理', '/drivers', 'driver:manage', 80 union all
    select 260601000300009, 0, '车辆管理', '/vehicles', 'vehicle:manage', 90 union all
    select 260601000300010, 0, '异常管理', '/exceptions', 'exception:manage', 100 union all
    select 260601000300011, 0, '费用结算', '/fees', 'fee:manage', 110 union all
    select 260601000300012, 0, '系统管理', '/system', 'system:manage', 900 union all
    select 260601000300016, 0, '上传文件', '/files', 'file:manage', 940 union all
    select 260601000300017, 0, '资源中心', '/resources', 'resource:view', 950
) t
where not exists (select 1 from sys_menu existed where existed.menu_path = t.menu_path);

set @system_menu_id := (select id from sys_menu where menu_path = '/system' order by id limit 1);

insert into sys_menu (id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select t.id, @system_menu_id, t.menu_name, t.menu_path, t.permission_code, t.sort_no, 1, current_timestamp, current_timestamp
from (
    select 260601000300013 id, '用户管理' menu_name, '/system/users' menu_path, 'system:user:manage' permission_code, 910 sort_no union all
    select 260601000300014, '角色管理', '/system/roles', 'system:role:manage', 920 union all
    select 260601000300015, '权限配置', '/system/permissions', 'system:permission:manage', 925 union all
    select 260601000300018, '操作日志', '/system/operation-logs', 'system:log:view', 930
) t
where not exists (select 1 from sys_menu existed where existed.menu_path = t.menu_path);

-- 如果角色还没有菜单关系，则按物流职责初始化一份基础菜单。
insert ignore into sys_role_menu (id, role_id, menu_id)
select 260601000400000 + row_number() over (order by r.id, m.sort_no, m.id),
       r.id,
       m.id
from sys_role r
join sys_menu m on (
    r.role_code = 'ADMIN'
    or (r.role_code = 'CUSTOMER_SERVICE' and m.menu_path in ('/customers', '/orders', '/waybills', '/tracks'))
    or (r.role_code = 'DISPATCHER' and m.menu_path in ('/dispatches', '/tasks', '/drivers', '/vehicles', '/tracks', '/exceptions'))
    or (r.role_code = 'DRIVER' and m.menu_path in ('/tasks', '/tracks', '/exceptions'))
    or (r.role_code = 'FINANCE' and m.menu_path in ('/fees', '/dashboard'))
    or (r.role_code = 'CUSTOMER' and m.menu_path in ('/orders', '/tracks'))
)
where not exists (select 1 from sys_role_menu existed where existed.role_id = r.id);

-- 菜单自身权限：控制页面是否可见。
insert ignore into sys_permission (
    id, permission_code, permission_name, permission_type,
    module_code, action_code, menu_id, sort_no, status, create_time, update_time
)
select 260601000000000 + row_number() over (order by m.sort_no, m.id) as id,
       m.permission_code,
       m.menu_name,
       'PAGE',
       substring(m.permission_code, 1, length(m.permission_code) - locate(':', reverse(m.permission_code))),
       substring_index(m.permission_code, ':', -1),
       m.id,
       m.sort_no * 100,
       1,
       current_timestamp,
       current_timestamp
from sys_menu m
where m.status = 1
  and m.permission_code is not null
  and m.permission_code <> '';

-- 按钮和接口权限：控制查询、新增、编辑、删除、导入、导出等细粒度操作。
insert ignore into sys_permission (
    id, permission_code, permission_name, permission_type,
    module_code, action_code, menu_id, sort_no, status, create_time, update_time
)
select 260601000100000 + row_number() over (order by m.sort_no, m.id, a.action_sort) as id,
       concat(substring(m.permission_code, 1, length(m.permission_code) - locate(':', reverse(m.permission_code))), ':', a.action_code),
       concat(m.menu_name, '-', a.action_name),
       'BUTTON',
       substring(m.permission_code, 1, length(m.permission_code) - locate(':', reverse(m.permission_code))),
       a.action_code,
       m.id,
       m.sort_no * 100 + a.action_sort,
       1,
       current_timestamp,
       current_timestamp
from sys_menu m
join (
    select 'query' action_code, '查询' action_name, 1 action_sort, 'manage' source_action union all
    select 'create', '新增', 2, 'manage' union all
    select 'update', '编辑', 3, 'manage' union all
    select 'delete', '删除', 4, 'manage' union all
    select 'import', '导入', 5, 'manage' union all
    select 'export', '导出', 6, 'manage' union all
    select 'query', '查询', 1, 'view' union all
    select 'export', '导出', 6, 'view'
) a on a.source_action = substring_index(m.permission_code, ':', -1)
where m.status = 1
  and m.permission_code is not null
  and m.permission_code <> '';

-- 角色基础权限由现有角色菜单关系推导，避免改变当前可见菜单结果。
insert ignore into sys_role_permission (id, role_id, permission_id)
select 260601000200000 + row_number() over (order by rm.role_id, p.id) as id,
       rm.role_id,
       p.id
from sys_role_menu rm
join sys_permission p on p.menu_id = rm.menu_id
where p.status = 1;
