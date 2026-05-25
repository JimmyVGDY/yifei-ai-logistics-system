# 角色权限接口文档

## 角色管理

角色通过通用管理接口维护：

`GET /logistics/modules/roles?page=1&pageSize=20&keyword=ADMIN`

`POST /logistics/modules/roles`

`POST /logistics/modules/roles/{id}`

`POST /logistics/modules/roles/{id}/delete`

## 权限模型

系统使用 Sa-Token 做登录和权限校验。菜单权限来自 `sys_menu` 和 `sys_role_menu`，登录后返回给前端渲染侧边栏。

模块级权限会派生按钮级权限：

| 模块权限 | 派生操作 |
| --- | --- |
| `order:manage` | `order:query/create/update/delete/export/import` |
| `customer:manage` | `customer:query/create/update/delete/export/import` |
| `track:view` | `track:query/export` |

接口侧也会校验操作级权限，例如新增运单需要 `order:create`，删除客户需要 `customer:delete`，费用收款需要 `fee:update`。

## 默认职责

| 角色 | 可见范围 |
| --- | --- |
| 管理员 | 全部菜单和操作 |
| 客服 | 客户管理、运单管理、运单中心、物流轨迹 |
| 调度 | 调度管理、运输任务、司机管理、车辆管理、物流轨迹、异常管理 |
| 司机 | 运输任务、物流轨迹、异常管理 |
| 财务 | 费用结算、运营看板 |
| 客户 | 运单查询、物流轨迹 |
