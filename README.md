# practice-project-about-develop

这是一个用于练习 Spring Boot 后端开发的项目。当前项目已补充 Nacos、Sentinel、Elasticsearch 相关依赖、基础配置和验证接口，适合作为微服务基础设施学习与二次开发骨架。

## 技术栈

- Java 8
- Spring Boot 2.7.18
- Spring Cloud 2021.0.9
- Spring Cloud Alibaba 2021.0.6.0
- Nacos Discovery / Config
- Sentinel
- Spring Data Elasticsearch
- Spring Data Redis
- RabbitMQ
- Guava Bloom Filter
- Maven

## 快速开始

确认本地已经启动 Nacos、Sentinel Dashboard 和 Elasticsearch，然后运行：

```bash
mvn spring-boot:run
```

应用默认端口：

```text
http://127.0.0.1:8080
```

基础设施状态接口：

```text
GET http://127.0.0.1:8080/infra/status
```

## 项目结构

```text
src/main/java/jimmy
├── DemoApplication.java
├── controller
├── model
└── service
```

配置文件：

```text
src/main/resources/bootstrap.yml
src/main/resources/application.yml
```

更多说明见：

- [项目结构说明](docs/architecture.md)
- [配置说明](docs/configuration.md)
- [本地开发指南](docs/local-development.md)

## 常用接口

```text
GET /infra/status
GET /infra/nacos/services
GET /infra/nacos/instances?serviceId=practice-project-about-develop
GET /infra/sentinel/ping
GET /infra/elasticsearch/client
GET /actuator/health
```

## 环境变量

项目提供了本地默认值，也可以通过环境变量覆盖：

```text
NACOS_SERVER_ADDR=127.0.0.1:8848
SENTINEL_DASHBOARD=127.0.0.1:8858
ELASTICSEARCH_URIS=http://127.0.0.1:9200
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
SERVER_PORT=8080
```

完整配置见 [配置说明](docs/configuration.md)。
