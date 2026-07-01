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

AI 助手的临时只读 SQL 网关是另一个受控例外：`AiGeneratedSqlQueryService` 允许模型生成候选 `SELECT`，但执行前必须先让模型按白名单 schema 自检修正，再经过 `AiSqlSafetyValidator` 和数据库 `EXPLAIN` 语法预检；如果语法预检失败，会让模型按错误摘要自动纠错，最多重试 3 次，且每次纠错后都重新执行安全校验。校验规则包括单条 `SELECT`、禁止写关键字、禁止注释和多语句、禁止 `select *`、禁止子查询、禁止 `UNION`、禁止逗号连表、只允许模型可直查白名单表字段、每张表都要求当前账号具备对应查询权限，业务敏感列还必须通过列权限校验，并在顶层追加或收紧 `limit 20`。SQL 执行后还会按结果列来源再次做列权限过滤，避免通过别名绕过字段权限。该例外只服务自然语言临时统计、聚合、排名、关联和连表分析，不能推广到普通业务开发，也不能承接“查看全部订单/运输任务/费用”等普通明细列表。

临时 SQL schema 必须使用数据库真实物理表名和物理列名，不能使用前端展示字段、VO 别名或接口聚合字段。例如费用表应使用 `payable_fee/actual_fee`，车辆表应使用 `load_capacity_kg/volume_capacity_cubic`，运单表没有 `order_no` 时需要通过 `order_id` 关联 `logistics_order`。普通字段的返回列建议保持原字段名，聚合统计列可使用安全英文别名；用户界面的中文展示由 AI 回答层和前端 sanitizer 完成，不能把物理字段名、SQL 文本或内部工具名直接返回给用户。

Spring AI Tool Calling 中的普通业务查询不属于 SQL 例外：`AiBusinessQueryTools` 只负责把模型选择的只读工具参数交给 `AiReadonlyQueryService`，最终仍然复用 `LogisticsRequirementService.modulePage()`、`LogisticsModuleQueryMapper.xml` 和后端白名单。全场景模糊搜索、自动联合查询也只是组合调用已有白名单模块，不允许模型自由拼接业务 SQL。

除元信息检测和 AI 受控临时只读 SQL 外，新增业务查询、写入、更新、删除、统计聚合都应进入 Mapper XML。

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
- [Spring AI 接入说明](spring-ai.md)
