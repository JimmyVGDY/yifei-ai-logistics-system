# 权限配置接口说明

系统使用 Sa-Token 做登录和接口鉴权。权限模型已经从“角色菜单权限”升级为“角色基础权限 + 用户特殊权限覆盖”。

## 权限模型

- `sys_permission`：权限定义表，保存页面、按钮和接口权限，例如 `order:query`、`order:create`、`order:update`、`order:delete`。
- `sys_role_permission`：角色基础权限，一个角色默认拥有的页面和操作能力。
- `sys_user_permission`：用户特殊权限，支持 `GRANT` 额外授权和 `DENY` 单独禁用。

登录时最终权限计算规则：

```text
最终权限 = 角色基础权限 + 用户额外授权 - 用户单独禁用
```

前端菜单根据最终权限渲染，按钮根据最终权限控制显示；后端接口继续通过 Sa-Token 校验，防止绕过前端直接调用。

## 默认业务角色

系统默认准备了一批更贴近物流业务分工的角色：

| 角色编码 | 角色名称 | 典型职责 |
|------|------|------|
| `ADMIN` | 系统管理员 | 全部菜单和全部操作 |
| `OPERATIONS_MANAGER` | 运营主管 | 看板、订单、调度、任务、轨迹、异常统筹 |
| `CUSTOMER_SERVICE` | 客服专员 | 客户资料、运单、轨迹查询和客户侧跟进 |
| `ORDER_OPERATOR` | 订单运营专员 | 订单创建、运单流转和客户订单维护 |
| `DISPATCHER` | 调度人员 | 调度、任务、司机、车辆和异常协同 |
| `FLEET_MANAGER` | 车队管理员 | 司机、车辆、调度任务和轨迹管理 |
| `DRIVER` | 司机 | 运输任务、轨迹、异常上报 |
| `EXCEPTION_HANDLER` | 异常处理专员 | 异常受理、处理和订单任务查询 |
| `FINANCE` | 财务人员 | 费用结算和经营看板 |
| `FINANCE_MANAGER` | 财务主管 | 费用、看板和操作日志审计 |
| `AUDITOR` | 审计只读员 | 关键业务和日志只读查询 |
| `FILE_MANAGER` | 资料管理员 | 上传文件和资源中心 |
| `CUSTOMER` | 客户账号 | 自身订单和轨迹查询 |

## 接口列表

| 方法 | 地址 | 权限 | 说明 |
|------|------|------|------|
| GET | `/system/permissions/menus` | `system:permission:manage` | 查询可见菜单树，兼容旧页面 |
| GET | `/system/permissions/tree` | `system:permission:manage` | 查询页面、按钮、接口权限树 |
| GET | `/system/permissions/roles/{roleId}/permissions` | `system:permission:manage` | 查询角色已分配权限 ID |
| POST | `/system/permissions/roles/{roleId}/permissions` | `system:permission:manage` | 保存角色基础权限 |
| GET | `/system/permissions/users/{userId}/permissions` | `system:permission:manage` | 查询用户额外授权和单独禁用权限 |
| POST | `/system/permissions/users/{userId}/permissions` | `system:permission:manage` | 保存用户特殊权限 |
| GET | `/system/permissions/roles/{roleId}/menus` | `system:permission:manage` | 旧版角色菜单接口，保留兼容 |
| POST | `/system/permissions/roles/{roleId}/menus` | `system:permission:manage` | 旧版角色菜单保存，保留兼容 |

## 请求示例

配置角色权限：

```json
{
  "permissionIds": [260601000000001, 260601000100001]
}
```

配置用户特殊权限：

```json
{
  "grantPermissionIds": [260601000100003],
  "denyPermissionIds": [260601000100004]
}
```

## 数据库迁移

已有本地数据库不需要清库。执行下面的增量脚本即可补齐权限表和默认数据：

```sql
source scripts/sql/20260601_incremental_user_permission.sql;
source scripts/sql/20260601_incremental_enterprise_roles.sql;
```

应用启动时也会以“只创建缺失表、只补齐缺失权限定义”的方式兜底，不会删除已有业务数据。

## 权限控制边界

- 角色权限控制菜单和默认操作权限。
- 用户特殊权限可以在角色基础上增加或禁用具体权限码。
- 前端按钮显隐和后端接口鉴权必须使用同一套权限码，例如 `order:create`、`order:update`、`order:delete`。
- 如果角色或用户被配置为空菜单/空权限，后端不会再回退到默认菜单，避免权限收回失效。
- 客户角色还叠加 `customerId` 数据范围控制，不能只依赖菜单和按钮隐藏。

## 相关文档

- [项目文档索引](README.md)
- [认证接口文档](auth-api.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [前端新人接入手册](frontend.md)
