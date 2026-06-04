# 环境与中间件版本清单

本文记录物流管理系统本地开发、Docker 编排和前端开发需要使用的主要组件版本。新开发者搭环境时优先按“本地推荐版本”安装；如果使用 Docker Compose，则以 `docker-compose.yml` 中的镜像版本为准。

## 1. 版本选择原则

- 后端必须兼容 Java 8，避免引入只支持 Java 11+ 或 Java 17+ 的依赖版本。
- 本地 Windows 环境优先复用当前项目已验证的版本，减少“我这里能跑、你那里跑不了”的差异。
- Docker Compose 版本用于容器化部署和统一联调，可能与本机手动安装版本不同，但需要保持协议兼容。
- 新增或升级中间件时，需要同步更新本文、[本地开发环境说明](local-development.md)、[配置说明](configuration.md) 和相关启动脚本。

## 2. 基础开发工具

| 组件 | 本地推荐版本 | 最低/兼容范围 | 是否必需 | 说明 |
| --- | --- | --- | --- | --- |
| JDK | Java 8，推荐 Zulu 8 / Corretto 8 | Java 8 | 必需 | 项目 `pom.xml` 使用 `java.version=1.8`，不要直接升级到 Java 17。 |
| Maven | 3.8.x | 3.6+ | 必需 | 用于后端依赖下载、测试和打包。 |
| Node.js | 18 LTS | 16+ | 前端开发必需 | Vite 6 和 Vue 3 开发建议使用 Node 18 LTS；已安装 Node 16 时也可运行。 |
| npm | 随 Node 18 附带版本 | 8+ | 前端开发必需 | 进入 `frontend/` 后执行 `npm install`。 |
| Git | 2.40+ | 2.x | 必需 | VS Code 工作区已配置 Git 相关能力。 |
| VS Code | 当前稳定版 | 当前稳定版 | 前端推荐 | 前端开发建议打开仓库根目录的 `.code-workspace`。 |
| IntelliJ IDEA | 2023+ | 支持 Java 8/Maven 的版本 | 后端推荐 | 后端开发推荐使用 IDEA。 |

## 3. 本地 Windows 中间件推荐版本

| 组件 | 推荐版本 | 本机参考路径 | 端口 | 是否必需 | 用途 |
| --- | --- | --- | --- | --- | --- |
| MySQL | 8.4.9 | `F:\Development\Database\MySQL\Server-8.4.9` | 3306 | 必需 | 主业务库 `logistics_management`。 |
| Redis | 5.0.14.1 | `F:\Development\Middleware\redis\redis-5.0.14.1` | 6379 | 建议必启 | 订单缓存、验证码缓存。 |
| RabbitMQ | 4.1.8 | `F:\Development\Middleware\rabbitmq\rabbitmq_server-4.1.8` | 5672 / 15672 | 完整链路必启 | 订单创建事件和演示消息。 |
| Elasticsearch | 7.17.29 | `F:\Development\Middleware\elasticsearch\elasticsearch-7.17.29` | 9200 | 搜索链路必启 | 订单索引和多字段搜索。 |
| Nacos | 2.4.3 | `F:\Development\Middleware\nacos\nacos-2.4.3` | 8848 / 9848 / 9849 | 建议启动 | 注册中心和配置中心。 |
| Sentinel Dashboard | 1.8.8 | `F:\Development\Middleware\sentinel\sentinel-dashboard-1.8.8.jar` | 8858 | 建议启动 | 限流熔断观察和配置。 |
| XXL-Job Admin | 2.4.x | `F:\Development\Middleware\xxl-job` | 8081 | 可选 | 定时任务调度中心；默认本地可关闭。 |

本地启动最小组合：

```text
MySQL + 后端 Spring Boot + 前端 Vite
```

推荐日常开发组合：

```text
MySQL + Redis + RabbitMQ + Elasticsearch + 后端 Spring Boot + 前端 Vite
```

完整中间件联调组合：

```text
MySQL + Redis + RabbitMQ + Elasticsearch + Nacos + Sentinel + XXL-Job + 后端 + 前端
```

## 4. Docker Compose 镜像版本

