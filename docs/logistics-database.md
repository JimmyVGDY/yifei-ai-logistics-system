# 物流数据库说明

物流管理系统使用独立数据库：

```text
database: logistics_management
host: 127.0.0.1
port: 3306
username: root
password: 空（按实际配置）
```

## 核心表

### 物流业务表

- `logistics_customer`: 客户档案。
- `logistics_warehouse`: 仓库档案。
- `logistics_driver`: 司机档案。
- `logistics_vehicle`: 车辆档案。
- `logistics_route`: 运输路线。
- `logistics_order`: 物流订单主表。
- `logistics_waybill`: 运单表。
- `logistics_dispatch`: 调度表。
- `logistics_task`: 运输任务表。
- `logistics_track`: 物流轨迹表。
- `logistics_exception`: 运输异常表。
- `logistics_fee`: 费用结算表。
- `logistics_inventory`: 仓库库存表。

### 系统表

- `sys_user`: 系统用户（含 `password` 字段，支持 BCrypt 自动升级；`mobile` 存储密文，`mobile_hash` 用于手机号不可逆查重）。
- `sys_role`: 角色定义。
- `sys_menu`: 菜单/权限。
- `sys_user_role`: 用户-角色关联。
- `sys_role_menu`: 角色-菜单关联。
- `sys_operation_log`: 操作审计日志，含 `error_message` 字段用于排障。
- `sys_uploaded_file`: 上传文件记录。

### AI 长期记忆表

- `ai_conversation`: AI 会话主表，保存会话标题、状态、归档/删除时间、消息数量、最近消息时间和脱敏后的上下文快照。Redis 只做热缓存，历史会话以该表为准。
- `ai_conversation_message`: AI 会话消息表，保存脱敏后的用户消息、AI 回复、工具调用摘要、引用来源摘要，并记录 `traceId`、`operationId`、`loginSessionId`、`aiConversationId`、`aiMessageId`。
- `ai_user_profile`: 账号级 AI 画像，保存长期记忆开关、默认回答风格、常用模块、常用查询习惯、记忆数量和最近召回时间。
- `ai_user_memory`: 账号级长期记忆主表，保存记忆分类、置信度、脱敏标题、脱敏摘要、Qdrant 向量点 ID、创建来源会话和逻辑删除字段。
- `ai_memory_event`: 长期记忆审计事件表，记录创建、召回、跳过写入、删除、清空和设置变更，并保留 `traceId`、`operationId`、`loginSessionId`、`aiConversationId`。
- `ai_document_index`: RAG 文档索引状态表，记录系统文档路径、文件名、内容哈希、分块数量、索引状态、错误摘要和索引时间；不保存文档正文，Qdrant payload 也只保存脱敏摘要。
- `ai_query_cursor`: AI 查询结果游标表，保存当前用户当前会话最近一次只读查询的脱敏条件、分页位置、总数和已返回条数，用于“继续看”“查看剩余数据”“下一页”等多轮追问。

`20260622_incremental_ai_memory_governance.sql` 会继续扩展 AI 记忆表：

- `ai_user_memory.memory_scope/scope_value`：限定记忆只在全局、某模块或某业务场景内生效，避免“只查运输任务异常”污染所有异常查询。
- `ai_user_memory.conflict_group/superseded_by`：记录同类偏好冲突和替代关系，支持新偏好替换旧偏好。
- `ai_user_memory.evidence_count/negative_count/priority`：记录正向证据、负反馈和优先级，供定时任务晋升候选、降权或归档。
- `ai_user_memory.status=SUSPECTED_HALLUCINATION`：隔离模型推测出的疑似幻觉记忆，不参与召回，等待用户批准或拒绝。
- `ai_user_profile.profile_version/*_json/profile_confidence/compiled_at`：保存由有效记忆编译出来的结构化用户画像。
- `ai_memory_event.event_detail_json`：保存脱敏治理详情，用于审计记忆创建、召回、冲突、替代、归档、删除和画像编译。

### 增量字段说明

核心业务表和系统表通过增量脚本补齐通用审计字段：

- `create_by`、`update_by`：创建人和更新人 ID。
- `deleted`：逻辑删除标记，通用 CRUD 删除只允许写该字段，不再回退物理删除。
- `version`：乐观锁版本号，后续可用于并发更新控制。

`logistics_order.customer_id` 是客户账号数据隔离的关键字段。客户角色访问看板、运单、轨迹、异常、费用、订单详情和 ES 搜索时，都会按当前登录会话的 `customerId` 过滤；如果客户账号未绑定客户档案，后端会拒绝业务查询。

`sys_user.mobile_hash` 是手机号不可逆查询摘要，配合 `mobile` 密文字段使用：

- 新写入的手机号密文格式为 `ENCGCM:`，使用 AES-GCM 随机 IV。
- 旧数据的 `ENC:` 密文继续兼容解密和查重。
- 查重优先使用 `mobile_hash`，同时兼容旧明文和旧 `ENC:` 密文。

`sys_operation_log` 表在基础字段外，通过增量迁移脚本补齐：

