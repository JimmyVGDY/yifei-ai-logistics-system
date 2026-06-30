# 本地开发指南

## 前置要求

- JDK 21
- Maven 3.9.x（最低 3.6+）
- Node.js 18 LTS（最低 16+）
- MySQL 8.4.9（兼容 MySQL 8.0+）
- Nacos 2.4.3（兼容 Nacos 2.x）
- Sentinel Dashboard 1.8.8
- Elasticsearch 7.17.29（兼容 Elasticsearch 7.x）
- Redis 5.0.14.1（兼容 Redis 5.x 到 7.x 的基础缓存能力）
- RabbitMQ 4.1.8（基础 AMQP 能力兼容 RabbitMQ 3.x+）
- XXL-Job 2.4.x（需要调度任务时启动）

项目基于 Spring Boot `3.5.14`，依赖版本通过 `pom.xml` 中的 Spring Cloud、Spring Cloud Alibaba 和 Spring AI BOM 统一管理。完整版本矩阵见 [环境与中间件版本清单](environment-versions.md)。

## 本地中间件位置（Windows）

```text
MySQL:        F:\Development\Database\MySQL\Server-8.4.9
Nacos:        F:\Development\Middleware\nacos\nacos-2.4.3
Redis:        F:\Development\Middleware\redis\redis-5.0.14.1
RabbitMQ:     F:\Development\Middleware\rabbitmq\rabbitmq_server-4.1.8
ES:           F:\Development\Middleware\elasticsearch\elasticsearch-7.17.29
Sentinel:     F:\Development\Middleware\sentinel\sentinel-dashboard-1.8.8.jar
Erlang:       F:\Development\Middleware\erlang\otp-27.3.4.10
XXL-Job:      F:\Development\Middleware\xxl-job
脚本目录:     F:\Development\Middleware\scripts
```

## 启动基础组件

本地开发默认使用 Windows 本机安装的中间件，不需要启动 Docker Compose。Docker 仅用于可选的全栈/发布前编排验证。

### MySQL

服务名：`MySQL84`，默认连接：

```text
127.0.0.1:3306
root / 空密码（或按本机实际密码配置）
database: logistics_management
```

### Redis

```powershell
F:\Development\Middleware\scripts\start-redis.ps1
```

### RabbitMQ

```powershell
F:\Development\Middleware\scripts\start-rabbitmq.ps1
```

```text
AMQP: localhost:5672
Management: http://127.0.0.1:15672
guest / guest
```

### Nacos

```powershell
F:\Development\Middleware\scripts\start-nacos.ps1
```

```text
http://127.0.0.1:8848/nacos
nacos / nacos
```

Nacos 2.x 除了 `8848`，还需要 `9848` 和 `9849` 两个 gRPC 端口可用。

### Sentinel Dashboard

```text
java -jar sentinel-dashboard-1.8.8.jar --server.port=8858
http://127.0.0.1:8858
```

### Elasticsearch

```text
http://127.0.0.1:9200
```

### XXL-Job

调度中心本地地址：

```text
http://127.0.0.1:8081/xxl-job-admin
```

后端执行器默认端口是 `9999`。如果看到 `java.net.BindException: Address already in use: bind`，说明执行器端口已经被其他进程占用，不影响 Tomcat `8080` 已启动，但调度任务回调会不可用。处理方式：

```powershell
# 查看 9999 被哪个进程占用
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq 9999 }

# 临时换端口启动后端
--xxl.job.executor.port=10099

# 不需要调度任务时直接关闭执行器
--xxl.job.enabled=false
```

## IDEA 启动（推荐）

1. 使用 JDK 21 打开项目。
2. 等待 Maven 依赖加载完成。
3. 运行 `jimmy.DemoApplication`。

不需要 XXL-Job 调度任务时，VM Options 推荐：

```text
-Dspring.datasource.password=你的MySQL密码 -Dnacos.register-enabled=false -Dxxl.job.enabled=false
```

需要连接本地 XXL-Job 调度中心时，VM Options 推荐：

```text
-Dspring.datasource.password=你的MySQL密码 -Dnacos.register-enabled=false -Dxxl.job.enabled=true -Dxxl.job.admin.addresses=http://127.0.0.1:8081/xxl-job-admin -Dxxl.job.executor.port=9999
```

如果 `9999` 被占用，把最后一项改为：

```text
-Dxxl.job.executor.port=10099
```

## 命令行启动

### Windows

```cmd
:: 编译检查
ops\build-check.bat

:: 一键启动
ops\start.bat

:: 本地开发快速启动，会自动处理 XXL-Job 执行器端口冲突
ops\run-local.bat

:: 查看状态
ops\status.bat

:: 查看日志
ops\logs.bat

:: 停止
ops\stop.bat
```

Windows 本地脚本默认设置 `MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false`，避免本机 Elasticsearch health 响应和 Java client 版本差异导致 `/actuator/health` 被误判为 DOWN；业务搜索链路仍按 `ELASTICSEARCH_URIS` 连接。

### Linux / WSL2

```bash
# 编译检查
bash ops/build-check.sh

# 一键启动
bash ops/start.sh

# 本地开发快速启动，会自动处理 XXL-Job 执行器端口冲突
bash ops/run-local.sh

# 查看状态
bash ops/status.sh

# 查看日志
bash ops/logs.sh app -f

# 停止
bash ops/stop.sh
```

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：`http://127.0.0.1:5173`

Vite 会将 `/api` 代理到后端 `http://127.0.0.1:8080`。

当前 `frontend/package.json` 中的 `dev`、`build`、`preview` 和 `test:unit` 脚本显式使用 `C:\Progra~1\nodejs\node.exe` 调用 Node。这样做是为了规避部分 Windows/npm 环境在脚本中解析 `node` 或 `vite` 时误命中无扩展 shim 文件导致 `Access is denied.` 的问题。新开发者如果把 Node 安装在其它目录，可以改回 `node`，或按本机实际 Node 路径调整脚本。

