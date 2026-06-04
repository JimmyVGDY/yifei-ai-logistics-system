# 开发规范与约定

> 本文档定义项目开发中必须遵守的编码规范、命名约定、Git 工作流和代码审查流程。所有贡献者在提交代码前必须阅读。

## 编码规范

### Java

- **JDK 版本**：Java 21（兼容性要求）
- **编码格式**：UTF-8（所有 `.java`、`.xml`、`.yml`、`.properties` 文件）
- **缩进**：4 空格（禁止 Tab）
- **注释**：所有类、公共方法、复杂逻辑必须写中文注释，说明"为什么"而非"是什么"
- **日志**：使用 SLF4J（`@Slf4j`），禁止 `System.out.println`

**代码风格示例**：

```java
/**
 * 根据订单号查询订单详情（含缓存回填）。
 * <p>先查 Redis 缓存，缓存未命中时查 MySQL 并回写缓存。</p>
 *
 * @param orderNo 物流订单号
 * @return 订单详情，不存在时返回 null
 */
public LogisticsOrderVO getOrderDetail(String orderNo) {
    // 1. 先查 Redis 缓存，避免直接穿透到 MySQL
    String cacheKey = ORDER_CACHE_PREFIX + orderNo;
    LogisticsOrderVO cached = (LogisticsOrderVO) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }
    // 2. 缓存未命中，查 MySQL
    LogisticsOrder order = orderMapper.selectByOrderNo(orderNo);
    if (order == null) {
        return null;
    }
    LogisticsOrderVO vo = convertToVO(order);
    // 3. 回写缓存（异步不阻塞当前请求）
    redisTemplate.opsForValue().set(cacheKey, vo, 30, TimeUnit.MINUTES);
    return vo;
}
```

### 关键规则

| 规则 | 说明 |
|------|------|
| 金额计算 | 必须使用 `BigDecimal`，禁止 `float`/`double` |
| 集合遍历 | 优先使用传统 `for` 循环，复杂 Stream 需写注释说明意图 |
| 空值处理 | 方法返回值不返回 `null` 时用 `Optional` 或 `Collections.emptyList()` |
| 异常处理 | 不吞异常，不 `catch (Exception e) {}` 留空 |
| 字符串拼接 | 循环内用 `StringBuilder`，简单拼接可用 `+` |

### 安全关键逻辑

| 规则 | 说明 |
|------|------|
| 客户数据隔离 | 客户角色必须按 `customerId` 在后端过滤，不能只靠前端隐藏菜单 |
| 密码写入 | 新增或更新用户密码必须 BCrypt；空密码不得覆盖旧密码 |
| 敏感字段 | 手机号等字段入库前加密，查重使用不可逆 hash，不在日志中输出明文 |
| 删除操作 | 通用 CRUD 只允许逻辑删除；缺少 `deleted` 字段先补迁移脚本 |
| 文件上传 | 必须限制扩展名、大小和导入行数，禁止可执行文件进入上传目录 |
| 权限兜底 | 明确配置为空权限时不能自动回退默认权限 |

## 项目结构约定

```text
src/main/java/jimmy/
├── common/          ← 通用工具、ID 生成器
├── config/          ← Spring Bean 配置、中间件配置
├── controller/      ← HTTP 控制器（不写业务逻辑）
├── entity/          ← 数据库实体（对应表结构）
├── mapper/          ← MyBatis Mapper 接口
├── model/           ← DTO、VO、请求/响应对象
├── service/         ← 业务逻辑层
├── util/            ← 通用工具类
└── logistics/       ← 物流业务模块
    ├── controller/  ← 物流 HTTP 接口
    ├── service/     ← 物流业务逻辑
    ├── mapper/      ← 物流 SQL 映射
    ├── entity/      ← 物流实体类
    ├── model/       ← 物流 DTO/VO
    ├── job/         ← XXL-Job 定时任务
    ├── config/      ← 物流拦截器、上下文
    └── annotation/  ← 物流自定义注解（如 @OperationLog）
```

**分层约束**：

- **Controller**：只做参数校验和响应封装，不写业务逻辑。
- **Service**：承载全部业务逻辑、事务控制、缓存操作、消息发送。
- **Mapper**：只定义 SQL 映射接口，XML 中编写 SQL。
- **Entity / DTO / VO**：允许按现有代码风格使用 Lombok，关键业务字段和校验规则需要保留清晰命名与中文注释。

## Git 工作流

### 分支策略

- `master`：主分支，始终保持可构建、可运行
- `feature/*`：功能开发分支，合并后删除
- `fix/*`：Bug 修复分支

### 提交规范

**格式**：`<type> | <中文描述>`

