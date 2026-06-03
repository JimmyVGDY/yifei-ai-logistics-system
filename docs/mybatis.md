# MyBatis 使用规范

本项目后端业务 SQL 统一使用 `Mapper.java + mapper/*.xml` 的方式维护。Service 层只负责业务编排、参数校验、事务控制、日志记录和少量安全白名单配置，不再直接拼写业务查询、插入、更新或删除 SQL。

## 文件位置

```text
src/main/java/jimmy/**/mapper/*.java
src/main/resources/mapper/**/*.xml
```

新增业务 SQL 时，应先创建 Mapper 接口，再在对应 XML 中编写 SQL。Mapper 方法参数需要使用 `@Param` 明确命名，便于 XML 可读和后续维护。

## 已迁移模块

- 登录、会话、角色菜单、密码升级：`AuthMapper.xml`
- 权限菜单树、角色菜单保存：`SystemPermissionMapper.xml`
- 订单创建、详情、最近订单、状态更新：`LogisticsOrderMapper.xml`
- 操作日志写入：`OperationLogMapper.xml`
- 费用生成、费用列表、收款更新：`LogisticsFeeMapper.xml`
- 异常上报、异常处理、订单和任务查询：`LogisticsExceptionMapper.xml`
- 轨迹是否存在、轨迹初始化：`LogisticsTrackMapper.xml`
- 看板统计、状态分布、最近异常：`LogisticsDashboardMapper.xml`
- 订单趋势、收入趋势：`LogisticsStatisticsMapper.xml`
- 通用 CRUD 新增、更新、删除、编号检查：`LogisticsCrudMapper.xml`
- 模块列表分页、关键词查询、时间范围查询：`LogisticsModuleQueryMapper.xml`
- 文件上传记录、客户导入：`LogisticsFileMapper.xml`、`LogisticsCustomerImportMapper.xml`
- 练习用户示例：`DemoUserMapper.xml`

## 动态 SQL 边界

通用管理页和模块分页会涉及动态模块、动态字段和动态排序。这里允许 XML 中使用 `${}` 承接表名或字段名，但必须满足以下要求：

- 表名、字段名、排序字段只能来自后端白名单配置。
- 前端只能传业务参数，例如 `page`、`pageSize`、`keyword`、`startTime`、`endTime`。
- 关键词、时间范围、ID、状态等值必须使用 `#{}` 预编译参数。
- 新增和更新接口必须过滤不可写字段，例如 `id`、`create_by`、`update_by`、`deleted`、`version`、创建时间字段。
- 删除优先逻辑删除；表结构未迁移出 `deleted` 字段时，才允许通过封装好的兼容逻辑回退物理删除。

## 允许例外

`JdbcTemplate` 只允许用于数据库元信息检测，例如判断某张表是否存在 `deleted` 或 `user_code` 字段。该逻辑不属于业务 SQL，且必须集中封装，不能散落到 Controller 或 Service 的业务流程里。

除元信息检测外，新增业务查询、写入、更新、删除、统计聚合都应进入 Mapper XML。

## 暂不引入 MyBatis-Plus 的原因

当前系统存在动态模块查询、字段白名单、逻辑删除兼容、报表聚合、多表关联和增量迁移兼容逻辑。直接全面引入 MyBatis-Plus 的 BaseMapper 容易绕开现有安全边界。

后续如果客户、司机、车辆等单表 CRUD 边界稳定，可以再按模块局部评估 MyBatis-Plus。

## 验证要求

每迁移一批 SQL 后至少执行：

```text
mvn test
```

重点回归：

- 登录、退出、刷新会话、角色权限配置。
- 新增和处理异常，确认看板同步变化。
- 生成费用、标记收款。
- 创建订单后的 RabbitMQ 消费、轨迹初始化和 ES 索引链路。
- 看板统计、订单趋势、收入趋势。
- 通用管理页新增、编辑、删除、分页、模糊查询、时间范围查询。

## 相关文档

- [项目文档索引](README.md)
- [开发规范与约定](development-guide.md)
- [项目结构说明](architecture.md)
- [物流数据库说明](logistics-database.md)
- [数据库增量迁移说明](incremental-migration.md)
