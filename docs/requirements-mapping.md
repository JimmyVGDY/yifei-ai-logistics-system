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
| 操作日志 | 新增操作日志拦截器，物流写操作写入 `sys_operation_log`，含 `error_message` 字段支持排障 |
| 中间件业务链路 | 创建订单后写 Redis 缓存、BloomFilter、ES 索引，发送 RabbitMQ 事件并初始化物流轨迹 |
| 管理页 CRUD | 客户、订单、运单、调度、任务、轨迹、司机、车辆、异常、费用、用户、角色页面支持新增、编辑、删除 |
| 查询增强 | 管理页支持关键词模糊查询、开始时间查询、结束时间查询和时间范围查询 |

## 新增后端接口

```text
GET /logistics/dashboard
GET /logistics/modules/{module}?page=1&pageSize=20&keyword=上海&startTime=2026-05-01 00:00:00&endTime=2026-05-31 23:59:59
POST /logistics/modules/{module}
POST /logistics/modules/{module}/{id}
POST /logistics/modules/{module}/{id}/delete
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
customers / orders / waybills / dispatches / tasks / tracks
drivers / vehicles / exceptions / fees
users / roles / operationLogs / files
```

## 前端页面

侧边栏菜单结构：

- 运营看板
- 运单管理
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
  - 用户管理
  - 角色管理
  - 权限配置
  - 操作日志（含审计+排障 error_message）
- 上传文件
- 资源中心

管理页面的时间字段统一按 `yyyy-MM-dd HH:mm:ss` 展示。

管理页按钮会按登录用户权限码控制显示。权限码已从模块级 `order:manage` 扩展为操作级 `order:query`、`order:create`、`order:update`、`order:delete`、`order:export` 等。

## 运维基础设施

| 组件 | 说明 |
|------|------|
| Docker Compose | 13 服务全栈编排（MySQL/Redis/RabbitMQ/ES/Nacos/Sentinel/App/Prometheus/Grafana/Filebeat/Kibana/XXL-Job） |
| Prometheus | 应用指标采集（JVM/HTTP/GC），告警规则 4 条 |
| Grafana | 预置仪表盘（QPS/P99/错误率/堆内存/GC） |
| Filebeat | 日志采集写入 ES |
| Kibana | 日志检索与可视化 |
| XXL-Job | 定时任务：合同到期预警/月度收入报表/文件清理/缓存预热 |

## 当前边界

本次已完成需求书 v2.0 的基础能力框架 + DevOps 补齐：异常处理、费用收款、统计趋势、文件上传、Excel 导入导出、操作审计（含排障）、管理页基础 CRUD、组合查询、真实分页、按钮级权限显示、订单创建中间件链路、Docker 容器化、全栈监控、集中日志、定时任务和跨平台运维脚本。后续可以继续深化为 Excel 模板下载、上传文件预览、ES 高亮搜索和更细颗粒度的数据权限。
