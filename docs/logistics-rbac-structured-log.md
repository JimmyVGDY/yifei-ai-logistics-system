# 物流系统权限、随机 ID 与结构化日志说明

## 数据库重建说明

当前 `schema.sql` 已将主键 `id` 从自增改为显式 `BIGINT`，初始化数据由 `data.sql` 写入固定的 15 位短 ID。该方案适合本地开发环境重建数据库使用，会清空旧自增 ID 数据后重新初始化物流业务数据。

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

## 结构化日志

项目引入 `logstash-logback-encoder`，控制台保留文本日志，文件日志输出 JSON。日志字段包括 `timestamp`、`level`、`logger`、`traceId`、`userId`、`usernameMasked`、`roleCode`、`module`、`operation`、`costMs`、`result`。

用户 ID 原样记录，用户名和姓名使用脱敏后的值，便于排查问题时保留定位能力，同时避免直接暴露敏感信息。
