# 前端新人接入手册

本文面向新加入的前端开发者，说明物流管理系统前端的启动方式、目录结构、接口调用、路由权限、通用管理页、状态展示、开发流程和常见问题。阅读本文前建议先看 [项目文档索引](README.md) 和 [新手快速上手指南](getting-started.md)。

## 1. 前端定位

前端位于仓库的 `frontend/` 目录，是一个基于 Vue 3 + Vite + Element Plus 的后台管理台。它不直接连接数据库，也不直接调用中间件；所有业务数据、权限、菜单和审计都通过 Spring Boot 后端接口获取。

当前前端主要承担：

- 登录、验证码、单账号登录冲突提示。
- 根据后端返回的菜单树渲染侧边栏。
- 根据后端返回的权限码控制页面访问和按钮显隐。
- 展示物流管理核心页面：运单、客户、运单中心、调度、任务、轨迹、司机、车辆、异常、费用、文件、资源和系统管理。
- 调用统一后端接口完成列表查询、分页、模糊查询、时间范围查询、新增、编辑、删除、导入、导出和专项业务操作。

## 2. 技术栈

| 技术 | 用途 | 当前位置 |
| --- | --- | --- |
| Vue 3 | 页面和组件开发 | `frontend/src/**/*.vue` |
| Vite | 本地开发服务和构建 | `frontend/package.json` |
| Element Plus | 表格、弹窗、表单、按钮、分页等后台组件 | `frontend/src/main.js` |
| Vue Router | 前端路由和页面级权限守卫 | `frontend/src/router/index.js` |
| Axios | HTTP 请求封装 | `frontend/src/api/http.js` |
| sessionStorage | 保存当前页签的 token、用户、菜单和权限 | `frontend/src/stores/auth-store.js` |

## 3. 本地启动

先确认后端已经启动，默认地址为 `http://127.0.0.1:8080`。然后启动前端：

```bash
cd frontend
npm install
npm run dev
```

前端默认访问地址：

```text
http://127.0.0.1:5173
```

构建命令：

```bash
cd frontend
npm run build
```

权限逻辑轻量测试：

```bash
cd frontend
npm run test:unit
```

更多本地环境说明见 [本地开发环境说明](local-development.md)。

## 4. 后端自动打开前端

当前 `dev` 环境下，后端启动后可自动启动前端 Vite 服务并打开浏览器：

```text
LOCAL_FRONTEND_AUTO_START=true
LOCAL_FRONTEND_AUTO_OPEN=true
LOCAL_FRONTEND_URL=http://127.0.0.1:5173
```

如果前端开发者想自己手动启动 Vite，可以关闭：

```text
LOCAL_FRONTEND_AUTO_START=false
LOCAL_FRONTEND_AUTO_OPEN=false
```

这部分配置来自后端 `application.yml` 的 `app.local-frontend`，详细配置见 [配置说明](configuration.md)。

## 5. 代理和接口约定

前端请求统一走 `/api` 前缀，由 Vite 代理到后端：

```text
/api -> http://127.0.0.1:8080
```

常见接口示例：

```text
POST /api/auth/login
GET  /api/auth/session
GET  /api/logistics/modules/orders?page=1&pageSize=20
POST /api/logistics/orders
GET  /api/logistics/dashboard
GET  /api/infra/status
```

接口封装入口：

- `frontend/src/api/http.js`：Axios 实例、token 注入、统一响应拆包、401/403 处理。
- `frontend/src/api/auth.js`：登录、会话、退出、验证码、个人资料和密码修改。
- `frontend/src/api/logistics.js`：物流业务、通用模块 CRUD、导入导出、异常、费用、搜索和资源状态。
- `frontend/src/api/system-permission.js`：菜单、角色权限、用户特殊权限配置。

后端返回结构统一为 `ApiResponse<T>`，`http.js` 默认会把 `response.data.data` 解包给业务页面。Blob 下载接口会直接返回文件数据。

