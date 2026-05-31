# 物流管理系统接口文档

## 通用说明

除 `/auth/login`、`/actuator/health` 外，接口默认需要登录。登录成功后前端会把 Sa-Token 返回的 token 放入请求头。

统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页结构：

```json
{
  "total": 100,
  "page": 1,
  "pageSize": 20,
  "records": []
}
```

## 认证接口

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 无 | 登录，返回 token、用户信息、权限码和菜单 |
| GET | `/auth/session` | 登录 | 获取当前登录会话 |
| POST | `/auth/logout` | 登录 | 退出登录 |

登录请求：

```json
{
  "username": "admin",
  "password": "xlh963311213"
}
```

## 管理模块接口

`module` 支持：

```text
orders, customers, waybills, dispatches, tasks, tracks, drivers, vehicles,
exceptions, fees, users, roles, operationLogs, files
```

| 方法 | 地址 | 权限格式 | 说明 |
| --- | --- | --- | --- |
| GET | `/logistics/modules/{module}` | `{模块}:query` | 分页查询 |
| POST | `/logistics/modules/{module}` | `{模块}:create` | 新增记录 |
| POST | `/logistics/modules/{module}/{id}` | `{模块}:update` | 修改记录，只更新请求体中明确传入的字段 |
| POST | `/logistics/modules/{module}/{id}/delete` | `{模块}:delete` | 删除记录，优先逻辑删除 |

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码，默认 1 |
| `pageSize` | 否 | 每页数量，默认 20，最大 100 |
| `keyword` | 否 | 关键词模糊查询 |
| `startTime` | 否 | 开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `endTime` | 否 | 结束时间，格式 `yyyy-MM-dd HH:mm:ss` |

更新规则：

- 未传入的字段保持原值。
- 传入 `null` 时，只有后端配置为允许清空的字段才会写入空值。
- 运单的货物名称、重量、体积、计划提货时间、计划送达时间、线路、仓库、司机、车辆允许后补。

权限模块映射：

| module | 权限前缀 |
| --- | --- |
| orders | `order` |
| customers | `customer` |
| waybills | `waybill` |
| dispatches | `dispatch` |
| tasks | `task` |
| tracks | `track` |
| drivers | `driver` |
| vehicles | `vehicle` |
| exceptions | `exception` |
| fees | `fee` |
| users | `system:user` |
| roles | `system:role` |
| operationLogs | `system:log` |
| files | `file` |

## 物流订单接口

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/logistics/orders` | `order:create` | 创建物流订单 |
| GET | `/logistics/orders` | `order:query` | 查询最近订单 |
| GET | `/logistics/orders/{orderNo}` | `order:query` | 查询订单详情 |
| GET | `/logistics/orders/search` | `order:query` | Elasticsearch 订单搜索 |

创建订单必填：客户名称、发货地址、收货地址。货物名称、重量、体积和计划时间允许暂缺，后续可在运单管理里补充。

订单搜索参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码，默认 1 |
| `pageSize` | 否 | 每页数量，默认 20，最大 100 |
| `keyword` | 否 | 按订单号、客户、收货地址、货物名搜索 |

订单创建会执行业务链路：

```text
写入 MySQL
→ 写入 BloomFilter
→ 写入 Redis 缓存
→ 写入 Elasticsearch 索引
→ 发送 RabbitMQ 订单创建事件
→ 消费事件后初始化物流轨迹
```

## 业务扩展接口

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/logistics/exceptions/report` | `exception:create` | 上报运输异常 |
| POST | `/logistics/exceptions/{exceptionId}/handle` | `exception:update` | 处理运输异常 |
| POST | `/logistics/fees/generate/{orderNo}` | `fee:create` | 生成订单费用 |
| POST | `/logistics/fees/{feeId}/pay` | `fee:update` | 标记费用已付款 |
| GET | `/logistics/statistics/order-trend` | `dashboard:view` | 订单趋势 |
| GET | `/logistics/statistics/income-trend` | `dashboard:view` | 收入趋势 |
| GET | `/logistics/excel/export/{module}` | `{模块}:export` | 导出模块 Excel |
| POST | `/logistics/excel/import/customers` | `customer:import` | 导入客户 Excel |
| POST | `/logistics/files/upload` | `file:create` | 上传业务文件 |

异常处理请求：

```json
{ "exceptionStatus": "PROCESSING" }
```

表示“开始处理”。异常进入处理中后，再提交：

```json
{ "exceptionStatus": "CLOSED" }
```

表示“处理完成”。已处理异常不能重复处理。

## 错误码

| code | 说明 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误或业务校验失败 |
| 401 | 未登录或无权限 |
| 500 | 系统异常 |

## 监控与运维接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/prometheus` | Prometheus 指标（JVM/HTTP/GC/HikariCP） |
| GET | `/infra/status` | 中间件连接状态 |
