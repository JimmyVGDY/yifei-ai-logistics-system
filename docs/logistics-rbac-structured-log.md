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
| admin | 以 `APP_ADMIN_PASSWORD` 配置为准 | 系统管理员 |
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

### 客户数据隔离

客户角色不是只靠前端隐藏菜单，而是在后端强制过滤数据：

- 通用模块列表、运营看板、订单详情、近期订单和 ES 订单搜索都会读取 Sa-Token 会话中的 `customerId`。
- `logistics_order.customer_id` 是隔离主字段，运单、调度、任务、轨迹、异常、费用等关联表通过订单归属继续过滤。
- 客户账号未绑定客户档案时，后端会拒绝查询业务数据，避免空 `customerId` 退化成全量查询。
- 新增客户账号时会尽量按客户名称匹配历史订单并回填 `customer_id`，详见 [物流数据库说明](logistics-database.md) 和 [数据库增量迁移说明](incremental-migration.md)。

### 辅助接口权限

练习和中间件示例接口也纳入权限控制：

- `/demo-users/**`：查询走 `system:user:query`，新增走 `system:user:create`。
- `/bloom-filter/**`：走 `resource:view`。
- `/rabbitmq/**`：走 `resource:view`。

这些接口仍可用于练习和联调，但不再是“只要登录就能访问”。

## 密码安全

系统兼容旧的明文密码数据。用户使用明文密码登录成功后，后端会自动将 `sys_user.password` 升级为 BCrypt 密文；已经是 BCrypt 的密码会直接按 BCrypt 校验。

通用用户管理新增或编辑密码时也会自动写入 BCrypt；空密码不会覆盖原密码。

## 敏感字段存储

手机号等敏感字段入库前会加密：

- 新数据使用 `ENCGCM:` AES-GCM 密文，随机 IV，避免同一手机号每次加密结果相同。
- 旧数据 `ENC:` 密文继续兼容解密。
- `sys_user.mobile_hash` 保存不可逆查询摘要，用于手机号查重和个人客户账号唯一性校验。
- 生产环境应配置 `APP_ENCRYPT_KEY`，并由 `app.encrypt.require-key=true` 强制校验。

## 操作审计

每次请求进入系统时会生成：

- `traceId`：请求链路 ID，也会写入响应头 `X-Trace-Id`
- `operationId`：单次操作唯一 ID，也会写入响应头 `X-Operation-Id`

业务写操作会同步写入 `sys_operation_log`，字段包括 `operation_id`、`trace_id`、`user_id`、`user_code`、`username`、`role_code`、`request_uri`、`request_method`、`operation_status`、`cost_ms`、`error_message`、`operation_time`。

`error_message` 字段在接口返回异常时自动写入异常原因（经过安全清洗，手机号/邮箱/身份证号已脱敏），方便在操作日志页面直接排障，无需额外查询日志文件。

### AI 分层审计

AI 助手会额外写入分层审计日志，用于区分普通页面操作和 AI 链路内的动作：

| 操作来源 | 执行者 | 说明 |
| --- | --- | --- |
| `USER_TO_AI` | `USER` | 用户向 AI 助手发起提问 |
| `AI_TOOL` | `AI` | AI 为回答问题调用只读工具，例如业务数据查询、全局只读查找、日志排障 |
| `AI_RESPONSE` | `AI` | AI 汇总引用来源和工具结果后生成回答 |

新增字段包括 `operation_source`、`executor_type`、`ai_conversation_id`、`ai_message_id`、`ai_tool_name`、`ai_tool_target`、`ai_readonly`、`ai_prompt_summary`、`ai_result_summary`。这些字段只记录脱敏摘要，不记录完整提示词、密码、token、手机号、邮箱或详细地址。

AI 长期记忆会继续补充记忆审计字段：

| 字段 | 说明 |
| --- | --- |
| `ai_memory_id` | 本次召回、写入或删除关联的长期记忆 ID；批量或空召回时为空 |
| `ai_memory_event_type` | 记忆事件类型，例如 `RECALL`、`CREATE`、`DELETE`、`CLEAR`、`SKIP_SENSITIVE` |
| `ai_memory_source` | 事件来源，例如 `AI_MEMORY` 或 `USER` |
| `ai_memory_hit_count` | 本次召回命中的长期记忆数量 |
| `ai_memory_trace_summary` | 脱敏后的记忆链路摘要，不展示向量原文和敏感内容 |

记忆事件同时写入 `ai_memory_event`，并保留 `traceId`、`operationId`、`loginSessionId`、`aiConversationId`。管理员排查 AI 行为时，可先在操作日志按 `traceId` 或 `aiConversationId` 找到一次问答，再查看记忆召回、只读工具调用和回答生成是否都在同一链路内。

已有数据库请执行增量脚本：

```sql
source scripts/sql/20260605_incremental_ai_operation_audit.sql;
```

如果需要启用长期记忆审计字段，请继续执行：

```sql
source scripts/sql/20260605_incremental_ai_long_term_memory.sql;
```

前端“系统管理 → 操作日志”会把 `operation_source` 和 `executor_type` 展示为中文，例如“用户询问AI”“AI调用工具”“AI生成回答”，方便排查一次 AI 会话中到底是用户主动操作，还是 AI 为回答问题触发了只读查询。

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

## 相关文档

- [项目文档索引](README.md)
- [链路追踪与会话审计标识说明](trace-context-audit.md)
- [权限配置接口说明](role-permission-api.md)
- [物流数据库说明](logistics-database.md)
- [配置说明](configuration.md)
