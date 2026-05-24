# practice-project-about-develop

这是一个用于练习 Spring Boot 后端开发的项目。当前项目已经接入 Nacos、Sentinel、Elasticsearch、Redis、RabbitMQ、MyBatis 和布隆过滤器，适合作为后端基础设施学习与二次开发骨架。

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
- MyBatis
- MySQL 8.4 / H2
- Guava Bloom Filter
- Maven

## 快速开始

确认本地已经启动 Nacos、Sentinel Dashboard、Elasticsearch、Redis、RabbitMQ 和 MySQL，然后运行：

```bash
mvn spring-boot:run
```

应用默认地址：

```text
http://127.0.0.1:8080
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

如果不想依赖 MySQL，可以使用 H2 内存库：

```bash
set SPRING_PROFILES_ACTIVE=h2
mvn spring-boot:run
```

## 项目结构

```text
src/main/java/jimmy
├── DemoApplication.java
├── config
├── controller
├── entity
├── mapper
├── model
└── service
```

配置文件：

```text
src/main/resources/bootstrap.yml
src/main/resources/application.yml
src/main/resources/application-h2.yml
src/main/resources/mapper/DemoUserMapper.xml
src/main/resources/schema.sql
src/main/resources/data.sql
```

## 文档

- [项目结构说明](docs/architecture.md)
- [配置说明](docs/configuration.md)
- [本地开发指南](docs/local-development.md)
- [MyBatis 使用说明](docs/mybatis.md)

## 常用接口

```text
GET  /infra/status
GET  /infra/nacos/services
GET  /infra/sentinel/ping
GET  /infra/elasticsearch/client
GET  /infra/redis/client
GET  /infra/rabbitmq/client
GET  /demo-users
GET  /demo-users/detail?id=1
POST /demo-users?username=test&displayName=Test
POST /bloom-filter/items?value=demo
GET  /bloom-filter/items?value=demo
POST /rabbitmq/messages?message=hello
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
```
