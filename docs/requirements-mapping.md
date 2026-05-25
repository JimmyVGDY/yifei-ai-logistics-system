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
| 异常管理 | 新增 `logistics_exception` 表和异常列表页 |
| 费用结算 | 新增 `logistics_fee` 表和费用结算列表页 |
| 数据统计 | 新增 `/logistics/dashboard` 概览接口，前端仪表盘展示订单、异常和收入统计 |

## 新增后端接口

```text
GET /logistics/dashboard
GET /logistics/modules/{module}?limit=20
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
- 系统管理
- 资源中心

## 当前边界

本次先完成需求书的主数据表、模拟数据、查询接口和页面骨架匹配。新增、修改、删除、角色权限细粒度控制、操作日志切面、文件上传、Excel 导入导出等能力适合后续按模块继续深化。