| type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat \| 新增用户管理模块` |
| `fix` | Bug 修复 | `fix \| 修复订单号重复生成的问题` |
| `refactor` | 代码重构 | `refactor \| 统一脱敏规则为前2后4` |
| `docs` | 文档更新 | `docs \| 新增新手快速上手指南` |
| `test` | 测试相关 | `test \| 补充订单服务单元测试` |
| `chore` | 构建/配置 | `chore \| 升级 Spring Boot 到 3.5.14` |
| `perf` | 性能优化 | `perf \| 订单查询添加复合索引` |

**提交即推送**：`git commit` 后立即 `git push`。

**按功能边界拆分**：一个 commit 只做一件事。不要把一个功能的所有文件和另一个功能的文件混合提交。

## SQL 开发规范

- 所有新业务 SQL 写入 `src/main/resources/mapper/**/*.xml`，不允许在 Service 中直接拼接 SQL。
- Mapper 方法参数必须使用 `@Param` 命名。
- 动态表名、字段名必须来自后端白名单，禁止从前端直接拼入 `${}`。
- 查询条件值一律使用 `#{}` 预编译，防止 SQL 注入。
- AI 临时只读 SQL 属于受控例外，只能通过专用安全网关执行，不能作为普通业务开发写法；具体边界见 [MyBatis 使用规范](mybatis.md)。
- 新增表必须包含 `create_time`、`update_time` 字段。
- 业务表建议包含 `create_by`、`update_by`、`deleted`、`version` 审计字段。

**SQL 审查要点**：

1. 是否命中索引（`EXPLAIN` 分析）
2. 是否存在全表扫描
3. 是否存在隐式类型转换
4. 批量操作是否控制批次大小（单次 INSERT/UPDATE 不超过 500 条）

## 数据库迁移

本地已有数据环境**不自动执行 `schema.sql`**。每次结构变更：

1. 在 `scripts/sql/` 下新建增量脚本，命名格式 `YYYYMMDD_description.sql`
2. 脚本必须可重复执行（`IF NOT EXISTS`、`ADD COLUMN` 前检查列是否存在）
3. 更新 `docs/incremental-migration.md` 添加新脚本到执行列表

## 测试要求

- 新增或修改核心业务逻辑后，必须跑 `mvn test` 确认现有测试通过。
- Service 层核心方法应补充单元测试，放在 `src/test/java/` 下对应包路径。
- 测试类命名：`{ClassName}Test.java`
- 重点回归：登录、权限、订单创建链路（Redis → ES → RabbitMQ → 轨迹初始化）

## 代码审查流程

提交 PR 或合并前，按以下清单自查：

| 检查项 | 说明 |
|--------|------|
| **空指针** | 外部输入、集合元素、链式调用是否判空 |
| **并发** | 共享变量是否线程安全，是否有竞争条件 |
| **SQL** | 是否命中索引，是否用 `#{}`，是否有隐式类型转换 |
| **事务** | `@Transactional` 是否加在正确层级，传播行为是否正确 |
| **性能** | 循环内是否有 DB 调用/远程调用，大集合是否分批处理 |
| **安全** | 敏感日志是否脱敏（`LogMaskUtils`），参数是否校验 |
| **兼容** | 是否使用 Java 21+ 特性，是否兼容现有 JDK 21 |

## 日志安全

所有日志输出中的用户信息、手机号、身份证号必须脱敏：

```java
// ✅ 正确：使用 LogMaskUtils 脱敏
log.info("用户登录成功，userId={}", LogMaskUtils.maskId(String.valueOf(userId)));
log.info("订单创建，客户={}", LogMaskUtils.maskName(customerName));

// ❌ 错误：直接输出敏感信息
log.info("用户登录成功，userId={}, username={}", userId, username);
log.info("订单创建，客户手机={}, 地址={}", phone, address);
```

脱敏规则详见 `LogMaskUtils.java` 的 Javadoc：
- 手机号：前3后4
- 身份证：前6后4
- 姓名/账号/ID/文本：前2后4，中间随机4~8星号
- IP：保留前两段子网
- **数据库保留真值**，仅日志和接口响应脱敏

## 环境变量

所有环境相关配置通过 JVM 参数或环境变量覆盖，不硬编码到 `application.yml`：

```bash
# 指定 MySQL 密码
-Dspring.datasource.password=yourpassword

# 切换到 H2 内存库
-Dspring.profiles.active=h2

# 禁用 XXL-Job
-Dxxl.job.enabled=false
```

## 项目术语表

| 术语 | 含义 |
|------|------|
| `module` | 通用管理页对应的业务模块，如 `orders`、`customers`、`drivers` |
| `snowflake ID` | 15 位十进制业务 ID，格式 `yyMMddHHmmss` + 3 位序列号 |
| `BCrypt` | 密码加密算法，登录成功后自动升级旧明文密码 |
| `BloomFilter` | 布隆过滤器，用于订单号查询预检，降低无效查询 |
| `traceId` | 请求链路追踪 ID，贯穿 HTTP → MQ → XXL-Job |
| `operationId` | 单次操作唯一 ID，用于操作日志审计 |
| `MDC` | Mapped Diagnostic Context，SLF4J 的日志上下文 |

## 相关文档

- [项目文档索引](README.md) — 所有文档入口
- [新手快速上手指南](getting-started.md) — 10 分钟跑通项目
- [本地开发指南](local-development.md) — 中间件启动和环境搭建详解
- [项目结构说明](architecture.md) — 目录结构和分层约定
- [MyBatis 使用规范](mybatis.md) — SQL 编写和动态 SQL 边界
- [数据库增量迁移](incremental-migration.md) — 结构变更流程
- [权限、结构化日志与操作审计说明](logistics-rbac-structured-log.md) — 安全和审计规则
