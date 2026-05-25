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

## 当前会话

`GET /auth/session`

用于刷新页面后恢复用户、角色、权限和菜单。

## 退出登录

`POST /auth/logout`

退出后后端清理 Sa-Token 会话，前端同步清理本地 token、用户信息和菜单。
