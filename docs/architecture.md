# 项目结构说明

当前项目是一个 Spring Boot 3.5.x Java + Python 混合架构应用，已经预留常见后端基础设施接入。

## 目录结构

```text
yifei-ai-logistics-system
├── pom.xml
├── Dockerfile                      ← 应用 Docker 镜像
├── docker-compose.yml              ← 全栈编排（Java + Python + 中间件）
├── .env.example                    ← 环境变量模板
├── CONTEXT-MAP.md                  ← 多上下文地图（Java + Python）
├── CONTEXT.md                      ← Java 侧架构术语表
├── docs/                           ← 项目文档
│   └── adr/                        ← 架构决策记录
├── ops/                            ← 运维脚本（Linux .sh + Windows .bat）
│   ├── start.sh / start.bat        ← 一键启动
│   ├── stop.sh / stop.bat          ← 停止
│   ├── restart.sh                  ← 重启（仅应用）
│   ├── status.sh / status.bat      ← 状态检查
│   ├── logs.sh / logs.bat          ← 日志查看
│   ├── build-check.sh / .bat       ← 编译检查
│   ├── run-local.sh / .bat         ← 本地开发启动
│   ├── prometheus.yml / alert-rules.yml ← 监控配置
│   ├── filebeat.yml                ← 日志采集配置
│   └── grafana-*.json/yml          ← 监控仪表盘
├── docker/
│   └── sentinel/                   ← Sentinel 镜像构建
├── frontend/                       ← Vue 3 前端
├── ai-service/                     ← Python AI 服务（新增）
│   ├── pyproject.toml              ←   Python 依赖（uv）
│   ├── Dockerfile
│   ├── src/ai_service/             ←   源代码
│   │   ├── main.py                 ←   FastAPI 入口 + OTel
│   │   ├── api/                    ←   HTTP 路由
│   │   ├── core/                   ←   Agent、模型网关、Prompt
│   │   ├── memory/                 ←   记忆智能
│   │   ├── rag/                    ←   文档检索
│   │   ├── validation/             ←   输出校验
│   │   └── infrastructure/         ←   Redis/Qdrant/Java 客户端
│   ├── prompts/                    ←   Prompt 模板（Git 版本化）
│   ├── config/                     ←   Provider 注册表
│   └── tests/                      ←   单元测试
└── src/main/
    ├── java/jimmy/
    │   ├── DemoApplication.java
    │   ├── common/
    │   ├── config/
    │   ├── controller/
    │   ├── service/
    │   ├── mapper/
    │   ├── model/
    │   ├── entity/
    │   └── logistics/              ← 物流业务模块
    │       ├── controller/
    │       ├── service/
    │       ├── mapper/
    │       ├── model/
    │       ├── entity/
    │       ├── job/                ← XXL-Job 定时任务
    │       ├── util/               ← 工具类
    │       ├── config/             ← 拦截器
    │       └── annotation/         ← 自定义注解
    └── resources/
        ├── application.yml         ← 默认配置
        ├── application-h2.yml      ← H2 内存库
        ├── application-prod.yml    ← 生产环境配置
        ├── bootstrap.yml           ← Nacos 配置
        └── mapper/                 ← MyBatis XML
```

## 分层约定

- `config`: Spring Bean、组件配置和配置属性类。
- `controller`: 对外 HTTP 接口，只处理请求参数和响应。
- `entity`: 数据库实体对象。
- `mapper`: MyBatis Mapper 接口。
- `model`: DTO、VO、请求和响应模型。
- `service`: 业务逻辑和第三方组件调用入口。
- `job`: XXL-Job 定时任务处理器。
- `util`: 通用工具类（含 `LogMaskUtils` 日志脱敏工具、`FieldEncryptor` 字段加密、`CrudBusinessUtils` CRUD 辅助等）。
- `resources/mapper`: MyBatis XML 映射文件。

### 关键架构组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `ModuleManifest` | `system/config/` | **新增模块的唯一入口**。定义模块码、表名、列清单和敏感标记。`StandardColumnRegistry`、`SaTokenConfig` 模块前缀映射、`ColumnFilterAdvice` 路径映射均委托于此。 |
| `PermissionEvaluator` | `ai/service/` | SSE/HTTP 双线程环境下的统一权限判断。替代此前 8 处重复的 `SseChatContext/StpUtil` 分支。 |
| `UserContextResolver` | `ai/service/` | 当前用户标识（ID/UserCode/RoleCode/LoginSessionId）解析，兼容 SSE 和 HTTP 线程。 |
| `ColumnFilterAdvice` | `common/web/` | 基于 `@ColumnScope` 注解的响应字段级过滤，按 RBAC 列权限裁剪 API 返回值。 |
| `OperationContext` | `logistics/config/` | 从 HTTP 请求中提取客户端 IP/UA/参数（脱敏后），提供 `OperationLogInterceptor` 复用。 |
| `DataScopeResolver` | `logistics/config/` | 数据行级隔离条件解析（当前仅 CUSTOMER 角色）。 |

