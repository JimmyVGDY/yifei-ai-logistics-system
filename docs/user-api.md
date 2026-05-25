# 用户接口文档

## 用户管理

后台用户通过通用管理接口维护：

`GET /logistics/modules/users?page=1&pageSize=20&keyword=admin&startTime=2026-05-01 00:00:00&endTime=2026-05-31 23:59:59`

`POST /logistics/modules/users`

`POST /logistics/modules/users/{id}`

`POST /logistics/modules/users/{id}/delete`

## 字段说明

| 字段 | 说明 |
| --- | --- |
| `user_code` | 用户业务编号，前端优先展示 |
| `username` | 登录账号 |
| `real_name` | 姓名，日志中会脱敏 |
| `mobile` | 手机号 |
| `email` | 邮箱 |
| `password` | 密码，兼容旧明文，后续可单独升级为强制 BCrypt |
| `role_id` | 角色 ID |
| `status` | 状态，前端展示为中文 |

## 更新规则

编辑接口只更新请求体中明确传入的字段。未传入字段保持原值；传入 `null` 时，只有后端配置允许清空的字段才会写入空值。
