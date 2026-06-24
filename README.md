# yifei-ai-logistics-system

这是一个以物流管理系统为业务场景的 Spring Boot + Vue3 + Python (FastAPI) 前后端分离练习项目。Java 负责业务系统与数据安全网关，Python 负责 AI 推理引擎。项目已经接入 Nacos、Sentinel、Elasticsearch、Redis、RabbitMQ、MyBatis、Sa-Token、Spring AI、Qdrant 和布隆过滤器，重点用于练习”业务流程闭环 + 中间件落地 + 权限控制 + 前端管理台 + AI 助手（Java+Python 混合架构）”。

## 技术栈

| 层 | 技术 |
|---|---|
| **业务后端** | Java 21, Spring Boot 3.5.14, Spring Cloud 2025.0.2 |
| **AI 引擎** | Python 3.12, FastAPI, uv (包管理) |
| **AI 模型** | DeepSeek v4-flash (OpenAI 兼容), Ollama bge-m3 (Embedding) |
| **注册/配置** | Nacos Discovery / Config |
| **流量控制** | Sentinel |
| **搜索** | Spring Data Elasticsearch |
| **缓存/过滤** | Redis + Guava Bloom Filter |
| **消息队列** | RabbitMQ |
| **持久层** | MyBatis + MySQL 8.4 / H2 |
| **向量库** | Qdrant 1.18.2 |
| **鉴权** | Sa-Token (RBAC) |
| **链路追踪** | OpenTelemetry → Jaeger |
| **前端** | Vue 3 + Element Plus |
| **构建** | Maven + uv

## 已完成功能

- Sa-Token 登录认证、会话校验、RBAC 菜单权限和按钮权限。
- 物流订单创建、查询、管理页分页、模糊查询和时间范围查询。
- 运单支持部分字段后补：货物名称、重量、体积、计划时间等可暂缺，更新接口只修改明确传入字段。
- 客户、运单、调度、任务、轨迹、司机、车辆、异常、费用、用户、角色等管理页基础 CRUD。
- Redis 订单详情缓存，订单查询时回填缓存。
- BloomFilter 参与订单号查询预检，降低不存在单号反复查询的风险。
- RabbitMQ 订单创建事件，消费后自动初始化物流轨迹。
- Elasticsearch 订单搜索索引写入和搜索接口，支持按订单号、客户、地址、货物名检索。
- Sentinel 保护订单创建和订单查询等高频接口。
- 操作日志记录（含变更摘要、请求参数、错误消息）。
- 日志安全：userId/IP 脱敏、手机号/邮箱/身份证号清洗、敏感参数过滤、异常消息安全化。
- Excel 导出和客户资料导入。
- Spring AI 只读助手，支持系统文档问答、短期会话、操作日志排障分析、普通白名单业务查询和受控临时 SELECT 统计查询；具备多层次幻觉防护（回答幻觉检测+记忆幻觉多维度风险评估），整个检测→判断→修复过程对用户无感；未配置模型密钥时应用仍可启动并返回本地兜底提示。
- AI 模块正在迁移至 Python FastAPI（Java+Python 混合架构），详见 [ADR 0001](docs/adr/0001-java-python-hybrid-architecture.md) 和 [Python AI 服务开发指南](docs/python-ai-service.md)。

## 快速开始

确认本地已经启动 Nacos、Sentinel Dashboard、Elasticsearch、Redis、RabbitMQ 和 MySQL，然后运行：

```bash
# 1. 启动 Python AI 服务
cd ai-service
uv sync
set SPRING_AI_OPENAI_API_KEY=sk-***
uv run uvicorn ai_service.main:app --host 127.0.0.1 --port 8001

# 2. 启动 Java 后端（另一个终端）
cd ..
mvn spring-boot:run -Dspring-boot.run.arguments="--app.ai.python.enabled=true --app.encrypt.enabled=false"

# 3. 启动前端（可选，第三个终端）
cd frontend && npm run dev
```

应用默认地址：

```text
Java:  http://127.0.0.1:8080
Python: http://127.0.0.1:8001 (仅内部调用)
前端:  http://127.0.0.1:5173
```

默认管理员：

```text
账号：admin
密码：以 src/main/resources/application.yml 中 APP_ADMIN_PASSWORD 默认值为准，可通过环境变量覆盖
```

基础设施状态接口：

```text
GET http://127.0.0.1:8080/infra/status
```

## 本机数据库

当前本机 MySQL 已可用：

```text
MySQL 服务: MySQL84
版本: 8.4.9
地址: 127.0.0.1:3306
物流数据库: logistics_management
练习数据库: practice_dev
默认用户: root
默认密码: 空
```

应用默认不会自动执行 MySQL 的 `schema.sql` 或 `data.sql`，避免覆盖本地已有数据。现有库升级请手动执行：

