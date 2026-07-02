# Spring AI 接入说明

> **⚠️ 迁移中：AI 模块正在从 Spring AI (Java) 迁移到 Python FastAPI**。本文档描述的是迁移前的 Java 实现。新架构下 Python AI Service 接管模型网关、Agent 编排、RAG 和记忆智能；Java 保留 Tool Executor、SQL 安全校验、会话管理和记忆治理。详见 [ADR 0001 — Java+Python 混合架构](adr/0001-java-python-hybrid-architecture.md) 和 [Python AI 服务开发指南](python-ai-service.md)。

本文说明当前项目已落地的 Spring AI 只读助手能力，包括版本、配置、接口、权限、安全边界和本地验证方式。整体设计和后续演进路线见 [AI 助手设计文档](ai-assistant-design.md)。

## 当前状态

当前项目已经接入 Spring AI，但第一版严格限定为只读能力：

- 普通问答：`POST /ai/chat`
- 系统文档问答：读取 `README.md` 和 `docs/*.md`，通过 RAG 增量索引写入 Qdrant，模型不可用或 Qdrant 不可用时降级为轻量本地检索
- Spring AI Tool Calling：模型可选择后端只读工具查询业务数据，后端负责权限、分页、脱敏和客户数据隔离
- 全场景模糊搜索：用户只输入客户名、车牌号、地址、货物名、状态等短词时，会在当前账号有权限的业务模块中跨模块召回
- 自动联合查询：客户、订单、司机、车辆、异常等业务链路问题会按预设链路组合查询多个模块
- 临时只读 SQL：复杂统计、关联、连表类问题由模型生成候选 `SELECT`，后端安全校验后执行
- 日志排障：`POST /ai/logs/analyze`
- 当前用户会话：`GET /ai/conversations`、`GET /ai/conversations/{id}`
- 账号级长期记忆：MySQL 保存可审计记忆元数据，Qdrant 保存向量召回点，支持查看、删除、清空和关闭
- RAG 文档索引：`ai_document_index` 保存文档路径、内容哈希、分块数量和索引状态，文档未变化时跳过重复向量写入
- 前端入口：`/ai-assistant`

未开放能力：

- AI 不直接新增、修改、删除业务数据。
- AI 不绕过 Sa-Token、角色权限、用户特殊权限和客户数据隔离。
- 前端不直接调用模型平台，也不保存模型 API Key。
- 长期记忆只保存脱敏后的偏好和使用习惯，不保存密码、token、完整手机号、完整地址、SQL 原文或异常堆栈。

## 版本依赖

| 组件 | 当前版本 | 说明 |
| --- | --- | --- |
| Java | 21 | 项目运行基线 |
| Spring Boot | 3.5.14 | Spring AI 1.1.x 推荐兼容基线 |
| Spring Cloud | 2025.0.2 | 与 Boot 3.5.x 配套 |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos、Sentinel 依赖管理 |
| Spring AI | 1.1.7 | 通过 `spring-ai-bom` 管理 |
| Qdrant | 1.18.2 | AI 账号级长期记忆与 RAG 文档检索向量服务，本地端口 `6333` |

## 配置项

模型配置统一通过环境变量或本地私有配置提供，不提交真实密钥。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_AI_OPENAI_API_KEY` | `missing` | OpenAI 兼容接口密钥；保持默认时不调用真实模型 |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI 兼容接口地址，可替换为内网模型网关 |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o-mini` | 聊天模型名称 |
| `APP_AI_CONVERSATION_TTL_SECONDS` | `3600` | Redis 会话保留时间，单位秒 |
| `APP_AI_TOOL_MAX_CALLS` | `8` | 单次问答最多允许调用的只读工具次数，系统提示词和后端限制共用 |
| `APP_AI_QUERY_CURSOR_TTL_MINUTES` | `60` | 查询结果游标有效期，用于“继续看”“查看剩余数据”等多轮追问 |
| `APP_AI_SSE_TIMEOUT_MS` | `180000` | AI 流式问答的 Spring MVC 异步超时时间，单位毫秒 |
| `APP_AI_SSE_HEARTBEAT_SECONDS` | `10` | SSE 心跳事件间隔，避免长工具调用期间前端误判断开 |
| `APP_AI_MEMORY_QDRANT_ENABLED` | `true` | 是否启用 Qdrant 长期记忆向量召回；不可用时自动降级 |
| `APP_AI_MEMORY_QDRANT_BASE_URL` | `http://127.0.0.1:6333` | Qdrant HTTP 地址 |
| `APP_AI_MEMORY_QDRANT_COLLECTION` | `logistics_ai_user_memory` | 长期记忆向量集合；集合维度必须与当前 embedding 模型一致 |
| `APP_AI_RAG_ENABLED` | 兼容 `APP_AI_MEMORY_QDRANT_ENABLED`，默认 `true` | 是否启用 RAG 文档向量检索；关闭后文档问答退回本地轻量检索 |
| `APP_AI_RAG_INDEX_ON_STARTUP` | `true` | 应用启动时是否扫描 `README.md` 和 `docs/*.md` 并索引变更文档 |
| `APP_AI_RAG_QDRANT_BASE_URL` | 兼容 `APP_AI_MEMORY_QDRANT_BASE_URL`，默认 `http://127.0.0.1:6333` | RAG 文档向量检索使用的 Qdrant HTTP 地址 |
| `APP_AI_RAG_QDRANT_COLLECTION` | `logistics_docs` | RAG 文档向量集合；默认不清空、不重建现有集合 |
| `APP_AI_SSE_LEGACY_GET_ENABLED` | `true` | 是否保留旧版 `GET /ai/chat/stream` 流式入口；生产环境可关闭 |

未配置 `SPRING_AI_OPENAI_API_KEY` 时，应用仍可正常启动，AI 接口会返回本地文档检索结果和中文配置提示。这是为了保证 AI 模块不会影响现有物流业务功能。

