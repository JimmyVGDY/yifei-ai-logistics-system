# 项目结构说明

当前项目是一个 Spring Boot 3.3.x 单体应用骨架，已经预留常见后端基础设施接入。

## 目录结构

```text
practice-project-about-develop
├── pom.xml
├── Dockerfile                      ← 应用 Docker 镜像
├── docker-compose.yml              ← 13 服务全栈编排
├── .env.example                    ← 环境变量模板
├── docs/                           ← 项目文档
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
└── src/main/
    ├── java/jimmy/
    │   ├── DemoApplication.java
    │   ├── common/id/              ← 雪花 ID 生成器
    │   ├── config/                 ← Spring Bean 和中间件配置
    │   ├── controller/             ← 基础控制器
    │   ├── service/                ← 服务层
    │   ├── mapper/                 ← MyBatis 接口
    │   ├── model/                  ← DTO/VO
    │   ├── entity/                 ← 实体类
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
- **XXL-Job**: 分布式定时任务，含合同到期预警/月度报表等任务。
- **Docker Compose**: 13 服务一键编排部署。

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
