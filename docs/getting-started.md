# 新手快速上手指南

> 目标：10 分钟内从零拉取代码到启动应用并看到登录页面。

## 前置条件

- **JDK 21**（推荐 Temurin 21 / Zulu 21 / Corretto 21）
- **Maven 3.9.x**（最低 3.9+）
- **Node.js 18 LTS**（最低 16+，仅前端开发需要）
- **Python 3.12+**（AI 功能需要，需安装 `uv` 包管理器：`pip install uv`）
- **Git**（需要能访问 Gitee 仓库）
- 本机已安装并启动以下中间件（Windows 环境路径见下文）

> 💡 如果中间件还没安装，先确认 [环境与中间件版本清单](environment-versions.md)，再参考 [本地开发指南](local-development.md) 的「启动基础组件」一节。

## 第一步：克隆仓库

```bash
git clone https://gitee.com/jimmyVG/yifei-ai-logistics-system.git
cd yifei-ai-logistics-system
```

## 第二步：确认中间件运行状态

下表是本项目依赖的全部中间件，启动应用前确保它们都在运行。完整版本矩阵见 [环境与中间件版本清单](environment-versions.md)。

| 中间件 | 推荐版本 | 端口 | 用途 | 启动命令（Windows PowerShell） |
|--------|----------|------|------|-------------------------------|
| MySQL | 8.4.9 | 3306 | 主数据库 | `Start-Service MySQL84` |
| Redis | 5.0.14.1 | 6379 | 订单缓存 | `F:\Development\Middleware\scripts\start-redis.ps1` |
| RabbitMQ | 4.1.8 | 5672 / 15672 | 订单事件消息 | `F:\Development\Middleware\scripts\start-rabbitmq.ps1` |
| Elasticsearch | 7.17.29 | 9200 | 订单搜索 | 启动 ES 服务 |
| Nacos | 2.4.3 | 8848 / 9848 / 9849 | 注册/配置中心 | `F:\Development\Middleware\scripts\start-nacos.ps1` |
| Sentinel | 1.8.8 | 8858 | 流量控制 | `java -jar sentinel-dashboard-1.8.8.jar --server.port=8858` |
| XXL-Job | 2.4.x | 8081 | 定时任务（可选） | 按需启动 |

验证所有中间件是否就绪：

```bash
# Windows 下可直接浏览器访问
http://127.0.0.1:8848/nacos     # Nacos 控制台，账号 nacos/nacos
http://127.0.0.1:15672          # RabbitMQ 管理界面
http://127.0.0.1:8858           # Sentinel Dashboard
http://127.0.0.1:9200           # ES，返回 JSON 即正常
http://127.0.0.1:8081/xxl-job-admin  # XXL-Job（可选）
```

> 💡 如果不需要定时任务，启动时可加 `-Dxxl.job.enabled=false` 跳过。

## 第三步：数据库初始化

项目使用 Windows 本地 MySQL 8.4，数据库名 `logistics_management`。确认数据库已存在：

```powershell
# 通过 MySQL 客户端确认
mysql -h 127.0.0.1 -u root -e "SHOW DATABASES LIKE 'logistics_management';"
```

如果数据库不存在，创建它：

```sql
CREATE DATABASE logistics_management DEFAULT CHARACTER SET utf8mb4;
```

**首次启动**：把 `src/main/resources/application.yml` 中 `spring.sql.init.mode` 临时改为 `always`，启动时会自动执行 `schema.sql` 和 `data.sql` 建表+灌入种子数据。启动成功后改回 `never`，避免每次启动覆盖数据。

**已有旧库**：按 [增量迁移说明](incremental-migration.md) 逐条执行 SQL 脚本。

## 第四步：启动后端

### 方式一：IDE 启动（推荐）

用 IntelliJ IDEA 打开项目，运行 `jimmy.DemoApplication`。VM Options 建议：

```
-Dnacos.register-enabled=false -Dxxl.job.enabled=false
```

### 方式二：命令行启动

```bash
mvn spring-boot:run
```

看到以下日志表示启动成功：

```
Tomcat started on port(s): 8080 (http)
```

