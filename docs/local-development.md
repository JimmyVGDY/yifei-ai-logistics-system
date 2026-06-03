# 本地开发指南

## 前置要求

- JDK 8
- Maven 3.6+
- Node.js 16+
- MySQL 8.0+
- Nacos 2.x
- Sentinel Dashboard
- Elasticsearch 7.x
- Redis 5.x
- RabbitMQ 3.x+
- XXL-Job 2.4.x（需要调度任务时启动）

项目基于 Spring Boot `2.7.18`，依赖版本通过 `pom.xml` 中的 Spring Cloud 和 Spring Cloud Alibaba BOM 统一管理。

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

1. 使用 JDK 8 打开项目。
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
GET  http://127.0.0.1:8080/demo-users
POST http://127.0.0.1:8080/auth/login  {"username":"admin","password":"***"}
GET  http://127.0.0.1:8080/logistics/dashboard
GET  http://127.0.0.1:8080/actuator/health
GET  http://127.0.0.1:8080/actuator/prometheus
```

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
| Grafana | 3000 | 监控仪表盘 |
| Kibana | 5601 | 日志检索 |
| XXL-Job | 8081 | 定时任务调度中心 |

## 相关文档

- [项目文档索引](README.md)
- [新手快速上手指南](getting-started.md)
- [配置说明](configuration.md)
- [数据库增量迁移说明](incremental-migration.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