| 服务 | 镜像版本 | 端口 | 说明 |
| --- | --- | --- | --- |
| MySQL | `mysql:8.0` | 3306 | 容器开发库，首次启动会执行初始化 SQL。 |
| Redis | `redis:7-alpine` | 6379 | 容器缓存服务。 |
| RabbitMQ | `rabbitmq:3-management-alpine` | 5672 / 15672 | 含管理控制台。 |
| Elasticsearch | `elasticsearch:7.17.0` | 9200 | 单节点搜索服务。 |
| Nacos | `nacos/nacos-server:v2.2.3` | 8848 | 单机模式。 |
| Sentinel | 自定义镜像，Dashboard 1.8.8 | 8858 | 由 `docker/sentinel/Dockerfile` 构建。 |
| XXL-Job Admin | `xuxueli/xxl-job-admin:2.4.0` | 8081 | 调度中心。 |
| Prometheus | `prom/prometheus:v2.45.0` | 9090 | 指标采集，可选。 |
| Grafana | `grafana/grafana:9.5.0` | 3000 | 监控面板，可选。 |
| Filebeat | `elastic/filebeat:7.17.0` | 无 | 日志采集，可选。 |
| Kibana | `kibana:7.17.0` | 5601 | 日志检索，可选。 |

Docker Compose 一键启动：

```bash
docker compose up -d
```

容器版本和本地 Windows 版本不要求完全一致，但需要保持大版本兼容。例如 Elasticsearch 推荐保持 7.x，避免直接升级到 8.x 导致客户端和安全配置不兼容。

## 5. 后端核心依赖版本

| 组件 | 当前版本 | 来源 | 说明 |
| --- | --- | --- | --- |
| Spring Boot | 2.7.18 | `spring-boot-starter-parent` | Java 8 兼容的 Spring Boot 2.x 版本。 |
| Spring Cloud | 2021.0.9 | `pom.xml` BOM | 与 Spring Boot 2.7.x 配套。 |
| Spring Cloud Alibaba | 2021.0.6.0 | `pom.xml` BOM | Nacos、Sentinel 相关依赖由此统一管理。 |
| Sa-Token | 1.45.0 | `pom.xml` | 登录认证、权限和会话。 |
| MyBatis Spring Boot Starter | 2.3.1 | `pom.xml` | Mapper XML 数据访问。 |
| XXL-Job Core | 2.4.0 | `pom.xml` | 定时任务执行器端。 |
| Lombok | 1.18.34 | `pom.xml` | 简化部分模型代码。 |
| Logstash Logback Encoder | 6.6 | `pom.xml` | 兼容 Logback 1.2.x 的结构化日志编码器。 |
| Guava | 32.1.3-jre | `pom.xml` | 本地内存布隆过滤器。 |

注意：`logstash-logback-encoder` 不要随意升级到 7.x；Spring Boot 2.7 默认使用 Logback 1.2.x，7.x 版本会调用 Logback 1.3+ 才有的方法，可能导致启动时报 `ILoggingEvent.getInstant()` 不存在。

## 6. 前端依赖版本

| 组件 | 当前版本范围 | 说明 |
| --- | --- | --- |
| Vue | `^3.5.13` | 前端页面框架。 |
| Vue Router | `^4.5.0` | 路由和页面权限守卫。 |
| Element Plus | `^2.9.3` | 后台 UI 组件库。 |
| Element Plus Icons | `^2.3.1` | 图标组件。 |
| Axios | `^1.7.9` | HTTP 请求库。 |
| Vite | `^6.0.7` | 本地开发和构建工具。 |
| TypeScript | `^5.7.2` | 当前主要用于工具链支持，页面代码以 JavaScript/Vue SFC 为主。 |

前端实际安装版本以 `frontend/package-lock.json` 为准。新增前端依赖时，需要提交 `package.json` 和 `package-lock.json`。

## 7. 版本升级注意事项

- Java、Spring Boot、Spring Cloud、Spring Cloud Alibaba 需要成组升级，不能只升其中一个。
- Elasticsearch 不建议从 7.x 直接升到 8.x，除非同步调整安全配置、客户端兼容和索引初始化。
- RabbitMQ 本地 4.x 和 Docker 3.x 当前只使用基础 AMQP 能力，能兼容；如果后续使用高级特性，需要重新评估。
- Redis 当前只使用 String/Object 缓存能力，5.x 到 7.x 均可；如使用 Redis Stream、ACL 或 RedisBloom，需要重新注明版本要求。
- Nacos 2.x 需要开放 8848、9848、9849；只开放 8848 可能导致客户端一直处于 `STARTING`。
- XXL-Job 本地默认可关闭；只有验证定时任务时才需要启动 Admin，并避免执行器端口冲突。

## 8. 相关文档

- [项目文档索引](README.md)
- [新手快速上手指南](getting-started.md)
- [本地开发环境说明](local-development.md)
- [配置说明](configuration.md)
- [前端新人接入手册](frontend.md)
- [链路追踪与会话审计说明](trace-context-audit.md)