## Nacos 配置

项目启动时会显式导入 Nacos 配置：

```yaml
spring:
  config:
    import:
      - optional:nacos:spring-ai.yml?group=${NACOS_CONFIG_GROUP:DEFAULT_GROUP}&refreshEnabled=true
```

Nacos 控制台配置要求：

| 项 | 值 |
| --- | --- |
| Data ID | `spring-ai.yml` |
| Group | `DEFAULT_GROUP`，或与 `NACOS_CONFIG_GROUP` 保持一致 |
| 命名空间 | 默认 Public；如果使用其它命名空间，需要设置 `NACOS_CONFIG_NAMESPACE` |
| 配置格式 | YAML |

示例：

```yaml
spring:
  ai:
    openai:
      api-key: sk-***
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-v4-flash
```

启动成功后，控制台会输出 AI 配置摘要：

```text
AI 配置摘要：configured=true, baseUrl=https://api.deepseek.com, model=deepseek-v4-flash, source=nacos:spring-ai.yml
```

日志不会打印 API Key 明文。AI 模块会优先运行时读取 Nacos `spring-ai.yml`；若 Spring Cloud Alibaba 启动阶段的 ConfigDataLoader 提示配置为空，但最终“AI 配置摘要”里 `source=nacos:spring-ai.yml`，说明 AI 模块实际已经读取到远程配置。

若 `model` 仍显示本地默认值，优先检查：

- Nacos 配置是否已发布。
- Data ID、Group、命名空间是否一致。
- 启动参数是否关闭了 Nacos 配置。
- `src/main/resources/application-dev.yml` 等本地 profile 文件是否写死了 `spring.ai.openai.*` 并覆盖远程配置。

示例：

```powershell
$env:SPRING_AI_OPENAI_API_KEY="sk-***"
$env:SPRING_AI_OPENAI_BASE_URL="https://api.openai.com"
$env:SPRING_AI_OPENAI_CHAT_MODEL="gpt-4o-mini"
mvn spring-boot:run
```

## 后端结构

当前 AI 模块位于 `src/main/java/jimmy/ai`：

| 类 | 职责 |
| --- | --- |
| `AiAssistantController` | 暴露 `/ai/**` 接口，统一返回 `ApiResponse<T>` |
| `AiAssistantService` | 编排文档检索、日志分析、模型调用和会话写入 |
| `AiChatPipeline` | 普通问答和 SSE 流式问答的统一编排链路，集中处理脱敏、会话、RAG、长期记忆、工具调用、兜底、审计和保存 |
| `AiIntentPlanner` / `AiExecutionPlan` | 生成只读执行计划，描述单模块、全局搜索、联合查询、日志排障、临时 SQL、RAG 或普通问答等模式 |
| `AiModelGateway` | 封装 Spring AI `ChatClient`，缺少密钥或模型异常时降级 |
| `AiKnowledgeService` | 优先从 Qdrant RAG 文档集合召回片段，Qdrant 不可用时降级为本地轻量检索 |
| `AiDocumentIndexer` | 扫描 `README.md` 和 `docs/*.md`，按内容哈希做增量索引，失败时记录状态但不阻断启动 |
| `AiDocumentIndexMapper` | 维护 `ai_document_index` 文档索引状态，避免索引状态 SQL 散落在 Java 代码中 |
| `AiBusinessQueryTools` | Spring AI Tool Calling 工具集，向模型暴露只读业务查询、全场景搜索、联合查询和看板查询能力 |
| `AiToolCallContext` | 收集同一次请求内的工具调用结果，供前端工具调用区和操作日志使用 |
| `AiReadonlyQueryService` | 复用现有业务查询能力，执行单模块、全场景模糊、自动联合查询并生成 AI 可读摘要 |
| `AiGeneratedSqlQueryService` | 对统计、关联、连表类问题生成候选只读 SQL，并在校验、预检、列权限过滤后执行 |
| `AiSqlSafetyValidator` | 校验 AI SQL 的单语句、只读、表白名单、可生成表、字段来源、敏感列和权限边界 |
| `AiReadableSchemaRegistry` | 统一维护 AI 临时 SQL 可读表、字段、模块、权限和是否允许模型直查，模型提示词和安全校验共用 |
| `AiLogAnalysisService` | 复用操作日志能力生成排障时间线 |
| `AiConversationService` | 使用 MySQL 持久化当前用户会话和消息，Redis 只缓存最近上下文 |
| `AiMemoryService` | 管理账号级长期记忆画像、召回、自动写入、删除和审计事件 |
| `AiMemoryExtractor` | 使用 LLM 判断对话是否包含用户偏好或习惯，替代关键词兜底规则 |
| `AiQdrantMemoryClient` | 调用 Qdrant 创建集合、校验集合维度、写入向量点、召回和删除记忆向量 |
| `AiMemoryVectorEncoder` | 优先使用 Ollama `bge-m3` 生成 1024 维向量；不可用时降级为确定性哈希向量并补齐到 1024 维 |
| `AiSensitiveDataMasker` | 模型输入前脱敏手机号、邮箱、token、密码等敏感数据 |

AI 日志排障复用现有操作日志和链路追踪字段，不新增日志查询 SQL。后续如果日志量变大，可以把结构化日志接入 Filebeat/Kibana 或 Elasticsearch 后再增强检索。

## Tool Calling、只读业务查询与临时 SQL

AI 查询分三层处理：

