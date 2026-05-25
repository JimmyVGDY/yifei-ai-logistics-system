# 物流管理系统需求匹配说明

本文档根据 `物流管理系统需求说明书 v1.0` 梳理当前项目的落地情况。

## 已匹配模块

| 需求模块 | 当前落地内容 |
| --- | --- |
| 登录认证 | 已接入 Sa-Token，支持管理员登录、退出、会话校验和前端 token 携带 |
| 用户管理 | 新增 `sys_user`、`sys_role`、`sys_menu`、用户角色和角色菜单关联表，并提供用户/角色列表页 |
| 客户管理 | 已有 `logistics_customer` 表，并提供客户列表页 |
| 物流订单 | 已有订单创建、查询和列表接口，订单创建状态调整为 `WAIT_DISPATCH` |
| 运单管理 | 新增 `logistics_waybill` 表和运单中心列表页 |
| 调度管理 | 新增 `logistics_dispatch` 表和调度列表页 |
| 司机管理 | 已有 `logistics_driver` 表，并提供司机列表页 |
| 车辆管理 | 已有 `logistics_vehicle` 表，并提供车辆列表页 |
| 运输任务 | 新增 `logistics_task` 表和运输任务列表页 |
| 物流轨迹 | 新增 `logistics_track` 表和轨迹时间线数据列表页 |
| 异常管理 | 新增 `logistics_exception` 表和异常列表页，并提供异常上报、关闭处理接口 |
| 费用结算 | 新增 `logistics_fee` 表和费用结算列表页，并提供费用生成、收款确认接口 |
| 数据统计 | 新增 `/logistics/dashboard` 概览接口和趋势接口，前端仪表盘展示订单、异常和收入统计 |
| 文件上传 | 新增业务附件上传接口和 `sys_uploaded_file` 文件记录表 |
| Excel 导入导出 | 支持模块列表导出 `.xlsx`，客户基础资料支持 `.xlsx` 导入 |
| 操作日志 | 新增操作日志拦截器，物流写操作和重点接口写入 `sys_operation_log` |

## 新增后端接口

```text
GET /logistics/dashboard
GET /logistics/modules/{module}?limit=20
POST /logistics/exceptions/report
POST /logistics/exceptions/{exceptionId}/handle
POST /logistics/fees/generate/{orderNo}
POST /logistics/fees/{feeId}/pay
GET /logistics/statistics/order-trend?days=7
GET /logistics/statistics/income-trend?months=6
POST /logistics/files/upload
GET /logistics/excel/export/{module}?limit=100
POST /logistics/excel/import/customers
```

`module` 当前支持：

```text
customers
orders
waybills
dispatches
tasks
tracks
drivers
vehicles
exceptions
fees
users
roles
operationLogs
files
```

## 前端页面

侧边栏已按需求书扩展：

- 运营看板
- 运单管理
- 新建运单
- 客户管理
- 运单中心
- 调度管理
- 运输任务
- 物流轨迹
- 司机管理
- 车辆管理
- 异常管理
- 费用结算
- 上传文件
- 系统管理
  - 用户管理
  - 角色管理
  - 操作日志
- 资源中心

## 当前边界

本次已完成需求书 v2.0 的基础能力框架：异常处理、费用收款、统计趋势、文件上传、Excel 导入导出和操作日志。后续可以继续深化为完整 CRUD、细粒度角色权限、Excel 模板下载、上传文件预览、操作日志详情和审计检索。
