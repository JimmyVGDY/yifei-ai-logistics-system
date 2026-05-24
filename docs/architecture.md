# 项目结构说明

当前项目是一个 Spring Boot 2.7.x 单体应用骨架，已经预留常见后端基础设施接入。

## 目录结构

```text
practice-project-about-develop
├── pom.xml
├── docs
│   ├── architecture.md
│   ├── configuration.md
│   ├── local-development.md
│   └── mybatis.md
└── src
    └── main
        ├── java
        │   └── jimmy
        │       ├── DemoApplication.java
        │       ├── config
        │       ├── controller
        │       ├── entity
        │       ├── mapper
        │       ├── model
        │       └── service
        └── resources
            ├── application.yml
            ├── application-h2.yml
            ├── bootstrap.yml
            ├── mapper
            ├── schema.sql
            └── data.sql
```

## 分层约定

- `config`: Spring Bean、组件配置和配置属性类。
- `controller`: 对外 HTTP 接口，只处理请求参数和响应。
- `entity`: 数据库实体对象。
- `mapper`: MyBatis Mapper 接口。
- `model`: DTO、VO、请求和响应模型。
- `service`: 业务逻辑和第三方组件调用入口。
- `resources/mapper`: MyBatis XML 映射文件。

## 已接入组件

- Nacos Discovery: 服务注册与发现。
- Nacos Config: 配置中心，使用 `bootstrap.yml` 加载。
- Sentinel: 流量控制、熔断降级和 Dashboard 上报。
- Elasticsearch: Spring Data Elasticsearch 客户端和 Repository 支持。
- Redis: 本地缓存、Key-Value 操作和后续分布式能力预留。
- RabbitMQ: 消息发送、交换机、队列和绑定配置。
- MyBatis: 数据访问层框架，默认连接本机 MySQL，也可切换 H2。
- Bloom Filter: 基于 Guava 的布隆过滤器框架，兼容 Java 8。

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
