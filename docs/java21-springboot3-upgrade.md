# Java 21 与 Spring Boot 3 升级记录

本文记录项目从 Java 8 / Spring Boot 2.7.x 升级到 Java 21 / Spring Boot 3.x 的范围、版本、验证结果和后续注意事项。当前运行基线已经演进到 Spring Boot 3.5.x。新开发者搭建环境前应先阅读 [环境与中间件版本清单](environment-versions.md) 和 [本地开发指南](local-development.md)。

## 升级范围

- 后端运行基线升级为 Java 21。
- Spring Boot 升级为 3.5.14。
- Spring Cloud 升级为 2025.0.2。
- Spring Cloud Alibaba 升级为 2025.0.0.0。
- Spring AI BOM 接入 1.1.7，当前只用于只读 AI 助手和日志排障。
- Sa-Token 切换为 Boot3 starter，保留现有登录、会话、权限和按钮控制逻辑。
- MyBatis Spring Boot Starter 升级为 3.0.4，继续使用 Mapper 接口 + XML 维护 SQL。
- Servlet、Validation、PostConstruct 相关包迁移到 Jakarta 命名空间。
- Docker 应用镜像切换到 Temurin 21。

本轮不拆分微服务，不清空数据库，不改变现有物流业务接口 URL、请求参数和响应结构。Spring AI 接入只新增 `/ai/**` 只读能力，未开放写操作。

## 本地环境

当前本机已验证：

| 工具 | 当前状态 |
| --- | --- |
| Java | 本地 JDK 21，推荐 `F:\Development\Java\temurin-21` |
| Maven | `F:\Development\Tools\apache-maven-3.9.16`，运行在 Java 21 上 |
| Node.js | 24.16.0 |
| npm | 11.13.0 |
| Docker CLI | 当前命令行未检测到 `docker` 命令 |

说明：

- Java 8 目录保留，不删除，避免影响其它旧项目。
- 当前项目优先使用本地 JDK 21，减少 IDEA 自带 JBR 与命令行环境不一致带来的问题。
- Java 8 仍可保留在本机，用于其它旧项目；本项目 Maven、IDEA Project SDK 和 Maven Runner 均应使用 Java 21。

## 兼容适配点

### Jakarta 命名空间

Spring Boot 3 基于 Jakarta EE，以下包已迁移：

```text
javax.servlet.*     -> jakarta.servlet.*
javax.validation.*  -> jakarta.validation.*
javax.annotation.*  -> jakarta.annotation.*
```

以下 JDK 自带包保持不变：

```text
javax.sql.*
javax.crypto.*
javax.imageio.*
```

### Elasticsearch 查询 API

Spring Data Elasticsearch 5 不再使用旧的 `NativeSearchQueryBuilder` 和 `org.elasticsearch.index.query.QueryBuilders`。订单搜索服务已改为 `NativeQuery` + Elasticsearch Java Client lambda 查询写法。

业务行为保持不变：

- 支持按订单号、客户名称、收货地址、货物名称搜索。
- 客户账号仍按 `customerId` 做数据范围过滤。
- Elasticsearch 异常仍按辅助搜索链路降级，不阻断主业务。

## 验证结果

已完成验证：

```bash
mvn test
mvn -DskipTests package
java -jar target/demo-springboot-1.0-SNAPSHOT.jar --spring.profiles.active=h2 --server.port=18080
```

验证结论：

- Java 21 编译通过。
- 单元测试通过，包含权限、日志、链路追踪、RabbitMQ 事件上下文和 AI 权限映射相关测试。
- Spring Boot 可执行 Jar 打包成功。
- H2 profile 启动成功，`GET /actuator/health` 返回 `UP`。
- 结构化日志、操作日志、权限服务、脱敏工具、RabbitMQ 事件上下文相关测试保持通过。

未完成验证：

- Docker 镜像构建未验证，因为当前命令行没有检测到 `docker` 命令。
- 本地完整中间件联调需要在 MySQL、Redis、RabbitMQ、Elasticsearch、Nacos、Sentinel、XXL-Job 启动后按业务流程再执行。

## 后续验证建议

升级后第一次本地联调建议按顺序验证：

1. `java -version` 和 `mvn -version` 均显示 Java 21。
2. 后端使用本地 MySQL 启动成功。
3. `GET /actuator/health` 返回 `UP`。
4. admin、客户、司机、审计员、调度等角色可正常登录。
5. 菜单、按钮和接口权限保持一致。
6. 运单创建、订单搜索、物流轨迹、异常处理、费用结算、操作日志可正常使用。
7. Redis 缓存、RabbitMQ 事件、Elasticsearch 搜索、Sentinel 兜底和 XXL-Job 注册按需验证。
8. AI 助手入口在管理员账号下可见；未配置模型密钥时 `/ai/chat` 返回中文兜底提示，配置密钥后再验证真实模型回答。

## 相关文档

- [项目文档索引](README.md)
- [环境与中间件版本清单](environment-versions.md)
- [本地开发指南](local-development.md)
- [配置说明](configuration.md)
- [Spring AI 接入说明](spring-ai.md)
- [MyBatis 使用说明](mybatis.md)
- [链路追踪与会话审计说明](trace-context-audit.md)