1. Spring AI Tool Calling：模型根据用户自然语言选择后端只读工具，例如单模块查询、全场景模糊搜索、业务联合查询、运营看板查询或临时只读 SQL 分析。工具只接收业务参数，不能直接执行任意 SQL。
2. 标准业务查询：工具内部复用 `LogisticsRequirementService.modulePage()` 或运营看板查询。该路径继续沿用模块白名单、字段白名单、分页、关键词、时间范围、客户账号数据隔离和前端状态脱敏。
3. 临时只读 SQL：当问题包含“统计、数量、总数、排名、平均、连表、关联、SQL、join、group”等复杂分析意图时，模型只生成候选 `SELECT`。候选 SQL 会先由模型按白名单 schema 自检修正，再通过 `AiSqlSafetyValidator` 和数据库 `EXPLAIN` 语法预检，最后才会由 `AiGeneratedSqlQueryService` 执行。临时 SQL 只开放简单可审计的查询子集：显式 `JOIN`、聚合、分组、排序和普通条件；不开放子查询、`UNION`、逗号连表和 AI 内部会话/记忆表直查。

普通“查看全部订单/运输任务/费用/异常”等明细查询必须走标准业务查询链路。`execute_readonly_sql` 只服务统计、聚合、排名和关联分析；Java 和 Python 两侧都会在工具选择前拦截普通明细问题，避免把数据库字段名、SQL 别名或内部工具名带到用户界面。

工具返回给 SSE 时会同时提供兼容字段和展示字段：`displayToolName`、`displayTarget`、`displaySummary`、`columns`、`rows/data`、`total/returnedCount/remainingCount/hasMore`。聊天气泡只使用安全中文摘要；结构化表格只展示经过列权限过滤和中文化后的字段。前端还有统一 sanitizer 兜底，详见 [前端开发说明](frontend.md)。

全局候选搜索和业务联合查询会额外返回 `dataGroups`。每个分组包含 `groupId`、`displayToolName`、`displayTarget`、`displaySummary`、`columns`、`rows`、`cursorId`、`total/returnedCount/remainingCount/hasMore` 和 `nextPageHint`。旧版 `rows/columns` 仍保留为首个主结果组用于兼容，但前端新页面必须优先按 `dataGroups` 渲染多张独立卡片，避免客户、订单、运单、异常等模块列和摘要混在一张表里。

自然语言解析会先做输入归一化，处理全角字符、零宽字符、换行、重复空格和关键词前后的误触标点，避免用户多打空格或符号时影响模块和关键词识别。解析器只负责识别白名单模块、关键词、业务编号、车牌号、状态和时间范围，不直接生成 SQL。

同一会话内支持轻量上下文继承：如果上一轮用户查询了“异常管理”，下一轮只输入“只要待处理的”这类缺少模块的追问，后端会把上一轮用户问题作为查询上下文补充给解析器，再按当前筛选条件查询。

Python Agent 侧增加轻量 `IntentPlan` 规划层，复用 `intent-classify.yml` 在模糊短词、多模块表达、上下文修正、代词追问、模块和关键词冲突场景中给出结构化建议。规则负责快路径和安全硬闸门，LLM 只提供 `modules/keyword/toolHint/refinementOfPrevious` 等建议，Java `AiReadonlyQueryService` 仍是最终模块白名单、权限、时间范围、工具边界和列权限校验点。典型链路是：`看看陈土豆` 先做受控业务候选搜索；用户随后说 `我要看跟陈土豆有关的订单信息` 或 `只看订单` 时，后端继承上一轮实体并收窄到订单模块，不再重复查询客户、用户、操作日志等无关模块。

全局搜索仅用于“无明确模块但有有效实体/编号/车牌/地址/姓名”的候选场景。普通业务全局搜索默认排除用户、角色、操作日志和上传文件；只有用户明确提到账户、权限、日志、文件时，才应走对应明确模块或日志工具。`异常`、`数据`、`这个月的` 等宽泛表达低置信度时应澄清或受控单模块查询，不能跨模块乱查。

跨会话个性化由“账号级长期记忆”负责：登录账号的常用模块、常用筛选条件、回答风格偏好会以脱敏摘要写入 MySQL，并把向量点写入 Qdrant。下一次新会话开始时，AI 会先按当前 `userId/userCode` 从 Qdrant 召回长期记忆，再把召回摘要作为回答上下文。召回和写入都绑定当前账号，不做企业客户级共享记忆。

## 账号级长期记忆

长期记忆用于让 AI 助手逐步适应同一账号的使用习惯，例如“回答先给结论”“常查待处理异常”“经常关注费用未收款”等。它不用于自动修改业务数据，也不绕过任何权限。

### 数据边界

| 存储 | 内容 | 边界 |
| --- | --- | --- |
| `ai_user_profile` | 记忆开关、默认回答风格、常用模块、常用查询习惯和记忆数量 | 账号级隔离，用户只能管理自己的画像 |
| `ai_user_memory` | 长期记忆元数据、分类、置信度、脱敏摘要、Qdrant 向量 ID | 逻辑删除，前端只展示脱敏摘要 |
| `ai_memory_event` | 记忆创建、召回、跳过、删除、清空、设置变更等事件 | 记录 `traceId`、`operationId`、`loginSessionId`、`aiConversationId` |
| Qdrant 集合 `logistics_ai_user_memory` | 脱敏摘要的向量点和账号级 payload | 召回时必须同时匹配 `userId/userCode` |

敏感内容不会写入长期记忆，包括密码、token、密钥、完整手机号、完整地址、SQL 原文、异常堆栈和未脱敏个人信息。Qdrant 不可用时，AI 问答和只读业务查询继续可用，只记录长期记忆降级审计事件。

### Qdrant 本地运行

本机安装路径：

```text
F:\Development\Middleware\qdrant\qdrant-1.18.2
```

数据目录：

```text
F:\Development\Middleware\qdrant\data
```

仓库脚本：

```cmd
ops\start-qdrant.bat
ops\status-qdrant.bat
ops\stop-qdrant.bat
```

健康检查：

```text
GET http://127.0.0.1:6333/readyz
```

当前默认 embedding 模型为 Ollama `bge-m3`，向量维度为 1024。应用启动或首次访问 Qdrant 集合时会校验集合维度：如果本地沿用了旧版 128 维集合，长期记忆向量召回会自动降级并在日志中提示重建集合或改用带模型维度后缀的新集合名。建议切换 embedding 模型时同步调整 `APP_AI_MEMORY_QDRANT_COLLECTION`，例如 `logistics_ai_user_memory_bge_m3_1024`，避免不同维度的数据混放。

