# 配置说明

项目使用 `bootstrap.yml` 管理应用名、环境和 Nacos，使用 `application.yml` 管理端口、数据源、Redis、RabbitMQ、Sentinel、Elasticsearch 和监控端点。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 当前运行环境 |
| `SERVER_PORT` | `8080` | 应用端口 |
| `SPRING_DATASOURCE_DRIVER` | `com.mysql.cj.jdbc.Driver` | JDBC 驱动 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3306/logistics_management...` | JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | `root` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | `20MB` | 单个上传文件大小上限 |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | `30MB` | 单次上传请求大小上限 |
| `APP_UPLOAD_ROOT` | `uploads` | 业务附件本地保存目录 |
| `SPRING_SQL_INIT_MODE` | `always` | SQL 初始化模式 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务地址 |
| `NACOS_USERNAME` | `nacos` | Nacos 用户名 |
| `NACOS_PASSWORD` | `nacos` | Nacos 密码 |
| `NACOS_REGISTER_ENABLED` | `true` | 是否向 Nacos 注册服务 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel Dashboard 地址 |
| `SENTINEL_API_PORT` | `8719` | Sentinel 客户端通信端口 |
| `ELASTICSEARCH_URIS` | `http://127.0.0.1:9200` | Elasticsearch 地址 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ 地址，Windows 本地 RabbitMQ 可能只监听 IPv6 `::1`，使用 `localhost` 兼容性更好 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP 端口 |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ 密码 |
| `BLOOM_FILTER_EXPECTED_INSERTIONS` | `100000` | 布隆过滤器预计写入数量 |
| `BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY` | `0.01` | 布隆过滤器误判率 |
| `APP_ADMIN_USERNAME` | `admin` | 后台管理员账号 |
| `APP_ADMIN_PASSWORD` | `xlh963311213` | 后台管理员密码 |
| `MYBATIS_SQL_LOG_LEVEL` | `info` | MyBatis Mapper 日志级别，排查 SQL 时可临时设为 `debug` |
| `SA_TOKEN_NAME` | `satoken` | Sa-Token 请求头名称 |
| `SA_TOKEN_TIMEOUT` | `86400` | 登录有效期，单位秒 |
| `SA_TOKEN_ACTIVE_TIMEOUT` | `1800` | 无操作有效期，单位秒 |

## MySQL

本机默认连接：

```text
jdbc:mysql://127.0.0.1:3306/logistics_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

项目启动时会执行 `schema.sql` 和 `data.sql`，用于创建并初始化 `demo_user` 示例表。

## H2 兜底模式

如果临时不想使用 MySQL，可以启动 H2 profile：

```bash
set SPRING_PROFILES_ACTIVE=h2
mvn spring-boot:run
```

## Nacos

默认应用名是 `practice-project-about-develop`，默认环境是 `dev`。

本地启动 Nacos：

```powershell
F:\Development\Middleware\scripts\start-nacos.ps1
```

Nacos 配置中心可以创建：

```text
Data ID: practice-project-about-develop-dev.yaml
Group: DEFAULT_GROUP
```

## Redis

项目默认连接本机 Redis：

```text
127.0.0.1:6379
```

可直接注入 `StringRedisTemplate` 或 `RedisTemplate`。

## RabbitMQ

项目默认连接本机 RabbitMQ：

```text
AMQP: localhost:5672
Management: http://127.0.0.1:15672
```

默认创建：

```text
Exchange: practice.demo.exchange
Queue: practice.demo.queue
Routing Key: practice.demo.routing-key
```

## MyBatis

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: jimmy.entity
  configuration:
    map-underscore-to-camel-case: true
```

更多内容见 [MyBatis 使用说明](mybatis.md)。

默认不输出 Mapper debug 日志，避免 SQL 参数里出现手机号、客户名等敏感信息。需要排查 SQL 时，可以临时设置：

```bash
set MYBATIS_SQL_LOG_LEVEL=debug
```

排查结束后恢复为 `info` 或删除该环境变量；生产环境会额外将 `jimmy.mapper` 固定为 `WARN`。

## Sa-Token 鉴权

项目已接入 Sa-Token，默认保护后端业务接口。前端登录成功后会保存后端返回的 token，并在后续请求头中自动携带。

当前登录策略为单账号单会话：

```text
SA_TOKEN_IS_CONCURRENT=false
SA_TOKEN_IS_SHARE=false
```

登录代码也会显式指定该策略，保证同一账号不会同时保留多个有效 token。

为了避免旧会话被生硬踢回登录页，系统额外提供 60 秒登录冲突确认窗口：新登录先等待，旧会话页面弹窗确认；旧会话拒绝则新登录失败，旧会话不处理则确认窗口结束后新登录生效。

默认管理员账号：

```text
username: admin
password: xlh963311213
```

常用接口：

```text
POST /auth/login
GET  /auth/session
POST /auth/logout
```

`/auth/login` 和 `/actuator/health` 会放行，其余接口默认要求登录。

## Bloom Filter

布隆过滤器使用 Guava 实现，默认参数：

```text
expectedInsertions = 100000
falsePositiveProbability = 0.01
```