```bash
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/20260525_incremental_base_fields_and_indexes.sql
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/20260603_incremental_security_hardening.sql
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/20260610_incremental_ai_memory_lifecycle.sql
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/20260610_incremental_ai_menu_for_all_roles.sql
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/20260621_incremental_ai_prompt_template.sql
```

AI 相关增量脚本只补齐长期记忆生命周期字段、AI 菜单和最小必要权限，不清库、不重建表；其中 `ai:log:analyze` 仅保留给管理员、审计只读员和财务主管这类审计角色。

如果不想依赖 MySQL，可以使用 H2 内存库：

```bash
set SPRING_PROFILES_ACTIVE=h2
mvn spring-boot:run
```

## 项目结构

```text
├── ai-service/                   ← Python AI 服务（FastAPI）
│   ├── src/ai_service/           ← 源代码：模型网关、Agent、RAG、记忆
│   ├── prompts/                  ← Prompt 模板（Git 版本化）
│   ├── config/                   ← Provider 注册表
│   └── tests/                    ← 单元测试（pytest）
├── frontend/                     ← Vue 3 前端
├── src/main/java/jimmy
│   ├── ai/                       ← AI 模块（Java 侧：Tool Executor、会话、记忆治理）
│   ├── logistics/                ← 物流业务
│   ├── auth/                     ← 认证鉴权
│   ├── system/                   ← 系统管理
│   └── common/                   ← 公共组件
├── docs/                         ← 项目文档
│   └── adr/                      ← 架构决策记录
└── scripts/sql/                  ← 数据库增量脚本
```

## 文档

- [项目文档索引](docs/README.md) — 所有文档入口和推荐阅读顺序
- [新手快速上手指南](docs/getting-started.md) — 10 分钟跑通项目
- [开发规范与约定](docs/development-guide.md) — 编码规范、Git 工作流、代码审查清单
- [项目结构说明](docs/architecture.md)
- [配置说明](docs/configuration.md)
- [本地开发指南](docs/local-development.md)
- [环境与中间件版本清单](docs/environment-versions.md)
- [Java 21 与 Spring Boot 3 升级记录](docs/java21-springboot3-upgrade.md)
- [MyBatis 使用说明](docs/mybatis.md)
- [前端新人接入手册](docs/frontend.md)
- [需求匹配说明](docs/requirements-mapping.md)
- [物流接口文档](docs/logistics-api.md)
- [认证接口文档](docs/auth-api.md)
- [权限配置接口说明](docs/role-permission-api.md)
- [用户接口文档](docs/user-api.md)
- [物流数据库说明](docs/logistics-database.md)
- [数据库增量迁移说明](docs/incremental-migration.md)
- [权限、结构化日志与操作审计说明](docs/logistics-rbac-structured-log.md)
- [链路追踪与会话审计标识说明](docs/trace-context-audit.md)
- [Spring AI 接入说明](docs/spring-ai.md) — 当前 AI 接口、Nacos 配置、只读业务查询和临时 SQL 安全边界
- [AI 助手设计文档](docs/ai-assistant-design.md) — AI 助手整体架构、权限审计和后续演进路线
- [ADR 0001 — Java+Python 混合架构](docs/adr/0001-java-python-hybrid-architecture.md) — AI 迁移架构决策
- [Python AI 服务开发指南](docs/python-ai-service.md) — Python 服务启动、测试、API 端点

## 常用接口

```text
POST /auth/login
GET  /auth/session
POST /auth/logout
GET  /logistics/dashboard
GET  /logistics/modules/{module}?page=1&pageSize=20&keyword=上海
POST /logistics/modules/{module}
POST /logistics/modules/{module}/{id}
POST /logistics/modules/{module}/{id}/delete
POST /logistics/orders
GET  /logistics/orders/{orderNo}
GET  /logistics/orders/search?keyword=上海&page=1&pageSize=20
GET  /logistics/excel/export/{module}
POST /logistics/excel/import/customers
POST /ai/chat
POST /ai/logs/analyze
GET  /ai/conversations
GET  /ai/conversations/{id}
GET  /infra/status
GET  /actuator/health
```

## 常用环境变量

```text
SERVER_PORT=8080
NACOS_SERVER_ADDR=127.0.0.1:8848
SENTINEL_DASHBOARD=127.0.0.1:8858
ELASTICSEARCH_URIS=http://127.0.0.1:9200
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/logistics_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=
SPRING_AI_OPENAI_API_KEY=sk-***                    # DeepSeek API Key（必配，从 Nacos 获取）
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-v4-flash
APP_AI_PYTHON_ENABLED=true                         # 启用 Python AI 服务代理
APP_AI_PYTHON_BASE_URL=http://127.0.0.1:8001
APP_ENCRYPT_ENABLED=false                          # 本地开发关闭加密
```
