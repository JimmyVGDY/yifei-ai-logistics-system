# practice-project-about-develop

这是一个以物流管理系统为业务场景的 Spring Boot + Vue3 前后端分离练习项目。项目已经接入 Nacos、Sentinel、Elasticsearch、Redis、RabbitMQ、MyBatis、Sa-Token 和布隆过滤器，重点用于练习“业务流程闭环 + 中间件落地 + 权限控制 + 前端管理台”。

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
- Sa-Token
- Vue 3
- Element Plus
- Maven

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

## 快速开始

确认本地已经启动 Nacos、Sentinel Dashboard、Elasticsearch、Redis、RabbitMQ 和 MySQL，然后运行：

```bash
mvn spring-boot:run
```

应用默认地址：

```text
http://127.0.0.1:8080
```

前端默认地址：

```text
http://127.0.0.1:5173
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
├── common
├── config
├── controller
├── entity
├── logistics
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

- [项目文档索引](docs/README.md) — 所有文档入口和推荐阅读顺序
- [新手快速上手指南](docs/getting-started.md) — 10 分钟跑通项目
- [开发规范与约定](docs/development-guide.md) — 编码规范、Git 工作流、代码审查清单
- [项目结构说明](docs/architecture.md)
- [配置说明](docs/configuration.md)
- [本地开发指南](docs/local-development.md)
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
- [AI 助手设计文档](docs/ai-assistant-design.md)
- [认证接口文档](docs/auth-api.md)
- [用户接口文档](docs/user-api.md)
- [角色权限接口文档](docs/role-permission-api.md)
- [数据库增量迁移说明](docs/incremental-migration.md)
- [物流数据库说明](docs/logistics-database.md)
- [权限和结构化日志说明](docs/logistics-rbac-structured-log.md)
- [链路追踪与会话审计](docs/trace-context-audit.md)

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
```
