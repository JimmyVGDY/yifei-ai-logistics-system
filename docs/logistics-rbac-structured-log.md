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

系统会根据菜单权限自动派生按钮权限。示例：`order:manage` 会派生 `order:query`、`order:create`、`order:update`、`order:delete`、`order:export`、`order:import`；`track:view` 会派生 `track:query`、`track:export`。

## 密码安全

系统兼容旧的明文密码数据。用户使用明文密码登录成功后，后端会自动将 `sys_user.password` 升级为 BCrypt 密文；已经是 BCrypt 的密码会直接按 BCrypt 校验。

## 结构化日志

项目引入 `logstash-logback-encoder`，控制台保留文本日志，文件日志输出 JSON。日志字段包括 `timestamp`、`level`、`logger`、`traceId`、`userId`、`usernameMasked`、`roleCode`、`module`、`operation`、`costMs`、`result`。

### 日志查询中心

本地开发环境新增轻量版“结构化日志”页面：

- 菜单路径：`系统管理 -> 结构化日志`
- 前端路由：`/system/structured-logs`
- 后端接口：`GET /system/structured-logs`
- 权限码：`system:log:view`

后端会读取 `logging.file.name` 指向的 JSON 日志文件及同目录滚动日志，统一解析为结构化字段后返回分页数据。支持按日志级别、关键词、Trace ID、用户 ID、模块、操作、开始时间、结束时间查询。

每次请求进入系统时会生成：

- `traceId`：请求链路 ID，也会写入响应头 `X-Trace-Id`
- `operationId`：单次操作唯一 ID，也会写入响应头 `X-Operation-Id`

业务写操作会同步写入 `sys_operation_log`，字段包括 `operation_id`、`trace_id`、`user_id`、`username`、`role_code`、`request_uri`、`request_method`、`operation_status`、`cost_ms`、`operation_time`。如果本地数据库还没有新增字段，代码会回退写入旧版基础操作日志；建议执行增量 SQL 补齐字段和索引。

### 后续标准化方向

当前实现适合本地练习和单机排查。生产级结构化日志通常会接入专门的日志系统，例如：

- Filebeat / Logstash -> Elasticsearch / OpenSearch -> Kibana / OpenSearch Dashboards
- Promtail -> Loki -> Grafana

后续如果接入这些组件，前端“结构化日志”页面可以保留，后端查询实现从读取本地文件切换为查询外部日志系统。

用户 ID 原样记录，用户名和姓名使用脱敏后的值，便于排查问题时保留定位能力，同时避免直接暴露敏感信息。