### 记忆接口

| 接口 | 方法 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `/ai/memory/profile` | GET | `ai:memory:query` | 查询当前账号画像、开关、记忆数量和最近召回时间 |
| `/ai/memory/items` | GET | `ai:memory:query` | 分页查询当前账号长期记忆，内容脱敏 |
| `/ai/memory/items/{id}` | DELETE | `ai:memory:delete` | 删除当前账号单条长期记忆，并同步删除 Qdrant 向量 |
| `/ai/memory/items` | DELETE | `ai:memory:delete` | 清空当前账号全部长期记忆 |
| `/ai/memory/settings` | PUT | `ai:memory:settings` | 开启或关闭长期记忆，保留历史记忆直到用户主动清空 |

### 记忆写入策略

每次 AI 回答完成后，记忆提炼采用两层策略：

1. **LLM 优先提炼**：通过一个轻量分类提示词（输入约 100 token），让模型判断对话中是否包含用户偏好、习惯或格式要求。模型需要区分"一次性业务查询"和"持续性偏好表达"——例如"查一下订单123"只是普通查询，不会被记；而"以后先给结论"或"我主要查异常"会触发记忆写入。LLM 返回 JSON 格式的 `memoryType`、`memoryTitle`、`memorySummary` 和 `confidence`。

2. **关键词降级匹配**：当 LLM 未配置或调用失败时，自动降级为关键词匹配。仅匹配"以后都这样""我主要查""先给结论"等明确表达偏好的语句，不再使用"查什么存什么"的兜底规则。

记忆写入前还需通过敏感内容过滤、去重检查（按 `userId/userCode/memoryType/memorySummary` 去重）和置信度阈值（< 0.72 跳过）。

### 工具能力清单

| 工具 | 使用场景 | 后端边界 |
| --- | --- | --- |
| `queryBusinessModule` | 用户明确要查订单、运单、客户、司机、车辆、异常、费用、用户、角色、文件或日志中的某一个模块 | 只查当前账号有权限的单个白名单模块 |
| `globalFuzzySearch` | 用户只输入“陈土豆”“沪A12345”“天盈广场”“待处理”等短词或模糊问题 | 按当前账号权限跨模块检索，每个模块最多 5 条，总体摘要化展示 |
| `joinedBusinessQuery` | 用户提出“客户全貌”“订单完整链路”“司机任务链路”“车辆任务链路”“异常影响”等跨模块问题 | 只走后端预设业务链路，不让模型自由连表 |
| `queryDashboard` | 今日订单、待调度、运输中、异常数、收入趋势等运营看板问题 | 复用看板聚合服务和客户数据隔离 |
| `readonlySqlAnalysis` | 统计、排名、汇总、关联、连表等复杂分析问题 | 只允许通过校验的单条 `SELECT` |

### 全场景模糊搜索

当用户没有明确模块，或只输入一个短词时，AI 会优先走全场景模糊搜索，而不是固定继承上一轮模块。搜索范围包括：

- 客户名、联系人、手机号、城市、地址。
- 订单号、运单号、货物名、收发地址。
- 司机姓名、手机号、驾驶证号、准驾车型。
- 车牌号、车型、当前城市。
- 异常类型、异常描述、上报人、处理人。
- 费用订单号、付款状态。
- 操作日志的 `traceId`、`operationId`、`loginSessionId`、用户编号、接口地址。

状态类中文词会转换为候选状态码一起查询，例如“待处理”会补充 `WAIT_HANDLE`，“运输中”会补充 `IN_TRANSIT`、`TRANSPORTING`、`ON_ROUTE`。用户多打空格、标点、引号或全角字符时，后端会先归一化再查询。

### 意图闸门与查询边界

`/ai/chat` 和 `/ai/chat/stream` 在进入模型工具调用前会先经过 `AiConversationIntentClassifier`。这层只负责判断“本轮是否允许查库”，不替代 Sa-Token 权限、客户数据范围、SQL 白名单和脱敏校验。

| 用户表达 | 意图分类 | 行为 |
| --- | --- | --- |
| “查一下运输任务里的异常任务” | 业务查询 | 只开放只读业务工具，优先限定在运输任务模块 |
| “只要待处理的”“继续看剩余数据” | 上下文续查 | 复用最近查询游标或结构化上下文 |
| “我说的是运输任务，不要查其它模块，记住了吗” | 纠偏/偏好 | 只回复确认并进入记忆候选，不查库 |
| “异常任务”且没有上下文 | 需要澄清 | 先追问是运输任务、异常管理还是订单/运单异常 |
| “为什么你刚刚查错了” | 普通问答 | 解释原因或道歉，不调用业务查询工具 |

默认策略是“不确定就追问，不擅自查库”。只有业务查询、上下文续查、日志排障和临时只读 SQL 这几类意图会开放查询工具；普通问答、纠偏、澄清和纯文档问答不会触发 `AiReadonlyQueryService` 兜底查询。

### 自动联合查询

联合查询不让模型自由拼 SQL，而是按后端预设链路组合多个白名单模块：

- 客户全貌：客户档案、订单、运单、轨迹、费用、异常。
- 订单全貌：订单、运单、调度、任务、轨迹、费用、异常。
- 司机任务链路：司机、调度、任务、轨迹、异常。
- 车辆任务链路：车辆、调度、任务、轨迹、异常。
- 异常影响：异常、订单、运单、任务、费用。

每条链路都会按当前账号权限裁剪；没有权限的模块会跳过或返回友好提示，不向前端暴露权限码、表名、字段名、SQL 或异常堆栈。

AI 查询意图遵循以下决策顺序，避免模型把用户偷懒输入误解成错误模块：

