# 数据库增量迁移说明

项目默认不会在 MySQL 启动时自动执行 `schema.sql` 或 `data.sql`，避免覆盖本地业务数据。

如果要升级现有 `logistics_management` 数据库结构，请按顺序手动执行：

```sql
source scripts/sql/20260525_incremental_base_fields_and_indexes.sql;
source scripts/sql/20260526_incremental_role_permission_menu.sql;
source scripts/sql/20260601_incremental_user_permission.sql;
source scripts/sql/20260601_incremental_enterprise_roles.sql;
```

这些脚本会保留现有数据，并补充：

- `create_by`
- `update_by`
- `deleted`
- `version`
- `sys_user.user_code` 用户业务编号
- 常用查询索引，例如状态、时间、手机号、订单号
- 运单可后补字段，例如货物名称、重量、体积、计划时间、路线、仓库、司机、车辆
- 系统管理下的“权限配置”菜单
- 企业级权限表：`sys_permission`、`sys_role_permission`、`sys_user_permission`
- 企业岗位角色：客服、订单运营、运营主管、车队管理员、司机、异常处理、财务主管、审计只读、资料管理员、客户账号

权限增量脚本会根据现有 `sys_menu` 和 `sys_role_menu` 推导默认权限数据，不会删除旧角色、旧用户或旧菜单。

`schema.sql` 和 `data.sql` 主要用于 H2 或全新开发库初始化；已有数据环境优先执行增量脚本。
