# 数据库增量迁移说明

本项目默认不会在 MySQL 启动时自动执行 `schema.sql` 或 `data.sql`，避免覆盖本地业务数据。

如需升级现有 `logistics_management` 数据库结构，请手动执行：

```sql
source scripts/sql/20260525_incremental_base_fields_and_indexes.sql;
```

该脚本会保留现有数据，并为系统表和物流表补充：

- `create_by`
- `update_by`
- `deleted`
- `version`
- `sys_user.user_code` 用户业务编号
- 状态、时间、手机号、订单号等常用查询索引

脚本使用 `information_schema` 判断字段和索引是否已存在，可重复执行。
