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
| `password` | 密码，新增/更新时后端写入 BCrypt；空密码不会覆盖旧密码 |
| `role_id` | 角色 ID |
| `status` | 状态，前端展示为中文 |

## 更新规则

编辑接口只更新请求体中明确传入的字段。未传入字段保持原值；传入 `null` 时，只有后端配置允许清空的字段才会写入空值。

## 客户账号规则

客户账号建议走独立客户账号创建流程，而不是和内部员工账号完全混用：

- 个人客户账号：按手机号查重，一个个人客户只允许一个账号。
- 企业客户账号：企业主账号按公司名称查重，后续可扩展为统一社会信用代码校验；企业允许多个子账号。
- 客户账号会绑定 `customer_id`，用于订单、轨迹、异常、费用等数据隔离。
- 新增客户账号时，系统会按客户名称尝试匹配历史订单和客户档案，并回填订单 `customer_id`。

## 敏感字段

- `mobile` 入库存储密文，新写入为 `ENCGCM:`，旧 `ENC:` 数据继续兼容。
- `mobile_hash` 是不可逆查询摘要，用于手机号查重，不用于展示。
- 前端展示手机号、姓名、操作人等敏感信息时应使用后端已脱敏结果或统一脱敏工具。

## 相关文档

- [项目文档索引](README.md)
- [认证接口文档](auth-api.md)
- [权限配置接口说明](role-permission-api.md)
- [物流数据库说明](logistics-database.md)