接口详情见 [物流接口文档](logistics-api.md)、[认证接口文档](auth-api.md)、[权限配置接口说明](role-permission-api.md)。

## 6. 目录结构

```text
frontend/
├── package.json                     # 前端依赖和脚本
├── src/
│   ├── main.js                      # 创建 Vue 应用，注册 Element Plus 和图标
│   ├── App.vue                      # 应用根布局
│   ├── styles.css                   # 全局样式
│   ├── api/
│   │   ├── http.js                  # Axios 统一封装
│   │   ├── auth.js                  # 登录和会话接口
│   │   ├── logistics.js             # 物流业务接口
│   │   └── system-permission.js     # 权限配置接口
│   ├── components/
│   │   ├── ModuleToolbar.vue        # 管理页查询栏和操作按钮
│   │   └── ModulePagination.vue     # 管理页分页
│   ├── router/
│   │   └── index.js                 # 路由表和权限守卫
│   ├── stores/
│   │   └── auth-store.js            # token、用户、角色、菜单和权限状态
│   ├── utils/
│   │   ├── permission-utils.js      # 操作权限码推导
│   │   └── status-labels.js         # 状态中文化和时间格式化
│   └── views/
│       ├── LoginView.vue            # 登录页
│       ├── DashboardView.vue        # 运营看板
│       ├── ModuleListView.vue       # 通用管理页
│       ├── PermissionConfigView.vue # 权限配置页
│       ├── ResourcesView.vue        # 资源中心
│       └── AiAssistantView.vue      # AI 助手（SSE 流式对话）
```

整体架构说明见 [项目结构说明](architecture.md)。

## 7. 登录和会话

登录流程：

1. `LoginView.vue` 调用 `login(payload)`。
2. 后端返回 token、用户信息、角色、权限码和菜单树。
3. `auth-store.js` 的 `saveAuthToken()` 保存会话信息到响应式状态和 `sessionStorage`。
4. 后续请求由 `http.js` 自动把 Sa-Token token 放到请求头。
5. 刷新页面后，路由守卫会调用 `/auth/session` 恢复菜单和权限。

当前前端保存的关键字段：

| 字段 | 说明 |
| --- | --- |
| `tokenName` / `tokenValue` | Sa-Token 请求头名称和值 |
| `username` | 脱敏后的展示用户名 |
| `userId` | 用户主键，用于审计和排障 |
| `userCode` | 用户业务编号，前端优先展示 |
| `roleCode` / `roleName` | 当前角色编码和名称 |
| `permissions` | 最终权限码，等于角色权限 + 用户额外授权 - 用户禁用权限 |
| `menus` | 后端返回的当前用户可见菜单树 |

认证规则见 [认证接口文档](auth-api.md)。

## 8. 路由和页面权限

路由定义在 `frontend/src/router/index.js`。每个需要鉴权的路由通常包含：

