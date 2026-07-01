# 项目文档索引

本目录存放物流管理系统的架构、开发、接口、数据库、权限、安全、日志、运维和后续扩展设计文档。新开发者建议按下列顺序阅读。

## 新人接入

| 文档 | 用途 |
| --- | --- |
| [getting-started.md](getting-started.md) | 从拉取代码到启动前后端的快速上手指南 |
| [development-guide.md](development-guide.md) | 编码规范、Git 提交规范、SQL 规范、测试要求和代码审查清单 |
| [local-development.md](local-development.md) | 本地开发环境、中间件、IDE 和启动参数说明 |
| [environment-versions.md](environment-versions.md) | JDK、Maven、Node、MySQL、Redis、RabbitMQ、ES、Qdrant、Nacos、Sentinel、XXL-Job 和 Docker 镜像版本清单 |
| [configuration.md](configuration.md) | Spring Boot、Nacos、Redis、RabbitMQ、ES、XXL-Job 等配置说明 |
| [java21-springboot3-upgrade.md](java21-springboot3-upgrade.md) | Java 21 与 Spring Boot 3 升级范围、验证结果和后续注意事项 |

## 系统设计

| 文档 | 用途 |
| --- | --- |
| [architecture.md](architecture.md) | 项目目录、Java+Python 混合架构拓扑、后端分层和组件说明 |
| [CONTEXT-MAP.md](../CONTEXT-MAP.md) | 多上下文地图（Java Backend + Python AI Service） |
| [CONTEXT.md](../CONTEXT.md) | Java 侧架构术语表（含 _Avoid_ 别名） |
| [requirements-mapping.md](requirements-mapping.md) | 物流管理需求与当前实现模块的对应关系 |
| [frontend.md](frontend.md) | 前端新人接入手册 |
| [mybatis.md](mybatis.md) | MyBatis Mapper XML 使用规范 |
| [spring-ai.md](spring-ai.md) | Spring AI 接入状态、权限、只读 SQL、RAG 和 Qdrant 长期记忆说明 |

## AI 模块混合架构迁移

| 文档 | 用途 |
| --- | --- |
| [adr/0001-java-python-hybrid-architecture.md](adr/0001-java-python-hybrid-architecture.md) | 架构决策记录 — 为什么迁 Python、边界在哪、不引入什么 |
| [python-ai-service.md](python-ai-service.md) | Python AI 服务开发指南 — 启动、测试、API 端点、OTel 配置 |
| [ai-assistant-design.md](ai-assistant-design.md) | AI 助手整体设计 — 当前落地状态、权限审计、后续演进 |

## 业务与接口

| 文档 | 用途 |
| --- | --- |
| [logistics-api.md](logistics-api.md) | 物流业务接口说明 |
| [auth-api.md](auth-api.md) | 登录认证、会话和权限相关接口说明 |
| [role-permission-api.md](role-permission-api.md) | 角色权限、用户特殊权限和菜单权限接口说明 |
| [user-api.md](user-api.md) | 用户与客户账号相关接口说明 |

## 数据库与迁移

| 文档 | 用途 |
| --- | --- |
| [logistics-database.md](logistics-database.md) | 物流业务表、系统表和关键字段说明 |
| [incremental-migration.md](incremental-migration.md) | 保留现有数据的增量迁移脚本执行说明 |

## 安全、日志和审计

| 文档 | 用途 |
| --- | --- |
| [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md) | RBAC 权限、结构化日志和操作审计说明 |
| [trace-context-audit.md](trace-context-audit.md) | `traceId`、`operationId`、`loginSessionId` 链路追踪和会话审计说明 |

## 后续扩展

| 文档 | 用途 |
| --- | --- |
| [spring-ai.md](spring-ai.md) | 已落地的 Spring AI 只读助手、业务查询、临时只读 SQL、AI 展示安全、RAG 文档索引、长期记忆和 Qdrant 接入说明 |
| [ai-assistant-design.md](ai-assistant-design.md) | AI 助手整体设计、现有组件编排、权限审计、只读 SQL 边界和后续演进方案 |

## 阅读建议

