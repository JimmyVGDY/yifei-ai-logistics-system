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
source scripts/sql/20260610_incremental_ai_memory_lifecycle.sql;
source scripts/sql/20260610_incremental_ai_menu_for_all_roles.sql;
source scripts/sql/20260611_incremental_ai_conversation_persistence.sql;
source scripts/sql/20260611_incremental_add_deleted_version.sql;
source scripts/sql/20260611_incremental_operation_log_archive.sql;
source scripts/sql/20260611_incremental_ai_token_usage.sql;
source scripts/sql/20260612_incremental_ai_document_index.sql;
source scripts/sql/20260613_incremental_ai_query_cursor.sql;
source scripts/sql/20260619_incremental_permission_sensitive_flag.sql;
source scripts/sql/20260621_incremental_ai_prompt_template.sql;
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
- AI 会话持久化表：`ai_conversation`、`ai_conversation_message`，服务重启后仍可回显历史会话
- AI RAG 文档索引状态表：`ai_document_index`，用于按内容哈希跳过未变化文档，并记录索引失败原因
- AI 查询结果游标表：`ai_query_cursor`，用于“继续看”“查看剩余数据”“下一页”等多轮追问分页
- 权限目录敏感列标记：`sys_permission.sensitive_flag`，用于区分普通列和敏感列，避免只读角色自动获得手机号、金额、异常详情等敏感字段

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

### `20260610_incremental_ai_memory_lifecycle.sql`

该脚本用于补齐 AI 长期记忆生命周期字段：

- `reinforce_count`：记忆被再次命中的强化次数。
- `last_reinforced_at`：最后强化时间。
- `status`：生命周期状态，例如 `ACTIVE`、`WEAKENING`、`ARCHIVED`。
- `idx_ai_memory_status`：生命周期定时任务使用的状态索引。

该脚本可重复执行，不再依赖 `mysql -f` 跳过重复字段错误。旧库未执行该脚本时，应用会跳过偏好挖掘生命周期任务，不影响主业务。

### `20260610_incremental_ai_menu_for_all_roles.sql`

该脚本用于补齐 AI 助手菜单和最小必要权限：

- 所有角色默认获得 AI 助手入口、普通问答、当前用户会话和当前用户长期记忆管理权限。
- `ai:log:analyze` 属于跨用户审计权限，只默认授予 `ADMIN`、`AUDITOR`、`FINANCE_MANAGER`。
- 脚本使用 `insert ignore` 和 `not exists` 补齐缺失数据，可重复执行；同时会收回非审计类角色历史上被默认授予的 `ai:log:analyze`，避免普通角色保留跨用户日志排障能力。

### `20260611_incremental_ai_conversation_persistence.sql`

该脚本用于把 AI 会话历史从 Redis 短期缓存升级为 MySQL 持久化主存储：

- 新增 `ai_conversation` 保存会话标题、状态、归档/删除时间、最近消息时间、消息数量和上下文摘要。
- 新增 `ai_conversation_message` 保存脱敏后的用户消息、AI 回复、工具调用摘要、引用来源摘要和链路审计标识。
- 补齐 `ai:conversation:archive`、`ai:conversation:delete` 权限，并授予已有 AI 助手可用角色。
- Redis 继续作为最近上下文热缓存，不再作为会话历史的唯一来源。

该脚本可重复执行，不清库、不重建现有表。执行后可通过 [Spring AI 接入说明](spring-ai.md) 和 [链路追踪与会话审计标识说明](trace-context-audit.md) 查看会话持久化和审计链路。
### `20260612_incremental_ai_document_index.sql`

该脚本用于启用 RAG 文档增量索引状态记录：

- 新增 `ai_document_index` 保存文档路径、文件名、内容哈希、分块数量、索引状态、错误摘要和索引时间。
- 文档未变化时应用启动索引会直接跳过，避免重复写入 Qdrant。
- 文档变化时会按 `sourcePath` 清理旧向量，再写入新分块。
- Qdrant 不可用或单文件索引失败时会记录失败状态，不影响应用启动和物流主业务。