```js
{
  path: '/orders',
  component: ModuleListView,
  meta: {
    title: '运单管理',
    module: 'orders',
    permission: 'order:query',
    businessCreate: true
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `path` | 前端访问路径 |
| `component` | 渲染组件 |
| `meta.title` | 页面标题 |
| `meta.module` | 通用管理模块名称，会拼接到 `/logistics/modules/{module}` |
| `meta.permission` | 进入页面需要的查询或查看权限 |
| `meta.businessCreate` | 运单管理新增时走专用下单接口 `/logistics/orders` |

路由守卫会做三层判断：

1. 未登录时跳转到 `/login`。
2. 已登录但本地没有会话缓存时，调用 `/auth/session` 恢复。
3. 当前路径不在菜单树中，或缺少 `meta.permission`，跳转到第一个可访问菜单。

重要规则：

- 前端隐藏菜单和按钮只是体验层，真正安全边界在后端 Sa-Token 和数据权限。
- 新增页面必须同时确认后端菜单、权限码、路由 `meta.permission` 和按钮权限。
- 客户角色的数据隔离必须由后端按 `customerId` 过滤，前端不能自行决定数据范围。

权限模型见 [权限配置接口说明](role-permission-api.md) 和 [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)。

## 9. 按钮权限

按钮权限主要由 `ModuleListView.vue` 和 `permission-utils.js` 处理。

`permission-utils.js` 会根据页面查询权限推导操作权限：

```text
order:query  -> order:create / order:update / order:delete / order:export / order:import
fee:query    -> fee:create / fee:update / fee:export
```

通用管理页按钮显隐：

| 按钮 | 前端判断 | 常见权限 |
| --- | --- | --- |
| 查询 | `canQuery` | `xxx:query` 或 `track:view` |
| 新增 | `canCreate` | `xxx:create` |
| 编辑 | `canUpdate` | `xxx:update` |
| 删除 | `canDelete` | `xxx:delete` |
| 导出 Excel | `canExport` | `xxx:export` |
| 导入客户 | `canImportCustomer` | `customer:import` |
| 上报异常 | `canReportException` | `exception:create` |
| 异常处理 | `canHandleException` | `exception:update` |
| 生成费用 | `canGenerateFee` | `fee:create` |
| 收款 | `canPayFee` | `fee:update` |

如果新增按钮，要同步检查：

- 后端是否有对应接口权限。
- 登录响应 `permissions` 是否包含该权限。
- 前端按钮是否使用相同权限码。
- 操作日志是否能记录清晰的操作内容。

## 10. 通用管理页

大部分管理页面复用 `ModuleListView.vue`，包括：

- 运单管理
- 客户管理
- 运单中心
- 调度管理
- 运输任务
- 物流轨迹
- 司机管理
- 车辆管理
- 异常管理
- 费用结算
- 用户管理
- 角色管理
- 操作日志
- 上传文件

通用管理页的核心机制：

| 能力 | 说明 |
| --- | --- |
| 页面元数据 | `moduleMetas` 定义标题、说明、表格列和编辑字段 |
| 查询栏 | `ModuleToolbar.vue` 负责关键词、时间范围、页大小和按钮 |
| 表格 | 根据 `meta.columns` 动态渲染 |
| 分页 | `ModulePagination.vue` 统一处理 |
| 新增/编辑弹窗 | 根据 `editFields` 动态渲染输入框、数字框、时间选择器或下拉框 |
| 状态中文化 | `displayCell()` 调用 `statusLabel()` |
| 时间格式化 | `displayCell()` 调用 `formatDateTime()` |
| 关系下拉 | `loadRelationOptions()` 从订单、客户、司机、车辆等模块加载备选项 |
| 专项操作 | 异常上报、异常处理、费用生成、费用收款、客户账号创建 |

列表接口统一调用：

```js
fetchModuleRecords(route.meta.module, {
  page: page.value,
  pageSize: limit.value,
  keyword: keyword.value || undefined,
  startTime: timeRange.value?.[0],
  endTime: timeRange.value?.[1]
})
```

后端会返回分页结构：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

如果后端老接口仍返回数组，前端暂时兼容，但新接口应优先返回分页结构。

## 11. 新增一个管理页面

新增一个标准管理页面时，按这个顺序做：

1. 后端确认模块白名单、字段白名单、Mapper XML、权限码和菜单。
2. 在 `frontend/src/router/index.js` 增加路由，设置 `meta.module` 和 `meta.permission`。
3. 在 `ModuleListView.vue` 的 `moduleMetas` 增加模块配置：

```js
newModule: moduleMeta(
  'newModule',
  '页面名称',
  '页面说明',
  'field_a:字段A,status:状态,created_at:创建时间',
  'field_a:字段A,status:状态'
)
```

4. 如果字段需要下拉，在 `fieldOptions()` 或关系选项配置中补充。
5. 如果有新状态码，在 `frontend/src/utils/status-labels.js` 补中文映射。
6. 如果有专项按钮，在 `ModuleToolbar.vue` 和 `ModuleListView.vue` 中补按钮、权限和接口调用。
7. 执行 `npm run build`，并用不同角色登录检查菜单、按钮和接口权限。
8. 更新相关文档，至少包含 [前端新人接入手册](frontend.md)、[物流接口文档](logistics-api.md)、[权限配置接口说明](role-permission-api.md) 或 [数据库说明](logistics-database.md)。

## 12. 新增一个接口调用

新增前端接口调用时，不要在页面里直接写 `axios`，应放到对应 API 文件：

```js
// frontend/src/api/logistics.js
export function doSomething(payload) {
  return http.post('/logistics/example/action', payload)
}
```

页面里只调用封装后的函数：

```js
import { doSomething } from '../api/logistics'

