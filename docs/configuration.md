# 配置说明

项目使用 `bootstrap.yml` 管理应用名、环境和 Nacos，使用 `application.yml` 管理端口、数据源、Redis、RabbitMQ、Sentinel、Elasticsearch 和监控端点。组件安装版本和 Docker 镜像版本见 [环境与中间件版本清单](environment-versions.md)。

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
| `SPRING_SQL_INIT_MODE` | `never` | SQL 初始化模式，MySQL 默认不自动执行初始化脚本 |
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
| `APP_ADMIN_PASSWORD` | 见 `application.yml` | 后台管理员密码，建议本地通过环境变量覆盖 |
| `APP_ENCRYPT_ENABLED` | `true` | 是否启用手机号等敏感字段加密 |
| `APP_ENCRYPT_KEY` | 空 | 敏感字段加密密钥；生产环境必须配置 |
| `APP_ENCRYPT_REQUIRE_KEY` | `false`，生产为 `true` | 是否要求显式配置加密密钥 |
| `MYBATIS_SQL_LOG_LEVEL` | `info` | MyBatis Mapper 日志级别，排查 SQL 时可临时设为 `debug` |
| `SPRING_AI_OPENAI_API_KEY` | `missing` | Spring AI OpenAI 兼容接口密钥；未配置时 AI 接口走本地兜底，不影响应用启动 |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI 兼容接口地址，可替换为内网模型网关 |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o-mini` | AI 问答使用的聊天模型名称 |
| `APP_AI_CONVERSATION_TTL_SECONDS` | `3600` | Redis 中 AI 短期会话保留时间，单位秒 |
| `SA_TOKEN_NAME` | `satoken` | Sa-Token 请求头名称 |
| `SA_TOKEN_TIMEOUT` | `86400` | 登录有效期，单位秒 |
| `SA_TOKEN_ACTIVE_TIMEOUT` | `1800` | 无操作有效期，单位秒 |

## MySQL

本机默认连接：

```text
jdbc:mysql://127.0.0.1:3306/logistics_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

MySQL 环境默认 `SPRING_SQL_INIT_MODE=never`，项目启动不会自动执行 `schema.sql` 和 `data.sql`，避免覆盖已有数据。已有库结构升级请执行 [数据库增量迁移说明](incremental-migration.md) 中登记的脚本。

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
password: 963311213
```

常用接口：

```text
POST /auth/login
GET  /auth/session
POST /auth/logout
```

`/auth/login` 和 `/actuator/health` 会放行，其余接口默认要求登录。

## Spring AI 助手

AI 助手接口统一由后端代理调用模型，前端不会接触模型密钥。当前只开放只读问答、日志排障、白名单业务查询、受控临时 SELECT 查询和账号级长期记忆管理：

```text
POST /ai/chat
POST /ai/chat/stream
POST /ai/logs/analyze
GET  /ai/conversations
GET  /ai/conversations/{id}
GET  /ai/memory/profile
GET  /ai/memory/items
DELETE /ai/memory/items/{id}
DELETE /ai/memory/items
PUT /ai/memory/settings
```

权限码：

```text
ai:chat
ai:log:analyze
ai:conversation:query
ai:memory:query
ai:memory:delete
ai:memory:settings
```

`ai:chat` 会展开为普通问答、当前用户会话和当前用户长期记忆管理能力；`ai:log:analyze` 不随 `ai:chat` 自动展开，默认只建议授予管理员、审计员和需要排障的管理角色。

如果 `SPRING_AI_OPENAI_API_KEY` 未配置或仍为 `missing`，应用仍可正常启动，AI 问答会返回本地文档检索和中文配置提示；临时只读 SQL 能力依赖真实模型生成候选查询，未配置模型时会自动回退为普通业务查询提示。真实模型接入、脱敏边界和验证方式见 [Spring AI 接入说明](spring-ai.md)。

AI 配置可以放到 Nacos，默认导入 `spring-ai.yml`：

```text
Data ID: spring-ai.yml
Group: DEFAULT_GROUP
格式: YAML
```

应用启动后会输出“AI 配置摘要”，用于确认 `base-url` 和 `model` 是否来自远程配置；日志不会打印 API Key 明文。

AI 临时只读 SQL 不需要单独配置数据库连接，它复用当前应用数据源、Sa-Token 权限和 MyBatis 文档中定义的安全边界。普通业务 SQL 仍必须写入 Mapper XML，详见 [MyBatis 使用规范](mybatis.md)。

AI 长期记忆配置：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_AI_SSE_TIMEOUT_MS` | `180000` | AI 流式问答异步超时，单位毫秒；模型和工具调用较慢时可适当调大 |
| `APP_AI_MEMORY_QDRANT_ENABLED` | `true` | 是否启用 Qdrant 向量召回；不可用时自动降级 |
| `APP_AI_MEMORY_QDRANT_BASE_URL` | `http://127.0.0.1:6333` | Qdrant HTTP 地址 |
| `APP_AI_MEMORY_QDRANT_COLLECTION` | `logistics_ai_user_memory` | 长期记忆向量集合；集合维度必须匹配当前 embedding 模型 |

长期记忆的 MySQL 真值表为 `ai_user_profile`、`ai_user_memory`、`ai_memory_event`，需要执行 `scripts/sql/20260605_incremental_ai_long_term_memory.sql`。生命周期字段由 `scripts/sql/20260610_incremental_ai_memory_lifecycle.sql` 补齐，该脚本可重复执行。Qdrant 只保存脱敏摘要向量点，不能作为唯一审计数据源。

## Bloom Filter

布隆过滤器使用 Guava 实现，默认参数：

```text
expectedInsertions = 100000
falsePositiveProbability = 0.01
```

## 敏感字段加密

`FieldEncryptor` 负责手机号等敏感字段入库加密：

- 新数据写入 `ENCGCM:` AES-GCM 密文。
- 旧数据 `ENC:` 密文继续兼容解密。
- `sys_user.mobile_hash` 用于手机号不可逆查重。
- 生产配置 `application-prod.yml` 已开启 `app.encrypt.require-key=true`，必须通过 `APP_ENCRYPT_KEY` 提供密钥。

## 上传限制

应用层和业务层都会限制上传文件：

- 通用上传：最大 20MB，仅允许 `.xlsx`、`.xls`、`.pdf`、`.doc`、`.docx`、`.png`、`.jpg`、`.jpeg`。
- 客户导入：最大 10MB，仅允许 `.xlsx`，单次最多 1000 行。

## 相关文档

- [项目文档索引](README.md)
- [环境与中间件版本清单](environment-versions.md)
- [数据库增量迁移说明](incremental-migration.md)
- [Spring AI 接入说明](spring-ai.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [本地开发指南](local-development.md)
