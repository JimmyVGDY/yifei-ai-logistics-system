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
  "password": "xlh963311213"
}
```

返回包含 `token`、`userId`、脱敏用户名、角色编码、权限码和菜单树。管理员拥有全部菜单和操作权限。

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

## 退出登录

`POST /auth/logout`

退出后后端清理 Sa-Token 会话，前端同步清理本地 token、用户信息和菜单。