默认 `dev` 环境下，后端启动完成后会自动启动前端并打开浏览器。需要关闭时设置：

```text
LOCAL_FRONTEND_AUTO_START=false
LOCAL_FRONTEND_AUTO_OPEN=false
```

## Maven 配置

Windows 首次使用前复制镜像配置：

```cmd
copy ops\maven-settings-windows.xml %USERPROFILE%\.m2\settings.xml
```

Linux/WSL2 可使用腾讯云镜像，`ops/run-local.sh` 会直接调用本机 Maven。

## 常用验证接口

```text
GET  http://127.0.0.1:8080/infra/status
GET  http://127.0.0.1:8080/infra/nacos/services
GET  http://127.0.0.1:8080/infra/sentinel/ping
GET  http://127.0.0.1:8080/infra/elasticsearch/client
GET  http://127.0.0.1:8080/infra/redis/client
GET  http://127.0.0.1:8080/infra/rabbitmq/client
GET  http://127.0.0.1:6333/readyz
GET  http://127.0.0.1:8080/demo-users
POST http://127.0.0.1:8080/auth/login  {"username":"admin","password":"***"}
GET  http://127.0.0.1:8080/logistics/dashboard
POST http://127.0.0.1:8080/ai/chat  {"message":"系统有哪些文档？"}
POST http://127.0.0.1:8080/ai/logs/analyze  {"traceId":"..."}
GET  http://127.0.0.1:8080/ai/memory/profile
GET  http://127.0.0.1:8080/actuator/health
GET  http://127.0.0.1:8080/actuator/prometheus
```

### Python AI 服务

> AI 模块已迁移至 Python FastAPI 服务。开发时需先启动 Python 服务，再启动 Java。

```bash
# 1. 安装依赖（仅首次）
cd ai-service
uv sync

# 2. 设置 API Key（从 Nacos spring-ai.yml 获取）
set SPRING_AI_OPENAI_API_KEY=sk-***

# 3. 设置 Java/Python internal shared secret，两侧必须一致
set AI_INTERNAL_SHARED_SECRET=local-dev-ai-secret-please-change

# 4. 启动（PyCharm 或命令行）
uv run uvicorn ai_service.main:app --host 127.0.0.1 --port 8001 --reload
```

Java 侧通过 `app.ai.python.enabled=true` 启用 Python AI 服务代理。详见 [Python AI 服务开发指南](python-ai-service.md) 和 [ADR 0001](adr/0001-java-python-hybrid-architecture.md)。

AI 助手未配置模型密钥时仍可启动，接口会返回本地文档检索和明确的配置提示。需要真实模型回答时设置 `SPRING_AI_OPENAI_API_KEY` 等变量，详见 [Spring AI 接入说明](spring-ai.md)（迁移前的 Java 实现）。

AI 长期记忆和 RAG 文档检索使用 Qdrant 做向量召回，本地脚本如下：

```cmd
ops\start-qdrant.bat
ops\status-qdrant.bat
ops\stop-qdrant.bat
```

默认安装路径为 `F:\Development\Middleware\qdrant\qdrant-1.18.2`，数据目录为 `F:\Development\Middleware\qdrant\data`。如果 Qdrant 未启动，AI 问答和物流主业务仍可运行，只是不会使用向量长期记忆和 RAG 文档语义检索。RAG 相关变量包括 `APP_AI_RAG_ENABLED`、`APP_AI_RAG_INDEX_ON_STARTUP`、`APP_AI_RAG_QDRANT_BASE_URL` 和 `APP_AI_RAG_QDRANT_COLLECTION`，默认集合为 `logistics_docs`。

## 本地测试命令

```cmd
:: Java 全量单元测试
.\mvnw.cmd test

:: Python AI 测试；如果 pytest.exe 入口损坏，使用 python -m pytest
cd ai-service
uv sync --extra dev
uv run python -m pytest

:: 前端测试和构建
cd frontend
npm run test:unit
npm run build
```

如果当前 shell 找不到 `npm`，可以使用本机 Node/npm 安装路径，或使用 Codex/IDE 提供的 Node 可执行文件直接运行 `frontend/scripts/*.mjs` 和 `node_modules/vite/bin/vite.js`。

## Docker 部署（生产环境）

```bash
cp .env.example .env
docker compose up -d
```

应用镜像使用多阶段 Dockerfile，`docker compose up -d` 会自动完成 Maven 打包，不需要提前在本机执行 `mvn package`。

XXL-Job 调度中心使用独立数据库 `xxl_job`，初始化 SQL 位于 `docker/mysql/xxl-job-init.sql`。Docker Compose 场景下会显式设置 `XXL_JOB_ENABLED=true`，普通本地启动默认关闭，避免误占用执行器端口。

启动后常用端口：

| 服务 | 端口 | 说明 |
|------|------|------|
| 应用 | 8080 | Spring Boot API |
| Nacos | 8848 | 注册/配置中心 |
| Sentinel | 8858 | 流量控制 Dashboard |
| RabbitMQ | 15672 | 管理界面 |
| Qdrant | 6333 | AI 长期记忆和 RAG 文档向量服务 |
| Grafana | 3000 | 监控仪表盘 |
| Kibana | 5601 | 日志检索 |
| XXL-Job | 8081 | 定时任务调度中心 |

## 相关文档

- [项目文档索引](README.md)
- [新手快速上手指南](getting-started.md)
- [环境与中间件版本清单](environment-versions.md)
- [配置说明](configuration.md)
- [Spring AI 接入说明](spring-ai.md)
- [数据库增量迁移说明](incremental-migration.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