- `operation_id`、`trace_id`：请求链路追踪
- `user_id`、`user_code`、`role_code`：操作人身份
- `cost_ms`：接口耗时
- `error_message`：异常信息（接口报错时自动记录异常原因）
- `client_ip`、`user_agent`：客户端来源和浏览器标识
- `request_params`：安全请求参数摘要，密码、token 等敏感参数不会记录
- `target_id`：操作对象 ID，例如记录 ID、角色 ID、订单号
- `change_summary`：脱敏后的操作前后变化摘要（使用 LogMaskUtils.maskId/maskName 等），ID/编号仅保留后 4 位，密码/token 不记录，姓名、手机号、邮箱、地址等敏感信息只记录掩码值
- `operation_source`、`executor_type`、`ai_conversation_id`、`ai_tool_name`、`ai_tool_target`、`ai_prompt_summary`、`ai_result_summary`：AI 分层审计字段，用于区分用户提问、AI 只读工具调用和 AI 生成回答
- `ai_memory_id`、`ai_memory_event_type`、`ai_memory_source`、`ai_memory_hit_count`、`ai_memory_trace_summary`：AI 长期记忆审计字段，用于区分记忆召回、写入、删除、清空和降级事件
- `create_by`、`update_by`、`deleted`、`version`：通用审计字段

## 模拟数据规模

初始化脚本将写入：客户 100 条、订单/运单/调度/任务/轨迹/费用各 100 条，适合本地接口联调和演示。

## 扩展模拟数据

```bash
mysql -uroot logistics_management < scripts/sql/seed-logistics-mock-100.sql
```

## 刷新可读演示名称

```bash
mysql -uroot logistics_management < scripts/sql/refresh-logistics-readable-names.sql
```

## 兼容旧库补丁

如果本地已经执行过旧增量脚本，可执行下面脚本补齐操作日志 `error_message` 字段，并清理已下线的结构化日志菜单：

```bash
mysql -uroot logistics_management < scripts/sql/20260531_cleanup_structured_log_and_operation_log.sql
```

如果需要增强操作日志审计上下文，可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260602_incremental_operation_log_context.sql
```

如果需要记录脱敏后的操作前后变化摘要，可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260602_incremental_operation_log_change_summary.sql
```

如果需要区分 AI 问答链路中的用户提问、AI 工具调用和 AI 生成回答，可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260605_incremental_ai_operation_audit.sql
```

如果需要启用 AI 账号级长期记忆和 Qdrant 向量召回审计，可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260605_incremental_ai_long_term_memory.sql
```

该脚本只新增 `ai_user_profile`、`ai_user_memory`、`ai_memory_event` 表，并补齐 `sys_operation_log` 的 AI 记忆审计字段；不会清空或重建现有物流业务表。

如果需要启用 AI 长期记忆生命周期管理（强化计数、最后强化时间、生命周期状态和状态索引），可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260610_incremental_ai_memory_lifecycle.sql
```

该脚本可重复执行。应用启动时如果检测到 `ai_user_memory.status`、`last_reinforced_at`、`reinforce_count` 尚未就绪，会跳过偏好挖掘和生命周期管理，不影响 AI 问答和物流主业务。

如果需要补齐 AI 助手菜单和最小必要权限，可执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260610_incremental_ai_menu_for_all_roles.sql
```

该脚本会给所有角色补齐 AI 助手入口和当前用户自己的基础 AI 权限；`ai:log:analyze` 仅默认授予管理员、审计员和财务经理，并会收回非审计类角色历史上被默认授予的日志分析权限，避免普通角色保留跨用户日志排障能力。

如果需要启用 AI 会话持久化、归档删除和会话级上下文快照，可执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260611_incremental_ai_conversation_persistence.sql
```

该脚本只新增 `ai_conversation`、`ai_conversation_message` 表，并补齐 `ai:conversation:archive`、`ai:conversation:delete` 权限；不会清空或重建现有业务表。执行后，AI 会话历史不再依赖 Redis 存活。

如果需要启用 AI 查询结果游标和多轮追问分页能力，可执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260613_incremental_ai_query_cursor.sql
```

该脚本只新增 `ai_query_cursor` 表并补充 `ai_conversation` 查询索引；不会清空或重建现有业务表。执行后，AI 默认仍只展示前 10 条数据，用户继续追问“继续看”“查看剩余数据”时会复用游标分页，而不是重新猜测上一轮查询条件。当前本地库已执行该脚本。

如果需要补齐客户数据隔离、手机号查重摘要和逻辑删除安全字段，可执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260603_incremental_security_hardening.sql
```

## 相关文档

- [项目文档索引](README.md)
- [数据库增量迁移说明](incremental-migration.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [认证接口文档](auth-api.md)

## AI Prompt 模板表

`ai_prompt_template` 用于保存 AI Prompt 模板和版本信息，是 AI 助手 Prompt 治理的主表。它保存模板编码、模板名称、版本号、模板类型、Mustache 模板内容、必填变量、可选变量、输出结构要求、模型用途和状态。应用启动不会自动重建该表，已有数据库请执行 `scripts/sql/20260621_incremental_ai_prompt_template.sql`。

`ai_token_usage` 已补充 `template_code`、`template_version`、`estimated_cost_currency` 和 `cached_tokens` 字段。`estimated_cost_currency` 区分 DeepSeek（CNY）和 OpenAI（USD）两种币种费用。`cached_tokens` 记录 API 返回的缓存命中数，用于按缓存折扣精确计算输入费用（DeepSeek 缓存命中 ¥0.02/M vs 未命中 ¥1.00/M）。字段不存在时应用会降级为原 token 记录方式，不影响 AI 主流程。

当前默认模板覆盖普通问答、临时 SQL 生成、自检、纠错、文件分析和长期记忆提取。模板缺失或渲染失败时会使用代码兜底模板，避免因为模板表问题导致 AI 页面不可用。配置说明见 [配置说明](configuration.md)，AI 设计说明见 [AI 助手设计文档](ai-assistant-design.md)。
