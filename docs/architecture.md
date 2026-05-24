# 项目结构说明

当前项目是一个 Spring Boot 2.7.x 单体应用骨架，已经预留 Nacos、Sentinel、Elasticsearch 的基础接入。

```text
practice-project-about-develop
├── pom.xml
├── docs
│   ├── architecture.md
│   ├── configuration.md
│   └── local-development.md
└── src
    └── main
        ├── java
        │   └── jimmy
        │       ├── DemoApplication.java
        │       ├── controller
        │       │   └── InfrastructureController.java
        │       ├── model
        │       │   └── InfrastructureStatus.java
        │       └── service
        │           └── InfrastructureStatusService.java
        └── resources
            ├── application.yml
            └── bootstrap.yml
```

## 分层约定

- `controller`: 对外 HTTP 接口，只处理请求参数和响应。
- `service`: 业务逻辑和第三方组件调用入口。
- `model`: DTO、VO、请求和响应模型。
- `config`: 后续放置自定义 Bean、拦截器、序列化、安全配置等。
- `resources`: Spring Boot 配置文件。

## 已接入组件

- Nacos Discovery: 服务注册与发现。
- Nacos Config: 配置中心，使用 `bootstrap.yml` 加载。
- Sentinel: 流量控制、熔断降级和 Dashboard 上报。
- Elasticsearch: Spring Data Elasticsearch 客户端和 Repository 支持。
- Redis: 本地缓存、Key-Value 操作和后续分布式能力预留。
- RabbitMQ: 消息发送、交换机、队列和绑定配置。
- Bloom Filter: 基于 Guava 的布隆过滤器框架，兼容 Java 8。

## 示例接口

- `GET /infra/status`: 查看基础设施配置是否被加载。
- `GET /infra/nacos/services`: 查看 Nacos 中发现到的服务列表。
- `GET /infra/nacos/instances?serviceId=xxx`: 查看指定服务实例。
- `GET /infra/sentinel/ping`: Sentinel 资源示例接口。
- `GET /infra/elasticsearch/client`: 查看 Elasticsearch 客户端 Bean 信息。
- `GET /infra/redis/client`: 验证 Redis 客户端连接。
- `GET /infra/rabbitmq/client`: 查看 RabbitMQ 连接配置。
- `POST /bloom-filter/items?value=xxx`: 写入布隆过滤器。
- `GET /bloom-filter/items?value=xxx`: 判断值是否可能存在。
- `POST /rabbitmq/messages?message=hello`: 发送 RabbitMQ 示例消息。
