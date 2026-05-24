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

## 后端代理

前端开发环境通过 Vite 代理访问后端：

```text
/api -> http://127.0.0.1:8080
```

例如：

```text
GET /api/logistics/orders?limit=20
POST /api/logistics/orders
GET /api/infra/status
```

## 页面

- `运营看板`: 展示运单统计和近期运单。
- `运单管理`: 查看物流订单列表。
- `新建运单`: 调用后端创建物流订单。
- `资源中心`: 查看后端基础设施配置状态。

## VS Code

仓库包含 `.vscode` 配置和 `practice-project-about-develop.code-workspace` 工作区文件。

已配置内容：

- Git 可执行文件路径：`F:\Development\Git\cmd\git.exe`
- 前端开发、构建任务
- 后端运行、测试任务
- Vue、ESLint、Prettier、Git Graph、GitLens 等推荐扩展
- 终端 PATH 包含 Node、Git、Maven 和 JDK