该脚本可重复执行，不清库、不重建现有表。RAG 配置和安全边界见 [Spring AI 接入说明](spring-ai.md)。

### `20260613_incremental_ai_query_cursor.sql`

该脚本用于启用 AI 查询结果游标：

- 新增 `ai_query_cursor` 保存当前用户当前会话的最近只读查询状态，包括模块、关键词、时间范围、状态、页码、总数和已返回条数。
- 补充 `ai_conversation` 的会话状态和最近消息时间索引，方便 AI 页面加载会话列表。
- 游标默认只保存脱敏查询状态，不保存敏感原文；过期时间由 `APP_AI_QUERY_CURSOR_TTL_MINUTES` 控制。
- 用户追问“继续看”“查看剩余28条”“下一页”时优先复用游标，不再依赖模型猜测上一轮上下文。

该脚本可重复执行，不清库、不重建现有表。AI 查询链路和前端展示策略见 [Spring AI 接入说明](spring-ai.md)。

### `20260611_incremental_add_deleted_version.sql`

该脚本用于为前三批迁移未覆盖的表补齐 `deleted` 和 `version` 字段：

- 补齐 `sys_permission`、`sys_role_permission`、`sys_user_permission` 的 `deleted/version` 字段。
- 补齐 `sys_login_history` 的 `deleted/version` 字段。
- 为上述表的 `deleted` 列添加索引，提升逻辑删除查询性能。

该脚本可重复执行，使用存储过程自动跳过已存在的列和索引。

### `20260611_incremental_operation_log_archive.sql`

该脚本用于创建操作日志归档表 `sys_operation_log_archive` 和归档存储过程：

- `sys_operation_log_archive`：与主表结构一致，额外增加 `archived_at` 字段记录归档时间。
- `archive_operation_logs`：按保留天数分批迁移（每批 5000 条），避免长事务锁表。
- 通过 XXL-Job 处理器 `operationLogArchive` 调用，建议每月执行一次。
- 保留天数通过 `APP_OPERATION_LOG_RETENTION_DAYS` 环境变量配置（默认 180 天）。

该脚本可重复执行，归档表不存在时自动创建。

### `20260611_incremental_ai_token_usage.sql`

该脚本用于创建 AI Token 用量追踪表：

- `ai_token_usage`：记录每次模型调用的 Token 消耗（prompt/completion/total）和预估费用。
- 支持按模型、用途（chat/sql_generate/memory_extract 等）、用户、时间维度汇总查询。
- 模型网关 `AiModelGateway` 自动记录每次调用，写入失败不影响主业务。

该脚本可重复执行。

## 相关文档

- [项目文档索引](README.md)
- [物流数据库说明](logistics-database.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [配置说明](configuration.md)
- [Spring AI 接入说明](spring-ai.md)

### `20260621_incremental_ai_prompt_template.sql`

该脚本用于启用 AI Prompt 模板治理和模板级 token 追踪：

- 新增 `ai_prompt_template` 表，保存模板编码、版本、模板类型、Mustache 模板内容、变量声明、输出结构和启停状态。
- 初始化 `AI_CHAT_SYSTEM`、`AI_CHAT_USER`、`AI_SQL_GENERATE_*`、`AI_SQL_SELF_CHECK_*`、`AI_SQL_REPAIR_*`、`AI_FILE_ANALYSIS_*`、`AI_MEMORY_EXTRACT_*` 默认模板。
- 补齐 `ai_token_usage.template_code` 和 `ai_token_usage.template_version` 字段，方便按模板版本排查模型调用和 token 消耗。
- 脚本只在模板编码不存在时插入默认模板，不覆盖已经人工调整过的模板内容。

该脚本可重复执行，不清库、不重建旧表。Prompt 模板运行逻辑和输出校验链路见 [Spring AI 接入说明](spring-ai.md) 和 [AI 助手设计文档](ai-assistant-design.md)。