0. 对话控制优先：纠正、限定范围、要求记住、确认是否理解时，不触发业务查询。
1. 明确模块优先，例如“所有运输任务”只确定查询模块，不把“所有”“任务”当关键词。
2. 跨模块意图优先，例如“所有运单和订单”“陈土豆的订单和费用”“这辆车今天的任务和轨迹”会进入联合查询。
3. 明确字段优先，例如“客户名称为陈土豆”会提取关键词“陈土豆”并查询客户管理。
4. 纯业务名称兜底，例如用户只输入“陈土豆”，默认做全场景模糊搜索。
5. 筛选式追问才继承上一轮模块，例如“只要待处理的”“今天的”“最近7天”会复用上一轮模块。
6. 确认式补充会复用上一轮关键词，例如上一轮“陈土豆”，下一轮“是一个客户”，会继续用“陈土豆”查询客户管理。

订单相关模块按业务边界拆分：

- “订单管理 / 运单管理 / 订单号 / LO / ORD / 下单”优先查询业务订单模块，用于查看客户下单、货物、收发地址和调度前状态。
- “运单中心 / 运单号 / 运输单 / WB”优先查询运输运单模块，用于查看订单生成后的运输单、始发网点、目的网点和运输状态。
- 如果用户只输入人名或客户名，客户模块未命中时，系统会在当前账号可访问的订单、运单中心、运输任务、物流轨迹、异常和费用等模块中继续做全局只读查找。
- 如果用户追问“全局查找 / 全局搜索”，系统会复用上一轮关键词执行跨模块只读召回，不把“全局查找”当成新的查询关键词。

如果未来接入向量检索，向量库只作为语义召回和候选模块判断层，最终业务数据仍必须回到本节所述的白名单查询、权限校验、客户数据隔离和脱敏流程。

临时只读 SQL 的执行链路：

1. `AiGeneratedSqlQueryService` 根据用户问题生成候选 `SELECT`。
2. 模型自检候选 SQL，只能返回修正后的单条 MySQL `SELECT`，不得返回解释、Markdown 或分号。
3. `AiSqlSafetyValidator` 校验单条 SELECT、危险关键字、多语句、注释、`select *`、白名单表、模型可直查表、当前账号查询权限和敏感列权限。
4. 后端用 `EXPLAIN <sql>` 做语法和字段预检，不把候选 SQL 包成子查询，避免破坏原 SQL 的 `JOIN/GROUP/ORDER` 结构。
5. 如果语法预检或执行阶段报 SQL 语法错误，模型会按原问题、上一轮 SQL 和错误摘要自动纠错，最多重试 3 次；每次纠错后仍重新执行安全校验和语法预检。
6. 预检通过后，在顶层追加或收紧 `limit 20` 执行真实查询，并把结果按中文安全列摘要化、脱敏后返回。

临时只读 SQL 的错误分类：

| 错误码 | 含义 | 展示策略 |
| --- | --- | --- |
| `SQL_GENERATE_EMPTY` | 模型未生成 SQL | 前端提示换一种描述 |
| `SQL_SELF_CHECK_FAILED` | 模型自检无法产出合规 SQL | 前端提示自检失败 |
| `SQL_SECURITY_BLOCKED` | 后端安全校验拦截 | 前端只展示权限或安全友好提示 |
| `SQL_SYNTAX_ERROR` | 数据库语法预检或执行时报语法错误，且 3 次自动纠错后仍失败 | 前端提示临时查询语法问题 |
| `SQL_EXECUTION_ERROR` | 非语法类执行失败 | 前端提示联系管理员 |

临时只读 SQL 的硬性边界：

- 只允许单条 `SELECT`，禁止 `insert/update/delete/drop/alter/create/truncate/call/execute` 等写操作或过程调用。
- 禁止多语句、注释片段、`select *`、子查询、`UNION` 和逗号连表；多表查询必须使用显式 `JOIN`，避免正则表解析漏掉未授权表。
- 密码、token、secret、api_key 等静态敏感字段永久禁止；手机号、金额、地址、异常详情等业务敏感字段必须通过当前账号的列权限校验。
- 只能访问后端白名单中标记为“允许模型直查”的业务表和字段，且每张表都必须通过当前登录账号的查询权限校验。AI 会话、长期记忆、查询游标等内部表只能由后端工具读取，不能通过模型生成 SQL 直查。
- SQL 结果列会按“结果别名 -> 原始字段”再次过滤；普通字段建议保留数据库字段名，聚合统计列可以使用安全英文别名。
- SQL 工具结果不会返回 Markdown 明细表、SQL 文本、物理字段名或内部工具名；普通业务字段统一映射为中文展示名，无法确认安全的聚合别名显示为 `统计字段1`、`统计字段2` 等中性列名。
- 客户账号存在数据范围限制，暂不开放临时关联 SQL，仍走普通客户、订单、轨迹查询。
- 顶层统一追加或收紧 `limit 20`，回答区只展示摘要化结果，敏感内容继续脱敏。

当前临时只读 SQL schema 使用数据库真实物理列，不使用前端展示字段、VO 字段或接口聚合别名。比如 `logistics_waybill` 没有 `order_no` 字段，需要订单号时必须通过 `order_id` 显式 `JOIN logistics_order` 读取 `logistics_order.order_no`。当前覆盖范围包括：

- 物流业务表：客户、仓库、司机、车辆、路线、订单、运单、调度、任务、轨迹、异常、费用、库存、运费账单。
- 系统权限表：用户、角色、菜单、权限、角色菜单、角色权限、用户角色、用户特殊权限。
- 审计资源表：操作日志、上传文件。
- AI 会话、长期记忆、查询游标等内部表不允许模型自定义 SQL 直查，只能通过后端专用工具读取脱敏元数据。

