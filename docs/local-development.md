# 本地开发指南

## 前置要求

- JDK 8
- Maven 3.6+
- MySQL 8.4
- Nacos 2.x
- Sentinel Dashboard
- Elasticsearch 7.x
- Redis 5.x
- RabbitMQ 4.1.x

当前项目基于 Spring Boot `2.7.18`，依赖版本通过 `pom.xml` 中的 Spring Cloud 与 Spring Cloud Alibaba BOM 统一管理。

## 本机中间件位置

```text
MySQL:        F:\Development\Database\MySQL\Server-8.4.9
Nacos:        F:\Development\Middleware\nacos\nacos-2.4.3
Redis:        F:\Development\Middleware\redis\redis-5.0.14.1
RabbitMQ:     F:\Development\Middleware\rabbitmq\rabbitmq_server-4.1.8
Erlang:       F:\Development\Middleware\erlang\otp-27.3.4.10
脚本目录:     F:\Development\Middleware\scripts
```

## 启动基础组件

### MySQL

服务名：

```text
MySQL84
```

默认连接：

```text
127.0.0.1:3306
root / 空密码
database: practice_dev
logistics database: logistics_management
```

### Redis

```powershell
F:\Development\Middleware\scripts\start-redis.ps1
```

验证：

```powershell
F:\Development\Middleware\redis\redis-5.0.14.1\redis-cli.exe ping
```

### RabbitMQ

```powershell
F:\Development\Middleware\scripts\start-rabbitmq.ps1
```

默认访问：

```text
AMQP: 127.0.0.1:5672
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

Nacos 2.x 除了 `8848`，还需要 `9848` 和 `9849` 两个 gRPC 端口可用。应用启动时报 `Client not connected, current status: STARTING` 时，优先检查这三个端口是否已经监听。

### Sentinel Dashboard

```text
127.0.0.1:8858
```

### Elasticsearch

```text
http://127.0.0.1:9200
```

## 启动应用

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8080/infra/status
```

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：

```text
http://127.0.0.1:5173
```

Vite 已将 `/api` 代理到后端 `http://127.0.0.1:8080`。

默认 `dev` 环境下，后端启动完成后会自动启动前端并打开浏览器。需要关闭时可设置：

```text
LOCAL_FRONTEND_AUTO_START=false
LOCAL_FRONTEND_AUTO_OPEN=false
```

## 常用验证接口

```text
GET  http://127.0.0.1:8080/infra/status
GET  http://127.0.0.1:8080/infra/nacos/services
GET  http://127.0.0.1:8080/infra/sentinel/ping
GET  http://127.0.0.1:8080/infra/elasticsearch/client
GET  http://127.0.0.1:8080/infra/redis/client
GET  http://127.0.0.1:8080/infra/rabbitmq/client
GET  http://127.0.0.1:8080/demo-users
GET  http://127.0.0.1:8080/demo-users/detail?id=1
POST http://127.0.0.1:8080/demo-users?username=test&displayName=Test
POST http://127.0.0.1:8080/bloom-filter/items?value=demo
GET  http://127.0.0.1:8080/bloom-filter/items?value=demo
POST http://127.0.0.1:8080/rabbitmq/messages?message=hello
GET  http://127.0.0.1:8080/actuator/health
```

## IDEA 使用建议

1. 使用 JDK 8 打开项目。
2. 等待 Maven 依赖加载完成。
3. 运行 `jimmy.DemoApplication`。
4. 已配置 IDEA 支持在代码编辑页按住 `Ctrl` 并滚动鼠标滚轮缩放字体，重启 IDEA 后生效。
