# 物流数据库说明

物流管理系统使用独立数据库：

```text
database: logistics_management
host: 127.0.0.1
port: 3306
username: root
password: 空（按实际配置）
```

## 核心表

### 物流业务表

- `logistics_customer`: 客户档案。
- `logistics_warehouse`: 仓库档案。
- `logistics_driver`: 司机档案。
- `logistics_vehicle`: 车辆档案。
- `logistics_route`: 运输路线。
- `logistics_order`: 物流订单主表。
- `logistics_waybill`: 运单表。
- `logistics_dispatch`: 调度表。
- `logistics_task`: 运输任务表。
- `logistics_track`: 物流轨迹表。
- `logistics_exception`: 运输异常表。
- `logistics_fee`: 费用结算表。
- `logistics_inventory`: 仓库库存表。

### 系统表

- `sys_user`: 系统用户（含 `password` 字段，支持 BCrypt 自动升级）。
- `sys_role`: 角色定义。
- `sys_menu`: 菜单/权限。
- `sys_user_role`: 用户-角色关联。
- `sys_role_menu`: 角色-菜单关联。
- `sys_operation_log`: 操作审计日志，含 `error_message` 字段用于排障。
- `sys_uploaded_file`: 上传文件记录。

### 增量字段说明

`sys_operation_log` 表在基础字段外，通过增量迁移脚本补齐：

- `operation_id`、`trace_id`：请求链路追踪
- `user_id`、`user_code`、`role_code`：操作人身份
- `cost_ms`：接口耗时
- `error_message`：异常信息（接口报错时自动记录异常原因）
- `client_ip`、`user_agent`：客户端来源和浏览器标识
- `request_params`：安全请求参数摘要，密码、token 等敏感参数不会记录
- `target_id`：操作对象 ID，例如记录 ID、角色 ID、订单号
- `create_by`、`update_by`、`deleted`、`version`：通用审计字段

## 模拟数据规模

初始化脚本将写入：客户 100 条、订单/运单/调度/任务/轨迹/费用各 100 条，适合本地接口联调和演示。

## 扩展模拟数据

```bash
mysql -uroot logistics_management < scripts/sql/seed-logistics-mock-100.sql
```

## 刷新可读演示名称

```bash
mysql -uroot logistics_management < scripts/sql/refresh-logistics-readable-names.sql
```

## 兼容旧库补丁

如果本地已经执行过旧增量脚本，可执行下面脚本补齐操作日志 `error_message` 字段，并清理已下线的结构化日志菜单：

```bash
mysql -uroot logistics_management < scripts/sql/20260531_cleanup_structured_log_and_operation_log.sql
```

如果需要增强操作日志审计上下文，可继续执行：

```bash
mysql -uroot logistics_management < scripts/sql/20260602_incremental_operation_log_context.sql
```