- 新开发者先看“新人接入”和“系统设计”，搭环境前先确认 [environment-versions.md](environment-versions.md)，前端开发者还应重点阅读 [frontend.md](frontend.md)，再按负责模块阅读接口和数据库文档。
- 做后端业务开发前必须阅读 [development-guide.md](development-guide.md) 和 [mybatis.md](mybatis.md)。
- 做权限、日志、审计相关功能前必须阅读 [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md) 和 [trace-context-audit.md](trace-context-audit.md)。
- 使用或继续开发 AI 助手前先阅读 [spring-ai.md](spring-ai.md)，再阅读 [ai-assistant-design.md](ai-assistant-design.md)；涉及 Python Agent 或 SSE 事件时同步阅读 [python-ai-service.md](python-ai-service.md)，涉及前端聊天气泡、工具日志、结果卡片或抽屉展示时同步阅读 [frontend.md](frontend.md) 的 AI 展示安全章节，涉及 AI 生成临时查询时还必须阅读 [mybatis.md](mybatis.md) 中的例外边界，涉及历史会话时确认 `ai_conversation` / `ai_conversation_message` 增量脚本，涉及多轮追问分页时确认 `ai_query_cursor` 增量脚本，涉及长期记忆时还要确认 Qdrant 版本和 `ai_user_memory` 相关增量脚本；涉及系统文档 RAG 时还要确认 `ai_document_index`、`APP_AI_RAG_*` 配置和 `logistics_docs` 集合。

## 文档维护约定

- 所有 Markdown 文档使用 UTF-8 编码；如果 PowerShell 控制台显示乱码，以编辑器、Gitee 页面或 UTF-8 读取工具为准。
- 每个专题文档底部保留“相关文档”小节，至少回链到本索引，避免单个文档孤立。
- 新增接口、表结构、权限码、中间件链路或运维脚本时，需要同步更新对应专题文档和本索引。
- 已有 MySQL 数据库不通过应用启动自动重建；涉及字段变更时优先新增 `scripts/sql/*incremental*.sql`，并在 [incremental-migration.md](incremental-migration.md) 中登记。
- 文档示例不写真实生产密码、token、accessToken；本地默认值以配置文件和环境变量为准。

## 最近安全增强

- 客户角色数据隔离已覆盖通用模块列表、看板、订单详情、近期订单和 ES 订单搜索；客户账号缺少 `customer_id` 绑定时禁止查询业务数据。
- 新写入的手机号等敏感字段使用 `ENCGCM:` AES-GCM 密文，旧 `ENC:` 密文继续兼容；`sys_user.mobile_hash` 用于不可逆查重。
- 通用用户管理写入密码时自动 BCrypt，空密码不会覆盖旧密码。
- 通用删除只允许逻辑删除，缺少 `deleted` 字段时要求先执行增量迁移。
- 练习和中间件示例接口已接入 Sa-Token 权限映射，不再只是登录即可访问。

相关说明见 [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md)、[logistics-database.md](logistics-database.md)、[incremental-migration.md](incremental-migration.md) 和 [configuration.md](configuration.md)。

## AI Prompt 治理补充

AI 助手 Prompt 已纳入模板化治理。继续开发 AI 能力前，需要同时阅读 [Spring AI 接入说明](spring-ai.md)、[AI 助手设计文档](ai-assistant-design.md)、[MyBatis 使用规范](mybatis.md) 和 [数据库增量迁移说明](incremental-migration.md)。涉及新增 Prompt 时，需要同步维护 `ai_prompt_template` 默认模板、输出校验、测试用例和文档交叉引用。

AI 展示安全已统一为 Java 展示字段、Python SSE 透传和前端 sanitizer 三层兜底。继续开发 Tool Calling、临时 SQL、结果表格或抽屉分页时，必须确认用户界面不展示内部工具名、SQL、snake_case 字段名、权限码、异常堆栈或未知字段原文；相关说明见 [Spring AI 接入说明](spring-ai.md)、[Python AI 服务开发指南](python-ai-service.md)、[前端开发说明](frontend.md)、[MyBatis 使用规范](mybatis.md) 和 [链路追踪与会话审计说明](trace-context-audit.md)。

## AI 长期记忆治理与幻觉处理补充

AI 长期记忆已增加候选、冲突、疑似幻觉、替代和画像编译机制。继续开发记忆、RAG、Tool Calling 或临时 SQL 能力前，需要优先阅读：

- [Spring AI 接入说明](spring-ai.md)：运行链路、Prompt 模板、长期记忆、Qdrant、幻觉护栏和临时 SQL 说明。
- [AI 助手设计文档](ai-assistant-design.md)：意图边界、长期记忆治理、权限隔离和风险控制。
- [数据库设计说明](logistics-database.md)：`ai_user_memory`、`ai_user_profile`、`ai_memory_event` 字段含义。
- [数据库增量迁移说明](incremental-migration.md)：`20260622_incremental_ai_memory_governance.sql` 执行范围。
- [链路追踪与会话审计说明](trace-context-audit.md)：记忆治理事件、AI 回答修正和全链路排查方式。
