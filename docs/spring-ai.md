# Spring AI 接入说明

本文说明当前项目已落地的 Spring AI 只读助手能力，包括版本、配置、接口、权限、安全边界和本地验证方式。整体设计和后续演进路线见 [AI 助手设计文档](ai-assistant-design.md)。

## 当前状态

当前项目已经接入 Spring AI，但第一版严格限定为只读能力：

- 普通问答：`POST /ai/chat`
- 系统文档问答：读取 `README.md` 和 `docs/*.md` 做轻量检索
- 只读业务查询：复用现有模块白名单、权限校验、分页、脱敏和客户数据隔离
- 临时只读 SQL：复杂统计、关联、连表类问题由模型生成候选 `SELECT`，后端安全校验后执行
- 日志排障：`POST /ai/logs/analyze`
- 当前用户会话：`GET /ai/conversations`、`GET /ai/conversations/{id}`
- 前端入口：`/ai-assistant`

未开放能力：

- AI 不直接新增、修改、删除业务数据。
- AI 不绕过 Sa-Token、角色权限、用户特殊权限和客户数据隔离。
- 前端不直接调用模型平台，也不保存模型 API Key。
- 第一版不引入向量库，文档知识库先使用 Markdown 文件轻量检索。

## 版本依赖

| 组件 | 当前版本 | 说明 |
| --- | --- | --- |
| Java | 21 | 项目运行基线 |
| Spring Boot | 3.5.14 | Spring AI 1.1.x 推荐兼容基线 |
| Spring Cloud | 2025.0.2 | 与 Boot 3.5.x 配套 |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos、Sentinel 依赖管理 |
| Spring AI | 1.1.7 | 通过 `spring-ai-bom` 管理 |

## 配置项

模型配置统一通过环境变量或本地私有配置提供，不提交真实密钥。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_AI_OPENAI_API_KEY` | `missing` | OpenAI 兼容接口密钥；保持默认时不调用真实模型 |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI 兼容接口地址，可替换为内网模型网关 |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o-mini` | 聊天模型名称 |
| `APP_AI_CONVERSATION_TTL_SECONDS` | `3600` | Redis 会话保留时间，单位秒 |

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
| `AiModelGateway` | 封装 Spring AI `ChatClient`，缺少密钥或模型异常时降级 |
| `AiKnowledgeService` | 从 `README.md` 和 `docs/*.md` 检索文档片段 |
| `AiReadonlyQueryService` | 复用现有业务查询能力，生成 AI 可读的查询摘要 |
| `AiGeneratedSqlQueryService` | 对统计、关联、连表类问题生成候选只读 SQL 并执行安全查询 |
| `AiSqlSafetyValidator` | 校验 AI SQL 的单语句、只读、表白名单、字段和权限边界 |
| `AiLogAnalysisService` | 复用操作日志能力生成排障时间线 |
| `AiConversationService` | 使用 Redis 保存当前用户短期会话 |
| `AiSensitiveDataMasker` | 模型输入前脱敏手机号、邮箱、token、密码等敏感数据 |

AI 日志排障复用现有操作日志和链路追踪字段，不新增日志查询 SQL。后续如果日志量变大，可以把结构化日志接入 Filebeat/Kibana 或 Elasticsearch 后再增强检索。

## 只读业务查询与临时 SQL

AI 查询分两层处理：

1. 标准业务查询：自然语言先解析到运单、客户、车辆、司机、异常、费用、操作日志等白名单模块，再复用 `LogisticsRequirementService.modulePage()` 或运营看板查询。该路径继续沿用模块白名单、字段白名单、分页、关键词、时间范围、客户账号数据隔离和前端状态脱敏。
2. 临时只读 SQL：当问题包含“统计、数量、总数、排名、平均、连表、关联、SQL、join、group”等复杂分析意图时，模型只生成候选 `SELECT`。候选 SQL 需要通过 `AiSqlSafetyValidator` 后才会由 `AiGeneratedSqlQueryService` 执行。

临时只读 SQL 的硬性边界：

- 只允许单条 `SELECT`，禁止 `insert/update/delete/drop/alter/create/truncate/call/execute` 等写操作或过程调用。
- 禁止多语句、注释片段、`select *`、密码、token、secret、api_key 等敏感字段。
- 只能访问后端白名单中的业务表和字段，且每张表都必须通过当前登录账号的查询权限校验。
- 客户账号存在数据范围限制，暂不开放临时关联 SQL，仍走普通客户、订单、轨迹查询。
- 外层统一包裹 `limit 20`，回答区只展示摘要化结果，敏感内容继续脱敏。

这个能力只服务自然语言临时分析，不改变项目的 SQL 开发规范。普通业务 SQL 仍必须维护在 Mapper XML 中，详见 [MyBatis 使用规范](mybatis.md)。

## 接口与权限

| 接口 | 方法 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `/ai/chat` | POST | `ai:chat` | 普通问答、文档问答、只读业务说明 |
| `/ai/logs/analyze` | POST | `ai:log:analyze` | 按链路标识分析操作日志 |
| `/ai/conversations` | GET | `ai:conversation:query` | 查询当前用户未过期会话摘要 |
| `/ai/conversations/{id}` | GET | `ai:conversation:query` | 查询当前用户自己的会话详情 |

管理员启动时会自动补齐 AI 助手菜单和权限；其它角色需要在“权限配置”中按需分配。

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

- 左侧：当前用户 Redis 会话列表。
- 中间：问答消息区、引用来源、工具调用摘要。
- 右侧：日志排障查询和时间线结果。

## 安全边界

AI 助手必须遵守现有系统安全规则：

- 用户看不到的数据，AI 也不能回答。
- 所有接口继续走 Sa-Token 登录态和权限校验。
- AI 只读，不直接执行新增、修改、删除；临时 SQL 也只允许经过安全校验的 `SELECT`。
- 输入模型前脱敏手机号、邮箱、token、密码、详细敏感内容。
- `traceId`、`operationId`、`loginSessionId`、`userId`、`userCode` 保留原值，方便审计追踪。
- AI 问答和日志分析会进入操作日志，便于后续追查。

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
- 操作日志记录“AI助手-普通问答”。
- 原有登录、运单、客户、异常、费用、权限、日志模块不受影响。

只读业务查询验证：

```text
POST /ai/chat
{
  "message": "统计一下各订单状态分别有多少条"
}
```

预期结果：

- 配置真实模型时，可触发临时只读 SQL 查询并返回 Markdown 摘要表格。
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

## 后续规划

- 文档知识库从轻量关键词检索升级为向量检索或 Elasticsearch 文档检索。
- 业务查询工具继续补充更多领域摘要；临时只读 SQL 网关保持只读和白名单边界。
- 日志排障接入结构化日志文件或日志索引，展示更完整的接口、Redis、RabbitMQ、ES 链路。
- 写操作仍保持二次确认路线，必须先生成建议，再由用户明确确认并记录审计。

## 相关文档

- [项目文档索引](README.md)
- [AI 助手设计文档](ai-assistant-design.md)
- [配置说明](configuration.md)
- [MyBatis 使用规范](mybatis.md)
- [环境与中间件版本清单](environment-versions.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [链路追踪与会话审计说明](trace-context-audit.md)