以下字段不会暴露给临时 SQL：密码、token、密钥、手机号 hash、AI 消息正文、请求参数、变更摘要、异常堆栈、客户端 IP、登录 IP、UA 和失败原因等敏感或高风险内容。AI 会话和长期记忆表只开放元数据字段，完整消息正文和记忆摘要仍通过专用接口做权限和脱敏控制。

这个能力只服务自然语言临时分析，不改变项目的 SQL 开发规范。普通业务 SQL 仍必须维护在 Mapper XML 中，详见 [MyBatis 使用规范](mybatis.md)。

## 接口与权限

| 接口 | 方法 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `/ai/chat` | POST | `ai:chat` | 普通问答、文档问答、只读业务说明 |
| `/ai/chat/stream` | POST | `ai:chat` | 推荐流式问答入口，使用 POST body 传递用户问题，避免正文进入 URL |
| `/ai/chat/stream` | GET | `ai:chat` | 旧版兼容入口，默认保留；可通过 `APP_AI_SSE_LEGACY_GET_ENABLED=false` 关闭 |
| `/ai/logs/analyze` | POST | `ai:log:analyze` | 按链路标识分析操作日志 |
| `/ai/conversations` | GET | `ai:conversation:query` | 查询当前用户未过期会话摘要 |
| `/ai/conversations/{id}` | GET | `ai:conversation:query` | 查询当前用户自己的会话详情 |
| `/ai/conversations/{id}/archive` | PUT | `ai:conversation:archive` | 归档当前用户自己的会话 |
| `/ai/conversations/{id}/restore` | PUT | `ai:conversation:archive` | 恢复当前用户自己的归档会话 |
| `/ai/conversations/{id}` | DELETE | `ai:conversation:delete` | 逻辑删除当前用户自己的会话 |
| `/ai/conversations` | DELETE | `ai:conversation:delete` | 逻辑清空当前用户自己的会话 |
| `/ai/memory/profile` | GET | `ai:memory:query` | 查询当前用户长期记忆画像 |
| `/ai/memory/items` | GET | `ai:memory:query` | 查询当前用户长期记忆列表 |
| `/ai/memory/items/{id}` | DELETE | `ai:memory:delete` | 删除当前用户单条长期记忆 |
| `/ai/memory/items` | DELETE | `ai:memory:delete` | 清空当前用户全部长期记忆 |
| `/ai/memory/settings` | PUT | `ai:memory:settings` | 开启或关闭当前用户长期记忆 |

应用启动和增量脚本会补齐 AI 助手菜单和当前用户自己的基础 AI 权限：`ai:chat`、`ai:conversation:query`、`ai:conversation:archive`、`ai:conversation:delete`、`ai:memory:query`、`ai:memory:delete`、`ai:memory:settings`。`ai:log:analyze` 属于跨用户审计能力，不随 `ai:chat` 自动展开，只默认授予管理员、审计员和财务经理；其它角色如确需使用，需要在“权限配置”中单独分配。

## 会话持久化与归档删除

AI 会话历史以 MySQL 为主存储，Redis 仅缓存最近上下文。这样服务重启、Redis TTL 到期或 Redis 重启后，用户仍能在 AI 助手左侧看到历史会话。

| 表 | 作用 |
| --- | --- |
| `ai_conversation` | 保存会话标题、状态、归档/删除时间、消息数量和会话上下文快照 |
| `ai_conversation_message` | 保存脱敏后的用户消息、AI 回复、工具调用摘要、引用来源摘要和链路标识 |

会话删除采用逻辑删除：普通用户列表不再显示，审计链路仍保留。归档只改变会话状态，不删除消息。会话上下文快照用于当前会话内的连续追问，例如“只要待处理的”“再看他的订单和费用”；跨会话个性化仍由长期记忆负责。

老版本 Redis 中仍存在的短期会话会在应用启动时尽力迁移到 MySQL；已经因 Redis 清空、TTL 到期或历史服务重启而丢失的会话无法恢复。对应增量脚本见 [数据库增量迁移说明](incremental-migration.md)。

## 前端入口

前端页面位于：

```text
frontend/src/views/AiAssistantView.vue
frontend/src/api/ai-assistant.js
```

路由：

```text
/ai-assistant
```

菜单显示条件：

```text
ai:chat
```

页面由三部分组成：

- 左侧：当前用户 MySQL 持久化会话列表，支持当前/归档切换、关键词搜索、归档、恢复、删除和清空。
- 中间：问答消息区、结构化数据结果预览、引用来源、工具调用摘要。
- 右侧：日志排障查询和时间线结果。

AI 回答区只承载结论和摘要。业务查询返回多条结构化数据时，前端默认预览前 10 条；超过 10 条的数据会显示“继续查看剩余数据”和“查看全部”入口。后端会把本次查询的模块、关键词、时间范围、页码、总数、已返回条数和工具类型写入 `ai_query_cursor`，用户继续追问“继续看、查看剩余28条、下一页”时优先复用游标分页，不再靠模型猜上一句话。游标默认 60 分钟过期，只保存脱敏查询状态，不保存敏感原文。

当一次回答里存在多个结构化结果表时，前端点击某一张卡片的“继续查看剩余数据”必须把该卡片的 `cursorId` 通过 `POST /ai/chat/stream` 请求体传回后端。后端收到 `cursorId` 后会直接按当前用户、当前会话和该游标续页，并通过 SSE `tool_result` 事件返回单张续页表格，避免再次进入模型推理后出现多个表格串台或没有表格的情况。

日志排障面板只有当前账号拥有 `ai:log:analyze` 时才展示；普通 AI 问答用户不会看到该入口。

## 安全边界

AI 助手必须遵守现有系统安全规则：

