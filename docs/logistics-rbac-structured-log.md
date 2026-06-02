# 物流系统权限、随机 ID 与操作审计说明

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
| admin | 963311213 | 系统管理员 |
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

## 操作审计

每次请求进入系统时会生成：

- `traceId`：请求链路 ID，也会写入响应头 `X-Trace-Id`
- `operationId`：单次操作唯一 ID，也会写入响应头 `X-Operation-Id`

业务写操作会同步写入 `sys_operation_log`，字段包括 `operation_id`、`trace_id`、`user_id`、`user_code`、`username`、`role_code`、`request_uri`、`request_method`、`operation_status`、`cost_ms`、`error_message`、`operation_time`。

`error_message` 字段在接口返回异常时自动写入异常原因（经过安全清洗，手机号/邮箱/身份证号已脱敏），方便在操作日志页面直接排障，无需额外查询日志文件。

### 日志安全加固

为防止日志文件泄露导致批量数据外泄，全链路日志均经过脱敏处理：

- **userId/operator**：`LogMaskUtils.maskId()` 分级脱敏，短 ID 保留可辨识信息，长 ID 保留后 4 位。
- **userCode**：同 userId 脱敏规则。
- **clientIp**：`LogMaskUtils.maskIp()` 保留前两段子网信息（如 `192.168.***.***`）。
- **username/realName**：`LogMaskUtils.maskAccount()/maskName()` 仅保留头尾各 1 字符。
- **changeSummary**：变更摘要中 ID/编号字段自动脱敏，姓名/手机号/邮箱/地址等只记录掩码值。
- **requestParams**：过滤 password/token/手机号/邮箱/身份证号/银行卡号等 14 类敏感参数。
- **errorMessage**：`sanitizeErrorMessage()` 对异常消息中的手机号、邮箱、身份证号做头尾保留脱敏（如 `138****5678`），兼顾隐私保护与排障定位。
- **MDC 日志**：userId/userCode 在写入 MDC 前脱敏，Filebeat → Kibana 链路仅含脱敏值。
- **数据库保留真值**：`sys_operation_log` 表中 userId/userCode 保留原始值用于后端 SQL 查询过滤，仅前/后端返回和日志文件层面脱敏。

### 集中日志（Filebeat + Kibana）

生产环境可接入 Filebeat → Elasticsearch → Kibana 链路实现集中日志：

- Filebeat 采集应用日志文件（含 JSON 格式的结构化 MDC 日志）
- 写入已有 Elasticsearch 索引 `logistics-logs-*`
- Kibana 提供全文搜索和可视化仪表盘

配置文件位于 `ops/filebeat.yml`，Docker Compose 编排已包含 Filebeat 和 Kibana 服务。

### 操作日志页面

菜单路径：`系统管理 → 操作日志`，前端路由 `/system/operation-logs`，权限码 `system:log:view`。

列表展示字段包括操作 ID、Trace ID、用户编号（脱敏展示）、操作人（脱敏展示）、操作内容、接口路径、耗时和操作状态。IP 地址脱敏为子网段展示。支持按关键词模糊查询和时间范围筛选。
