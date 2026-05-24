# 物流数据库说明

物流管理系统使用独立数据库：

```text
database: logistics_management
host: 127.0.0.1
port: 3306
username: root
password: 空
```

项目启动时会通过 Spring SQL Init 自动执行：

```text
src/main/resources/schema.sql
src/main/resources/data.sql
```

## 核心表

- `logistics_customer`: 客户档案。
- `logistics_warehouse`: 仓库档案。
- `logistics_driver`: 司机档案。
- `logistics_vehicle`: 车辆档案。
- `logistics_route`: 运输路线。
- `logistics_order`: 物流订单主表。
- `logistics_order_tracking`: 订单轨迹。
- `logistics_inventory`: 仓库库存。
- `logistics_freight_bill`: 运费账单。

## 模拟数据规模

当前初始化脚本会写入：

- 4 个客户
- 3 个仓库
- 3 个司机
- 3 辆车
- 3 条路线
- 3 个订单
- 4 条轨迹
- 3 条库存
- 3 条运费账单

数据量不大，适合本地接口联调和后续继续扩展业务模块。

## 扩展模拟数据

如果需要把每张物流主表扩展到 100 条以上的演示规模，可以执行：

```bash
F:\Development\Database\MySQL\Server-8.4.9\bin\mysql.exe -uroot logistics_management < scripts/sql/seed-logistics-mock-100.sql
```

脚本会生成 `MOCK-*` 前缀的数据，并且可以重复执行；已有相同业务编号的数据不会重复插入。
