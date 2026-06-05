# 数据库增量迁移说明

项目默认不会在 MySQL 启动时自动执行 `schema.sql` 或 `data.sql`，避免覆盖本地业务数据。

如果要升级现有 `logistics_management` 数据库结构，请按顺序手动执行：

```sql
source scripts/sql/20260525_incremental_base_fields_and_indexes.sql;
source scripts/sql/20260526_incremental_role_permission_menu.sql;
source scripts/sql/20260601_incremental_user_permission.sql;
source scripts/sql/20260601_incremental_enterprise_roles.sql;
source scripts/sql/20260601_incremental_customer_account_binding.sql;
source scripts/sql/20260603_incremental_security_hardening.sql;
source scripts/sql/20260605_incremental_ai_operation_audit.sql;
source scripts/sql/20260605_incremental_ai_long_term_memory.sql;
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
- 安全加固字段：`sys_user.mobile_hash`、`logistics_order.customer_id` 以及核心表 `deleted/version`
- 存量订单客户归属回填：按 `logistics_order.customer_name = logistics_customer.customer_name` 补齐 `customer_id`
- AI 分层审计字段：区分用户提问、AI 只读工具调用、AI 回答生成
- AI 长期记忆表和审计字段：`ai_user_profile`、`ai_user_memory`、`ai_memory_event` 以及 `sys_operation_log.ai_memory_*`

权限增量脚本会根据现有 `sys_menu` 和 `sys_role_menu` 推导默认权限数据，不会删除旧角色、旧用户或旧菜单。

`schema.sql` 和 `data.sql` 主要用于 H2 或全新开发库初始化；已有数据环境优先执行增量脚本。

## 最近脚本说明

### `20260603_incremental_security_hardening.sql`

该脚本用于安全边界补齐，已经设计为可重复执行：

- 给 `sys_user` 增加 `mobile_hash`，用于手机号不可逆查重。
- 给 `logistics_order` 增加或确认 `customer_id`，用于客户角色数据隔离。
- 给核心业务表和系统表补齐 `create_by`、`update_by`、`deleted`、`version`。
- 按客户名称回填历史订单 `customer_id`，避免客户账号绑定后看不到存量订单。

执行后无需清库，也不会覆盖已有业务数据。

### `20260605_incremental_ai_operation_audit.sql`

该脚本用于补齐 AI 问答全链路审计字段：

- `operation_source`：区分用户提问、AI 工具调用和 AI 回答生成。
- `executor_type`：区分执行者是用户还是 AI。
- `ai_conversation_id`、`ai_message_id`、`ai_tool_name`、`ai_tool_target`、`ai_readonly`、`ai_prompt_summary`、`ai_result_summary`：记录 AI 会话、只读工具和脱敏摘要。

### `20260605_incremental_ai_long_term_memory.sql`

该脚本用于启用账号级长期记忆：

- 新增 `ai_user_profile` 保存记忆开关、回答风格和常用习惯。
- 新增 `ai_user_memory` 保存脱敏长期记忆元数据和 Qdrant 向量点 ID。
- 新增 `ai_memory_event` 保存记忆召回、写入、删除、清空、跳过和降级事件。
- 补齐 `sys_operation_log.ai_memory_*` 字段，方便在操作日志页面串联 AI 问答和长期记忆链路。

该脚本可重复执行，不清库、不重建现有业务表。Qdrant 只作为向量召回服务；MySQL 表是长期记忆的审计真值。

## 相关文档

- [项目文档索引](README.md)
- [物流数据库说明](logistics-database.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [配置说明](configuration.md)
- [Spring AI 接入说明](spring-ai.md)
