# 物流系统权限、随机 ID 与结构化日志说明

## 数据库升级说明

当前默认配置不会在 MySQL 启动时自动执行 `schema.sql` 或 `data.sql`，避免覆盖本地已有业务数据。

现有数据库升级采用手动增量迁移脚本：

```sql
source scripts/sql/20260525_incremental_base_fields_and_indexes.sql;
```

该脚本会保留旧数据，并补齐 `create_by`、`update_by`、`deleted`、`version`、用户业务编号和常用索引。`schema.sql`、`data.sql` 仍可用于全新 H2 或本地重建验证场景。

## 默认账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | xlh963311213 | 系统管理员 |
| service | 123456 | 客服专员 |
| dispatcher | 123456 | 调度专员 |
| driver | 123456 | 司机 |
| finance | 123456 | 财务专员 |
| customer | 123456 | 客户 |

## 菜单权限

登录后后端会从 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu` 读取角色和菜单，前端不再写死菜单。接口侧通过 Sa-Token 校验权限码，例如：

- `order:manage`：运单管理。
- `customer:manage`：客户管理。
- `dispatch:manage`：调度管理。
- `task:manage`：运输任务。
- `fee:manage`：费用结算。
- `system:user:manage`：用户管理。

前端页面也会基于登录响应中的 `permissions` 控制按钮显示，例如新增、编辑、删除、导入、导出、异常处理和费用收款。

## 密码安全

系统兼容旧的明文密码数据。用户使用明文密码登录成功后，后端会自动将 `sys_user.password` 升级为 BCrypt 密文；已经是 BCrypt 的密码会直接按 BCrypt 校验。

## 结构化日志

项目引入 `logstash-logback-encoder`，控制台保留文本日志，文件日志输出 JSON。日志字段包括 `timestamp`、`level`、`logger`、`traceId`、`userId`、`usernameMasked`、`roleCode`、`module`、`operation`、`costMs`、`result`。

用户 ID 原样记录，用户名和姓名使用脱敏后的值，便于排查问题时保留定位能力，同时避免直接暴露敏感信息。
