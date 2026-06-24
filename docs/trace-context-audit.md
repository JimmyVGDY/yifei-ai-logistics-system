# 链路追踪与会话审计标识说明

## 标识分层

- `traceId`：一次 HTTP 请求或一次业务链路的追踪 ID。前端传入 `X-Trace-Id` 时后端沿用，否则后端生成，并通过响应头 `X-Trace-Id` 返回。
- `operationId`：一次可审计操作的唯一 ID。前端传入 `X-Operation-Id` 时后端沿用，否则后端生成，并通过响应头 `X-Operation-Id` 返回。
- `loginSessionId`：一次登录会话的审计 ID。登录成功时生成并写入 Sa-Token Session，用于串联同一账号从登录到退出或会话失效前的全部操作。

## 数据库字段

`sys_operation_log` 新增 `login_session_id` 字段，用于保存登录会话审计 ID。已有数据库请手动执行：

```sql
source scripts/sql/20260602_incremental_operation_log_login_session.sql;
```

该脚本可重复执行，不清库、不重建表。

## 日志与异步链路

- 结构化 JSON 日志通过 MDC 输出 `traceId`、`operationId`、`loginSessionId`、`userId`、`userCode`、`roleCode` 等字段。
- 订单 RabbitMQ 事件会携带 `traceId`、`operationId`、`loginSessionId`、`userId`、`userCode`、`usernameMasked`、`roleCode`。
- 消费端恢复消息中的 MDC 上下文后再执行业务，处理完成后清理 MDC，避免上下文污染下一条消息。
- AI SSE 流式问答（`POST /ai/chat/stream`）运行在 Spring MVC 异步线程中，不能直接依赖 Sa-Token 和 MDC 的请求线程 `ThreadLocal`。Controller 会预捕获 `loginId`、权限列表、角色、客户范围、用户编号、`usernameMasked` 和 `loginSessionId`，通过 `SseChatContext` 传递给异步执行线程，并在 `finally` 块中清理。下游只读工具、临时 SQL 校验、长期记忆和 AI 审计日志必须优先读取这份快照，而非直接调用 `StpUtil`。Python AI 服务通过 HTTP Header 接收用户上下文，不直接访问 Sa-Token。
- XXL-Job 每次任务执行生成独立 `jobRunId`，同时设置 `traceId`，并使用 `operationId=jobRunId`，`userId/userCode=system`，`roleCode=SYSTEM`。

## 查询建议

- 查一次点击或接口问题：优先用 `operationId` 精确定位。
- 查一次业务链路：用 `traceId` 关联接口日志、Redis、ES、RabbitMQ 发布和消费日志。
- 查一次登录期间全部行为：用 `loginSessionId` 查询操作日志。
- 查某个用户的历史行为：用 `userId` 或 `userCode` 跨会话查询。
- 查一次 AI 问答全链路：用 `aiConversationId` 关联用户提问、长期记忆召回、只读工具调用、AI 回答生成和长期记忆写入事件。
- 查一次 AI 会话历史：用 `aiConversationId` 查询 `ai_conversation` 和 `ai_conversation_message`，再用其中的 `traceId`、`operationId` 回到操作日志和结构化日志。

## 和操作日志的关系

操作日志页面主要面向业务排障，展示 `traceId`、`operationId`、`loginSessionId`、用户编号、操作内容、接口路径、结果、耗时和变更摘要。结构化文件日志主要面向技术排障，可通过同一组标识继续追踪 Redis、ES、RabbitMQ、XXL-Job 等链路。

AI 长期记忆链路会额外写入 `ai_memory_event` 表，并在 `sys_operation_log` 中补充 `ai_memory_id`、`ai_memory_event_type`、`ai_memory_source`、`ai_memory_hit_count` 和 `ai_memory_trace_summary`。这些字段只保存脱敏摘要，用来确认某次回答是否参考了长期记忆、召回了几条、是否写入新记忆或因敏感内容被拒绝写入。

记忆治理和幻觉处理会继续写入审计事件：

- `CREATE_CANDIDATE`：弱信号候选记忆，只进入候选池，不参与召回。
- `CREATE_CONFLICTED`：与已有偏好冲突，等待用户在 AI 侧栏处理。
- `CREATE_SUSPECTED_HALLUCINATION`：疑似模型推测产生的记忆，不参与召回。
- `MEMORY_APPROVE` / `MEMORY_REJECT` / `MEMORY_RESTORE`：用户对候选、冲突、疑似幻觉或归档记忆的治理操作。
- `PROFILE_COMPILE`：定时任务或记忆变更后重新编译用户画像。

普通回答的幻觉护栏不会保存未脱敏原文。排查时优先看同一 `traceId` 下是否存在引用、工具调用、结构化数据结果和 `AiGroundingGuard` 修正后的回答摘要。

AI 会话历史会写入 `ai_conversation` 和 `ai_conversation_message`。其中 `ai_conversation` 负责归档、删除、最近上下文和消息数量，`ai_conversation_message` 负责单条用户消息、AI 回复、失败消息、工具摘要和引用摘要。删除采用逻辑删除，普通用户不可见但审计链路保留。

AI 查询结果游标会写入 `ai_query_cursor`。该表只保存当前用户当前会话的脱敏查询状态、分页位置、总数和已返回条数，用于“继续看”“查看剩余数据”等追问。游标本身不替代操作日志；排查时仍以 `traceId`、`operationId`、`loginSessionId` 和 `aiConversationId` 串联用户提问、工具调用、回答生成和后续分页查询。

AI 临时 SQL 链路会拆成“生成、自检、安全校验、语法纠错、执行”几个审计阶段。语法预检或执行阶段出现 SQL 语法错误时，会自动纠错最多 3 次，每次纠错后仍重新执行安全校验。日志中的错误分类包括 `SQL_GENERATE_EMPTY`、`SQL_SELF_CHECK_FAILED`、`SQL_SECURITY_BLOCKED`、`SQL_SYNTAX_ERROR` 和 `SQL_EXECUTION_ERROR`。前端只展示友好中文，内部 SQL、表名、字段名、权限码和异常堆栈不直接返回给用户。

一次完整 AI 问答的推荐排查顺序：

1. 用 `operationId` 精确定位用户点击“发送”的接口请求。
2. 用 `traceId` 查看本次请求内的文档检索、业务只读工具、Redis、Qdrant 和模型调用日志。
3. 用 `aiConversationId` 查看同一次会话内的用户提问、AI 工具调用、回答生成和长期记忆事件。
4. 用 `loginSessionId` 回看该用户本次登录期间是否持续出现相同问题。

## 相关文档

- [项目文档索引](README.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [物流接口文档](logistics-api.md)
- [AI 助手设计文档](ai-assistant-design.md)
- [Spring AI 接入说明](spring-ai.md)