await doSomething(form)
```

约定：

- URL 只写后端真实路径，不要手动加 `/api`，代理会自动处理。
- 需要下载文件时设置 `responseType: 'blob'`。
- 不在前端保存密钥、数据库密码、模型 API Key 或中间件账号。
- 401 和 403 交给 `http.js` 统一处理。

## 13. 状态和时间展示

状态中文化统一维护在：

```text
frontend/src/utils/status-labels.js
```

规则：

- 后端数据库状态仍使用英文编码，例如 `WAIT_DISPATCH`、`PROCESSING`、`PAID`。
- 前端展示必须转中文，例如 `待调度`、`处理中`、`已付款`。
- 未配置的新状态会显示为 `未知状态(原值)`，方便及时发现漏项。
- 时间统一展示为 `yyyy-MM-dd HH:mm:ss`。

新增状态码时，优先同步：

- 后端 VO 的 `statusLabel`。
- 前端 `status-labels.js` 兜底映射。
- 文档中的状态说明。

## 14. 客户账号和数据隔离

客户账号是特殊账号，不应完全等同于内部员工账号。

当前前端在用户管理中提供“新增客户账号”入口：

- 支持个人账号和企业账号。
- 企业账号可从已有运单客户名称中选择，也可以手动输入。
- 手机号按 11 位中国大陆手机号校验。
- 企业客户允许多个账号，个人客户以手机号查重。

安全边界：

- 客户账号能看到哪些订单，最终由后端 `customerId` 绑定和数据过滤决定。
- 前端菜单、按钮和查询条件不能作为客户数据隔离的唯一依据。
- 新增客户相关页面时必须回看 [用户与客户账号接口说明](user-api.md) 和 [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)。

## 15. 操作日志和排障展示

操作日志页面复用 `ModuleListView.vue`，模块名为 `operationLogs`。

列表页只展示关键摘要，过长字段会省略；点击“查看”打开详情抽屉，适合查看：

- `traceId`
- `operationId`
- `loginSessionId`
- 用户编号和用户主键
- 请求地址和方法
- 参数摘要
- 变更摘要
- 异常信息

排障时建议：

1. 用操作日志列表按关键词、时间范围、用户编号或接口路径查找记录。
2. 复制 `traceId` 或 `operationId`。
3. 到结构化日志或后端控制台按同一标识继续追踪 Redis、ES、RabbitMQ 等链路。

链路追踪规则见 [链路追踪与会话审计说明](trace-context-audit.md)。

## 16. VS Code 使用

仓库包含：

- `.vscode/` 配置
- `yifei-ai-logistics-system.code-workspace` 工作区文件

建议新前端开发者用 VS Code 打开工作区文件，而不是只打开 `frontend/` 目录。这样可以同时看到后端、前端、文档、脚本和配置。

已配置或建议使用：

- Git
- Vue
- ESLint
- Prettier
- Git Graph
- GitLens
- Node.js 终端环境
- 前端开发和构建任务

## 17. 开发检查清单

提交前建议至少检查：

- `npm run build` 通过。
- 新增页面有路由权限和菜单权限。
- 新增按钮按权限显隐。
- 直接访问无权限页面会被拦截。
- 列表分页、关键词、时间范围查询可用。
- 状态没有英文直出。
- 时间格式是 `yyyy-MM-dd HH:mm:ss`。
- 新增接口没有在页面里直接写裸 `axios`。
- 敏感字段不在前端明文长期保存。
- 相关文档和交叉引用已同步更新。

如果涉及后端接口变更，还要回归 `mvn test`，并检查对应接口文档。

## 18. AI 展示安全

AI 助手前端不能原样展示内部工具名、数据库字段名、权限码、SQL 文本或异常堆栈。`frontend/src/utils/ai-display-sanitizer.js` 是统一入口，`AiAssistantView`、`DataResultDrawer`、工具调用日志、引用来源和结构化结果卡片都必须复用它。

展示规则：

- 工具名和目标优先使用后端 `displayToolName`、`displayTarget`，缺失时映射为中文业务名。
- 摘要优先使用 `displaySummary`；如果内容包含 Markdown 表格、SQL、snake_case 字段或内部工具名，前端降级为固定中文安全提示。
- `columns` 和 `rows/data` 只展示中文安全列；未知字段、系统字段、权限字段和未授权敏感列默认隐藏，不再原样兜底展示。
- 结果卡片标题显示“业务数据查询 · 订单管理”“统计分析 · 统计结果”等中文名，不显示 `execute_readonly_sql`、`query_business_module` 或模块码。
- 抽屉 footer 只有在 `hasMore=true` 且存在 `cursorId` 时显示继续加载按钮。

继续开发 AI 查询、SSE 或表格抽屉时，还要同步阅读 [Spring AI 接入说明](spring-ai.md)、[Python AI 服务开发指南](python-ai-service.md)、[MyBatis 使用规范](mybatis.md) 和 [链路追踪与会话审计说明](trace-context-audit.md)。

## 19. 常见问题

### 登录后没有菜单

优先检查：

- `/auth/login` 返回的 `menus` 是否为空。
- `/auth/session` 是否能恢复菜单。
- 当前角色是否真的被分配菜单和权限。
- 前端 `sessionStorage` 中旧会话是否需要清理后重新登录。

相关文档：[认证接口文档](auth-api.md)、[权限配置接口说明](role-permission-api.md)。

### 页面能看到但接口提示无权限

说明菜单权限和接口权限可能不一致。检查：

- 路由 `meta.permission`。
- 按钮推导出的操作权限。
- 后端 Sa-Token 拦截器中的接口权限码。
- 当前用户是否存在 `DENY` 禁用权限。

### 状态显示 `未知状态(xxx)`

说明后端返回了新的状态码，但前端兜底映射还没补。先确认后端状态含义，再更新 `status-labels.js`。

### 403 弹窗后还能看到旧页面数据

这是前端缓存的旧页面渲染结果，不代表接口仍可访问。新增页面时要确保路由守卫、接口权限和数据权限都生效；真正安全边界以后端为准。

### 前端请求 404

检查：

- 接口路径是否写在 `frontend/src/api/*.js` 中。
- 是否误加了 `/api` 前缀。
- Vite 代理是否启动。
- 后端接口是否存在。

### 前端启动报依赖缺失

在 `frontend/` 目录执行：

```bash
npm install
```

必要时删除 `node_modules` 后重新安装。

## 20. 相关文档

- [项目文档索引](README.md)
- [新手快速上手指南](getting-started.md)
- [本地开发环境说明](local-development.md)
- [项目结构说明](architecture.md)
- [配置说明](configuration.md)
- [物流接口文档](logistics-api.md)
- [认证接口文档](auth-api.md)
- [权限配置接口说明](role-permission-api.md)
- [用户与客户账号接口说明](user-api.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [链路追踪与会话审计说明](trace-context-audit.md)
- [Spring AI 接入说明](spring-ai.md)
- [Python AI 服务开发指南](python-ai-service.md)
- [MyBatis 使用规范](mybatis.md)
