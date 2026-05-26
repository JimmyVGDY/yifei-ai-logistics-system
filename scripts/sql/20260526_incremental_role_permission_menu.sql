-- 增量补齐角色权限配置菜单，保留现有数据，可重复执行。

set @system_menu_id := (
    select id from sys_menu where menu_path = '/system' order by id limit 1
);

insert into sys_menu (id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select 260526090000001, coalesce(@system_menu_id, 0), '权限配置', '/system/permissions', 'system:permission:manage', 925, 1, current_timestamp, current_timestamp
where not exists (
    select 1 from sys_menu where menu_path = '/system/permissions'
);

set @permission_menu_id := (
    select id from sys_menu where menu_path = '/system/permissions' order by id limit 1
);

insert into sys_role_menu (id, role_id, menu_id)
select 260526090000002, r.id, @permission_menu_id
from sys_role r
where r.role_code = 'ADMIN'
  and @permission_menu_id is not null
  and not exists (
      select 1 from sys_role_menu rm where rm.role_id = r.id and rm.menu_id = @permission_menu_id
  );
