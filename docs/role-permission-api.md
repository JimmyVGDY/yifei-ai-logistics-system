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
```

应用启动时也会以“只创建缺失表、只补齐缺失权限定义”的方式兜底，不会删除已有业务数据。