- 用户看不到的数据，AI 也不能回答。
- 所有接口继续走 Sa-Token 登录态和权限校验。
- 流式问答推荐使用 `POST /ai/chat/stream`，用户问题放在请求体中；旧版 `GET /ai/chat/stream` 仅为兼容保留，可通过 `APP_AI_SSE_LEGACY_GET_ENABLED=false` 在生产环境关闭。操作日志会过滤 `message`、`prompt`、`question`、`pageContext` 等参数，避免 AI 提问正文进入参数摘要。该接口受 `APP_AI_SSE_TIMEOUT_MS` 控制，默认 180 秒，避免模型和工具调用超过 Spring MVC 默认异步超时后被容器提前关闭。
- SSE 流式问答运行在 Spring MVC 异步线程中，不能直接依赖 Sa-Token 的请求线程上下文；Controller 会预捕获 `loginId`、权限列表、角色、客户范围、用户编号和 `loginSessionId`，下游只读工具、临时 SQL 校验、长期记忆和 AI 审计日志都必须优先读取这份快照。
- AI 只读，不直接执行新增、修改、删除；临时 SQL 也只允许经过安全校验的 `SELECT`。
- 用户界面不展示 `execute_readonly_sql`、`query_business_module`、`generated_sql`、snake_case 物理字段名、权限码、SQL 文本或异常堆栈；后端展示字段、Python SSE 转发和前端 sanitizer 需要共同兜底。
- 输入模型前脱敏手机号、邮箱、token、密码、详细敏感内容。
- `traceId`、`operationId`、`loginSessionId`、`userId`、`userCode` 保留原值，方便审计追踪。
- AI 问答和日志分析会进入操作日志，并通过 `operation_source`、`executor_type`、`ai_tool_name`、`ai_tool_target` 区分“用户询问AI”“AI调用只读工具”“AI生成回答”。
- AI 长期记忆召回、写入、删除、清空和设置变更会写入 `ai_memory_event`，并在操作日志中记录 `ai_memory_id`、`ai_memory_event_type`、`ai_memory_source`、`ai_memory_hit_count` 和 `ai_memory_trace_summary`。
- AI 审计摘要只保存脱敏后的问题摘要和结果摘要，完整敏感内容不会写入操作日志。

## 本地验证

后端验证：

```bash
mvn test
mvn -DskipTests package
```

前端验证：

```bash
cd frontend
npm run test:unit
npm run build
```

无模型密钥验证：

```text
POST /ai/chat
{
  "message": "这个系统有哪些文档？"
}
```

预期结果：

- 接口返回成功响应。
- `answer` 中包含本地文档检索结果和“未配置模型 API Key”提示。
- 操作日志记录“AI助手-普通问答”，并额外生成“AI助手-用户提问”“AI助手-只读工具调用”“AI助手-生成回答”等分层审计记录。
- 原有登录、运单、客户、异常、费用、权限、日志模块不受影响。

只读业务查询验证：

```text
POST /ai/chat
{
  "message": "统计一下各订单状态分别有多少条"
}
```

预期结果：

- 配置真实模型时，可触发临时只读 SQL 查询并返回安全中文摘要和结构化表格，表格列名必须是中文展示名。
- 未配置真实模型时，返回普通查询或中文配置提示，不影响其它业务接口。
- 权限不足时只返回“当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。”，不暴露权限码、内部表名或异常堆栈。

日志排障验证：

```text
POST /ai/logs/analyze
{
  "traceId": "某次请求的 traceId"
}
```

预期结果：

- 返回 `summary`、`timeline`、`riskPoints`、`suggestions`。
- 手机号、邮箱、地址、token、密码等敏感内容不明文展示。

长期记忆验证：

```text
GET /ai/memory/profile
GET /ai/memory/items?page=1&pageSize=10
PUT /ai/memory/settings
```

## Token 用量追踪

每次调用模型（对话、SQL 生成/自检/纠错、记忆提炼）都会自动记录到 `ai_token_usage` 表：

| 字段 | 说明 |
|------|------|
| `model_name` | 模型名称（如 `deepseek-v4-flash`、`gpt-4o-mini`） |
| `purpose` | 调用用途：`chat` / `sql_generate` / `sql_self_check` / `sql_repair` / `memory_extract` |
| `prompt_tokens` / `completion_tokens` / `cached_tokens` / `total_tokens` | Token 消耗统计（含缓存命中数） |
| `estimated_cost` | 估算费用，按输入/输出分开计价 + 缓存折扣 |
| `estimated_cost_currency` | 费用币种（CNY / USD），DeepSeek 用人民币，OpenAI 用美元 |
| `duration_ms` | 调用耗时（毫秒） |

**费用计算方式：**

费用 = (未命中输入 Token / 1M) × 输入单价 + (缓存输入 Token / 1M) × 缓存单价 + (输出 Token / 1M) × 输出单价。
缓存命中数从 Spring AI 的 `Usage.getNativeUsage()` → `OpenAiApi.Usage.promptTokensDetails().cachedTokens()` 获取。

**模型单价参考：**

| 模型 | 输入（缓存未命中） | 输入（缓存命中） | 输出 | 币种 |
|------|:---:|:---:|:---:|:---:|
| `deepseek-v4-flash` | ¥1.00/M | ¥0.02/M | ¥2.00/M | CNY |
| `deepseek-chat`（废弃中） | ¥1.00/M | ¥0.02/M | ¥2.00/M | CNY |
| `gpt-4o-mini` | $0.15/M | $0.15/M | $0.60/M | USD |
| `gpt-4o` | $2.50/M | $2.50/M | $10.00/M | USD |
| 其他 | $0.15/M | $0.15/M | $0.60/M | USD（默认） |

配置开关：`APP_AI_TOKEN_USAGE_ENABLED=true`（默认开启）。写入失败不影响主业务。

SQL 查询示例：