## 架构拓扑

本系统采用 **Java + Python 混合架构**：

```
前端 (Vue3 :5173)
  │
  ▼
Java Spring Boot (:8080)    ← 唯一业务入口（BFF）
  │  鉴权 (Sa-Token) + 策略决策 (ModelPolicyDecider) + 数据安全网关
  │  SSE 代理透传
  │
  ├── HTTP :8001 (127.0.0.1) ──► Python FastAPI  ← AI 推理引擎
  │                                 模型网关 + Agent + RAG + 记忆智能
  │                                 不直接暴露给前端
  │
  └── MySQL / Redis / ES / RabbitMQ  ← 业务数据 + 会话 + 缓存
```

详细架构决策见 [ADR 0001](adr/0001-java-python-hybrid-architecture.md)、术语表见 [CONTEXT-MAP.md](../CONTEXT-MAP.md) 和 [CONTEXT.md](../CONTEXT.md)。

## 已接入组件

- **Nacos Discovery**: 服务注册与发现。
- **Nacos Config**: 配置中心，使用 `bootstrap.yml` 加载。
- **Sentinel**: 流量控制、熔断降级和 Dashboard 上报。
- **Elasticsearch**: Spring Data Elasticsearch 客户端和 Repository 支持。
- **Redis**: 本地缓存、Key-Value 操作和后续分布式能力预留。
- **RabbitMQ**: 消息发送、交换机、队列和绑定配置。
- **MyBatis**: 数据访问层框架，默认连接本机 MySQL，也可切换 H2。
- **Bloom Filter**: 基于 Guava 的布隆过滤器，提供缓存穿透防护。
- **Prometheus**: 应用指标监控，暴露 `/actuator/prometheus` 端点。
- **Filebeat**: 日志采集，将应用日志写入 Elasticsearch。
- **Grafana**: 监控仪表盘可视化，预置 JVM/HTTP/GC/错误率面板。
- **Kibana**: 日志检索与可视化。
- **XXL-Job**: 分布式定时任务调度，复用为 Python AI 异步任务的调度器。
- **Docker Compose**: 全栈服务编排。
- **Python AI Service (FastAPI)**: Python 3.12 + FastAPI，AI 推理引擎（模型网关、Agent 编排、RAG、记忆智能），只监听 `127.0.0.1:8001`。
- **Qdrant**: 向量数据库，归 Python 独占管理，Java 通过 HTTP API 间接访问。
- **Ollama**: 本地 Embedding 模型服务 (bge-m3)，归 Python 独占调用。
- **Jaeger**: OpenTelemetry 分布式链路追踪（OTLP :4318，UI :16686），Java + Python 双端自动埋点。
- **uv**: Python 包管理器，替代 pip。

## 示例接口

- `GET /infra/status`: 查看基础设施配置是否被加载。
- `GET /infra/nacos/services`: 查看 Nacos 中发现到的服务列表。
- `GET /infra/nacos/instances?serviceId=xxx`: 查看指定服务实例。
- `GET /infra/sentinel/ping`: Sentinel 资源示例接口。
- `GET /infra/elasticsearch/client`: 查看 Elasticsearch 客户端 Bean 信息。
- `GET /infra/redis/client`: 验证 Redis 客户端连接。
- `GET /infra/rabbitmq/client`: 查看 RabbitMQ 连接配置。
- `GET /demo-users`: 查询 MyBatis 示例用户列表。
- `POST /demo-users?username=test&displayName=Test`: 新增 MyBatis 示例用户。
- `POST /bloom-filter/items?value=xxx`: 写入布隆过滤器。
- `GET /bloom-filter/items?value=xxx`: 判断值是否可能存在。
- `POST /rabbitmq/messages?message=hello`: 发送 RabbitMQ 示例消息。
- `GET /actuator/prometheus`: Prometheus 指标端点。

## 相关文档

- [项目文档索引](README.md)
- [开发规范与约定](development-guide.md)
- [MyBatis 使用规范](mybatis.md)
- [前端新人接入手册](frontend.md)
- [物流接口文档](logistics-api.md)
