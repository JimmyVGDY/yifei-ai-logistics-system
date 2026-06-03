# 前端工程说明

项目已新增 `frontend` 目录，使用 Vue 3、Vite 和 Element Plus 搭建物流管理系统前端。

## 技术栈

- Vue 3
- Vite
- Element Plus
- Vue Router
- Axios

## 本地启动

先启动后端 Spring Boot，再启动前端：

```bash
cd frontend
npm install
npm run dev
```

访问地址：

```text
http://127.0.0.1:5173
```

## 后端启动后自动打开

当前 `dev` 环境下，后端启动完成后会自动启动前端 Vite 服务并打开浏览器：

```text
http://127.0.0.1:5173
```

如果临时不想自动启动或自动打开，可以设置：

```text
LOCAL_FRONTEND_AUTO_START=false
LOCAL_FRONTEND_AUTO_OPEN=false
```

## 后端代理

前端开发环境通过 Vite 代理访问后端：

```text
/api -> http://127.0.0.1:8080
```

例如：

```text
POST /api/auth/login
GET /api/logistics/modules/orders?page=1&pageSize=20
POST /api/logistics/orders
GET /api/infra/status
```

## 页面

- `运营看板`: 展示运单统计、订单状态占比、异常提醒和趋势。
- `运单管理`: 查看物流订单列表，并在页面顶部新增运单。
- `客户管理 / 运单中心 / 调度管理 / 运输任务 / 物流轨迹 / 司机管理 / 车辆管理 / 异常管理 / 费用结算`: 统一管理页，支持分页、关键词、时间范围、增删改查和状态中文展示。
- `系统管理`: 用户、角色和操作日志。
- `资源中心`: 查看后端基础设施配置状态。

前端菜单由后端登录响应返回，按钮显示也会根据 `permissions` 控制，例如 `order:create`、`order:update`、`order:delete`、`order:export`。

## VS Code

仓库包含 `.vscode` 配置和 `practice-project-about-develop.code-workspace` 工作区文件。

已配置内容：

- Git 可执行文件路径：`F:\Development\Git\cmd\git.exe`
- 前端开发、构建任务
- 后端运行、测试任务
- Vue、ESLint、Prettier、Git Graph、GitLens 等推荐扩展
- 终端 PATH 包含 Node、Git、Maven 和 JDK

## 相关文档

- [项目文档索引](README.md)
- [新手快速上手指南](getting-started.md)
- [认证接口文档](auth-api.md)
- [权限配置接口说明](role-permission-api.md)
- [物流接口文档](logistics-api.md)
