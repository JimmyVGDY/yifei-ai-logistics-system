-- 2026-06-10 为所有角色补充 AI 助手菜单入口
-- 修复：之前仅 ADMIN 角色能访问 /ai-assistant，因为 sys_menu 表缺少 AI 助手菜单记录
-- 说明：菜单 ID 使用 260610000300001，与现有 ID 前缀体系一致

-- 1. 插入 AI 助手菜单（如果不存在）
insert into sys_menu (id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select t.id, t.parent_id, t.menu_name, t.menu_path, t.permission_code, t.sort_no, 1, current_timestamp, current_timestamp
from (
    select 260610000300001 id, 0 parent_id, 'AI助手' menu_name, '/ai-assistant' menu_path, 'ai:chat' permission_code, 960 sort_no
) t
where not exists (select 1 from sys_menu existed where existed.menu_path = t.menu_path);

-- 2. 为所有角色初始化 AI 助手菜单关系
--    跳过已有该菜单关系的角色（如 ADMIN 可能已有）
insert ignore into sys_role_menu (id, role_id, menu_id)
select 260610000400000 + row_number() over (order by r.id),
       r.id,
       m.id
from sys_role r
cross join sys_menu m
where m.menu_path = '/ai-assistant'
  and not exists (select 1 from sys_role_menu existed where existed.role_id = r.id and existed.menu_id = m.id);

-- 3. 为 AI 助手补充权限定义（菜单级 PAGE 权限 + 按钮级 API 权限）
set @ai_menu_id := (select id from sys_menu where menu_path = '/ai-assistant' limit 1);

-- 3.1 AI 助手对话权限
insert ignore into sys_permission (
    id, permission_code, permission_name, permission_type,
    module_code, action_code, menu_id, sort_no, status, create_time, update_time
)
select 260610000500000 + t.seq, t.permission_code, t.permission_name, t.permission_type,
       t.module_code, t.action_code, @ai_menu_id, 96000 + t.seq, 1, current_timestamp, current_timestamp
from (
    select 1 seq, 'ai:chat' permission_code, 'AI助手-对话' permission_name, 'PAGE' permission_type, 'ai' module_code, 'chat' action_code union all
    select 2, 'ai:log:analyze', 'AI助手-日志分析', 'BUTTON', 'ai', 'log:analyze' union all
    select 3, 'ai:conversation:query', 'AI助手-会话查询', 'BUTTON', 'ai', 'conversation:query' union all
    select 4, 'ai:memory:query', 'AI助手-记忆查询', 'BUTTON', 'ai', 'memory:query' union all
    select 5, 'ai:memory:delete', 'AI助手-记忆删除', 'BUTTON', 'ai', 'memory:delete' union all
    select 6, 'ai:memory:settings', 'AI助手-记忆设置', 'BUTTON', 'ai', 'memory:settings'
) t
where not exists (select 1 from sys_permission existed where existed.permission_code = t.permission_code);

-- 4. 为所有角色关联 AI 助手权限（从 sys_role_menu 推导，保证一致性）
insert ignore into sys_role_permission (id, role_id, permission_id)
select 260610000600000 + row_number() over (order by rm.role_id, p.id) as id,
       rm.role_id,
       p.id
from sys_role_menu rm
join sys_permission p on p.menu_id = rm.menu_id
where rm.menu_id = @ai_menu_id
  and p.status = 1
  and not exists (select 1 from sys_role_permission existed where existed.role_id = rm.role_id and existed.permission_id = p.id);
