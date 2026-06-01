-- 2026-06-01 物流系统企业岗位角色增量脚本
-- 目标：在保留已有角色和用户的前提下，补齐更贴近物流业务分工的默认角色。

insert into sys_role (id, role_code, role_name, status, create_time, update_time)
select t.id, t.role_code, t.role_name, 1, current_timestamp, current_timestamp
from (
    select 260601000500001 id, 'CUSTOMER_SERVICE' role_code, '客服专员' role_name union all
    select 260601000500002, 'ORDER_OPERATOR', '订单运营专员' union all
    select 260601000500003, 'OPERATIONS_MANAGER', '运营主管' union all
    select 260601000500004, 'FLEET_MANAGER', '车队管理员' union all
    select 260601000500005, 'DRIVER', '司机' union all
    select 260601000500006, 'EXCEPTION_HANDLER', '异常处理专员' union all
    select 260601000500007, 'FINANCE_MANAGER', '财务主管' union all
    select 260601000500008, 'AUDITOR', '审计只读员' union all
    select 260601000500009, 'FILE_MANAGER', '资料管理员' union all
    select 260601000500010, 'CUSTOMER', '客户账号'
) t
where not exists (select 1 from sys_role existed where existed.role_code = t.role_code);

-- 给没有菜单关系的新角色初始化可见页面。
insert ignore into sys_role_menu (id, role_id, menu_id)
select 260601000510000 + row_number() over (order by r.id, m.sort_no, m.id),
       r.id,
       m.id
from sys_role r
join sys_menu m on (
    (r.role_code = 'CUSTOMER_SERVICE' and m.menu_path in ('/customers', '/orders', '/waybills', '/tracks'))
    or (r.role_code = 'ORDER_OPERATOR' and m.menu_path in ('/orders', '/customers', '/waybills', '/tracks'))
    or (r.role_code = 'OPERATIONS_MANAGER' and m.menu_path in ('/dashboard', '/orders', '/waybills', '/dispatches', '/tasks', '/tracks', '/exceptions'))
    or (r.role_code = 'FLEET_MANAGER' and m.menu_path in ('/drivers', '/vehicles', '/dispatches', '/tasks', '/tracks'))
    or (r.role_code = 'DRIVER' and m.menu_path in ('/tasks', '/tracks', '/exceptions'))
    or (r.role_code = 'EXCEPTION_HANDLER' and m.menu_path in ('/exceptions', '/orders', '/tasks', '/tracks'))
    or (r.role_code = 'FINANCE_MANAGER' and m.menu_path in ('/fees', '/dashboard', '/system/operation-logs'))
    or (r.role_code = 'AUDITOR' and m.menu_path in ('/dashboard', '/orders', '/waybills', '/tracks', '/fees', '/system/operation-logs'))
    or (r.role_code = 'FILE_MANAGER' and m.menu_path in ('/files', '/resources'))
    or (r.role_code = 'CUSTOMER' and m.menu_path in ('/orders', '/tracks'))
)
where not exists (select 1 from sys_role_menu existed where existed.role_id = r.id);

-- 角色权限由菜单权限推导；普通业务角色按菜单拿基础权限，审计员只拿查询/导出类权限。
insert ignore into sys_role_permission (id, role_id, permission_id)
select 260601000520000 + row_number() over (order by rm.role_id, p.id),
       rm.role_id,
       p.id
from sys_role_menu rm
join sys_role r on r.id = rm.role_id
join sys_permission p on p.menu_id = rm.menu_id
where p.status = 1
  and (
      r.role_code <> 'AUDITOR'
      or p.action_code in ('view', 'query', 'export')
  );