```sql
-- 最近 30 天各模型调用统计
select model_name, purpose, count(*) as calls,
       sum(total_tokens) as tokens,
       round(sum(estimated_cost), 4) as cost_usd
from ai_token_usage
where created_at >= date_sub(now(), interval 30 day)
  and deleted = 0
group by model_name, purpose
order by cost_usd desc;
```
```

预期结果：

- Qdrant 可用时，AI 问答后会自动写入脱敏长期记忆，并能在后续新会话中召回。
- Qdrant 不可用时，`/ai/chat` 和物流业务接口仍正常，记忆链路只降级为不召回或不写向量。
- 删除或清空记忆后，MySQL 记忆记录逻辑删除，Qdrant 向量点同步删除。
- 关闭长期记忆后不再召回、不再写入；历史记忆保留到用户主动清空。
- 操作日志和记忆事件表均能通过 `traceId`、`operationId`、`loginSessionId`、`aiConversationId` 串联一次 AI 问答链路。

### 长期记忆治理与幻觉处理

长期记忆不再是“抽取到就生效”的简单策略，而是采用候选、冲突、疑似幻觉和画像编译机制：

1. 模型抽取出的偏好先进入 `AiMemoryGovernanceService` 判定。明确来自用户原话的偏好才可能直接生效；模型摘要里的“用户可能希望”“推测用户习惯”不能自证为长期记忆。
2. 弱信号进入 `CANDIDATE`；与已有偏好冲突的记忆进入 `CONFLICTED`；包含“可能、也许、猜测、推测、不确定”等非用户明确表达的内容进入 `SUSPECTED_HALLUCINATION`。
3. 只有 `ACTIVE` 和 `WEAKENING` 参与召回。`CANDIDATE`、`CONFLICTED`、`SUSPECTED_HALLUCINATION`、`REJECTED`、`ARCHIVED` 不参与回答生成。
4. 用户可在 AI 助手侧栏按状态查看长期记忆，并对候选、冲突和疑似幻觉记忆执行批准、拒绝、恢复或删除。
5. 定时任务会根据证据数量、置信度、负反馈和时间衰减做候选晋升、冲突扫描、画像重编译和生命周期归档。

除记忆链路外，普通 AI 回答也增加了 `AiGroundingGuard` 证据护栏：

- 没有引用、工具结果或结构化数据时，如果回答声称“已查询”“共有”“命中记录”“系统中有数据”，会改为安全提示，避免无证据断言。
- 只有分页结果时，如果回答声称“完整列出全部数据”，会追加分页说明，引导用户用结果游标继续查看。
- 该护栏不替代模型推理，只处理高风险事实性幻觉；最终仍以权限、审计、脱敏和只读工具结果为准。

数据库字段见 [数据库设计说明](logistics-database.md)，增量脚本见 [数据库增量迁移说明](incremental-migration.md)，审计串联方式见 [链路追踪与会话审计说明](trace-context-audit.md)。

## 后续规划

- RAG 文档索引继续补充文档版本、来源章节和引用精度，长期记忆仍按账号隔离。
- 业务查询工具继续补充更多领域摘要；临时只读 SQL 网关保持只读和白名单边界。
- 日志排障接入结构化日志文件或日志索引，展示更完整的接口、Redis、RabbitMQ、ES 链路。
- 写操作仍保持二次确认路线，必须先生成建议，再由用户明确确认并记录审计。

## 相关文档

- [项目文档索引](README.md)
- [AI 助手设计文档](ai-assistant-design.md)

## Prompt 模板治理与输出校验

当前 AI 助手已把高风险 Prompt 从 Java 代码中抽离为可版本化模板，默认读取 `ai_prompt_template` 表；如果表不存在、模板停用、变量缺失或渲染失败，会自动回退到代码内置模板，不影响 `/ai/chat`、`/ai/chat/stream`、文件分析、长期记忆和临时 SQL 查询。

第一批模板编码包括：

| 模板编码 | 用途 |
| --- | --- |
| `AI_CHAT_SYSTEM` / `AI_CHAT_USER` | 普通问答和 SSE 问答的系统提示词、用户提示词 |
| `AI_SQL_GENERATE_SYSTEM` / `AI_SQL_GENERATE_USER` | 临时只读 SQL 候选生成 |
| `AI_SQL_SELF_CHECK_SYSTEM` / `AI_SQL_SELF_CHECK_USER` | 临时 SQL 自检修正 |
| `AI_SQL_REPAIR_SYSTEM` / `AI_SQL_REPAIR_USER` | SQL 语法预检失败后的自动纠错 |
| `AI_FILE_ANALYSIS_SYSTEM` / `AI_FILE_ANALYSIS_USER` | 文件内容分析 |
| `AI_MEMORY_EXTRACT_SYSTEM` / `AI_MEMORY_EXTRACT_USER` | 长期记忆候选提取 |

模板使用 Mustache 渲染。每个模板必须声明 `required_variables` 和 `optional_variables`，渲染服务只会把声明过的变量传入模板，避免未审计上下文被误注入 Prompt。模型调用会记录 `template_code`、`template_version` 和 `purpose`，用于后续 token 统计和审计排查。

输出校验分三层：

1. `AiOutputValidator` 负责剥离 Markdown 代码块、提取 JSON、处理空输出和超长输出。
2. `AiSqlOutputValidator` 负责 SQL 生成链路第一道校验，只接受单条 `SELECT` 文本，禁止解释文字、Markdown、多语句、注释和写操作关键字。
3. `AiToolOutputValidator` 负责面向前端展示的工具摘要清洗，避免权限码、SQL、表字段名、异常堆栈和敏感数据直接展示给用户。

临时 SQL 链路仍保持只读边界：模型生成候选 SQL 后，先经过模板化自检和输出校验，再进入 `AiSqlSafetyValidator` 的表白名单、字段白名单、权限、敏感列和 `EXPLAIN` 预检；失败时最多按语法错误摘要自动纠错 3 次。相关 SQL 规范见 [MyBatis 与 SQL 规范](mybatis.md)，数据库脚本见 [数据库增量迁移说明](incremental-migration.md)。
- [配置说明](configuration.md)
- [MyBatis 使用规范](mybatis.md)
- [环境与中间件版本清单](environment-versions.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [链路追踪与会话审计说明](trace-context-audit.md)