## 第五步：验证后端

打开浏览器访问：

```
http://127.0.0.1:8080/infra/status
```

应返回所有中间件的连接状态。再访问健康检查：

```
http://127.0.0.1:8080/actuator/health
```

返回 `{"status":"UP"}` 即表示服务正常。

## 第六步：启动前端（可选）

如果只需要调试后端接口，跳过此步。如需看管理页面：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`，用以下账号登录：

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `your-password` | 系统管理员（全部权限） |
| `dispatcher` | `123456` | 调度专员 |
| `finance` | `123456` | 财务专员 |
| `customer` | `123456` | 客户账号 |

## 第七步：跑通核心流程

验证业务链路是否正常：

```bash
# 1. 登录获取 token
curl -X POST http://127.0.0.1:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-password"}'

# 2. 查看运营看板
curl http://127.0.0.1:8080/logistics/dashboard \
  -H "satoken: <上一步返回的 token>"

# 3. 查询最近订单
curl http://127.0.0.1:8080/logistics/orders \
  -H "satoken: <token>"
```

## 常见问题

### Q: 启动报 `Table 'logistics_management.sys_login_history' doesn't exist`

数据库表缺失。需要执行增量迁移脚本，或临时开启 `spring.sql.init.mode=always` 重新建表。

### Q: `Connection refused` 连不上 RabbitMQ / Redis / ES

确认对应中间件服务已启动。Windows 下检查：

```powershell
Get-Service -Name "*Redis*","*RabbitMQ*","*Elasticsearch*" | Format-Table Name,Status
```

### Q: Maven 下载依赖很慢

配置国内镜像。项目 `ops/` 目录提供了 Maven 配置模板：

```cmd
copy ops\maven-settings-windows.xml %USERPROFILE%\.m2\settings.xml
```

### Q: `Address already in use: bind`（端口 8080 被占用）

```powershell
# 查看 8080 被谁占用
netstat -ano | findstr :8080
```

或换端口启动：`-Dserver.port=8081`

### Q: XXL-Job 执行器端口 9999 冲突

加 JVM 参数 `-Dxxl.job.enabled=false` 关闭调度任务，或 `-Dxxl.job.executor.port=10099` 换端口。

### Q: 前端启动报 `Cannot find module`

确认在 `frontend` 目录下执行了 `npm install`。

## 下一步

环境跑通后，建议按以下顺序阅读文档：

| 顺序 | 文档 | 何时看 |
|------|------|--------|
| 1 | [项目结构说明](architecture.md) | 了解目录结构和分层约定 |
| 2 | [配置说明](configuration.md) | 了解环境变量和组件配置 |
| 3 | [物流接口文档](logistics-api.md) | 熟悉所有 API 接口 |
| 4 | [权限配置说明](role-permission-api.md) | 理解 RBAC 权限模型 |
| 5 | [物流数据库说明](logistics-database.md) | 了解表结构和数据关系 |
| 6 | [MyBatis 使用规范](mybatis.md) | 新增 SQL 时参考 |
| 7 | [前端新人接入手册](frontend.md) | 前端开发参考 |
| 8 | [链路追踪说明](trace-context-audit.md) | 排障时查阅 |
| 9 | [需求匹配说明](requirements-mapping.md) | 对照需求查看落地情况 |
| 10 | [本地开发指南](local-development.md) | 中间件细节和运维脚本 |

## 开发工具建议

- **后端**：IntelliJ IDEA（社区版即可）
- **前端**：VS Code（项目提供了 `.vscode` 配置和工作区文件）
- **数据库**：Navicat / DBeaver / TablePlus
- **API 调试**：Apifox / Postman
- **Redis**：Another Redis Desktop Manager
- **消息队列**：RabbitMQ Management (`http://127.0.0.1:15672`)

## 相关文档

- [项目文档索引](README.md)
- [本地开发指南](local-development.md)
- [环境与中间件版本清单](environment-versions.md)
- [配置说明](configuration.md)
- [数据库增量迁移说明](incremental-migration.md)
- [前端新人接入手册](frontend.md)
