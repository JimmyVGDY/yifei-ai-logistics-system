# 认证接口文档

## 统一说明

所有响应统一为：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

除登录和健康检查外，接口需要携带 Sa-Token。前端会在登录后自动把 token 放入请求头。

## 登录

`POST /auth/login`

请求体：

```json
{
  "username": "admin",
  "password": "your-password"
}
```

返回包含 `token`、`userId`、脱敏用户名、角色编码、权限码和菜单树。管理员拥有全部菜单和操作权限。

权限和菜单来自数据库 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu`、`sys_role_permission` 和 `sys_user_permission`。如果角色被明确配置为空菜单，后端不会再使用默认菜单兜底，避免权限被收回后仍能看到页面。

登录采用单账号单会话策略，并增加登录冲突确认流程：

- 同一账号已在线时，新登录不会立刻顶掉旧会话。
- 后端会创建 60 秒登录冲突确认请求。
- 旧会话页面会弹窗提示“同一账号正在其他地方登录”。
- 旧会话点击“保持当前登录”会拒绝新登录。
- 旧会话不处理或允许新登录时，新登录会在确认窗口结束后生效，旧 token 随后失效。

相关接口：

```text
GET  /auth/login-conflicts/{conflictId}/status
GET  /auth/login-conflicts/current
POST /auth/login-conflicts/{conflictId}/reject
```

## 当前会话

`GET /auth/session`

用于刷新页面后恢复用户、角色、权限和菜单。

客户角色会话中必须包含 `customerId`，业务接口据此做数据隔离。客户账号未绑定客户档案时，后端会拒绝订单、看板、轨迹、异常、费用等业务查询。

## 退出登录

`POST /auth/logout`

退出后后端清理 Sa-Token 会话，前端同步清理本地 token、用户信息和菜单。

## 密码与资料安全

- 系统兼容旧明文密码；用户登录成功后会自动升级为 BCrypt。
- 通用用户管理和客户账号创建写入密码时会使用 BCrypt，空密码不会覆盖旧密码。
- 手机号等敏感字段入库前加密，资料更新会同步写入 `mobile_hash` 供查重使用。

## 相关文档

- [项目文档索引](README.md)
- [权限配置接口说明](role-permission-api.md)
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md)
- [用户接口文档](user-api.md)
