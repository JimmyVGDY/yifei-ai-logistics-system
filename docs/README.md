# 项目文档索引

本目录存放物流管理系统的架构、开发、接口、数据库、权限、安全、日志、运维和后续扩展设计文档。新开发者建议按下列顺序阅读。

## 新人接入

| 文档 | 用途 |
| --- | --- |
| [getting-started.md](getting-started.md) | 从拉取代码到启动前后端的快速上手指南 |
| [development-guide.md](development-guide.md) | 编码规范、Git 提交规范、SQL 规范、测试要求和代码审查清单 |
| [local-development.md](local-development.md) | 本地开发环境、中间件、IDE 和启动参数说明 |
| [environment-versions.md](environment-versions.md) | JDK、Maven、Node、MySQL、Redis、RabbitMQ、ES、Nacos、Sentinel、XXL-Job 和 Docker 镜像版本清单 |
| [configuration.md](configuration.md) | Spring Boot、Nacos、Redis、RabbitMQ、ES、XXL-Job 等配置说明 |

## 系统设计

| 文档 | 用途 |
| --- | --- |
| [architecture.md](architecture.md) | 项目目录、后端分层、前端结构和组件说明 |
| [requirements-mapping.md](requirements-mapping.md) | 物流管理需求与当前实现模块的对应关系 |
| [frontend.md](frontend.md) | 前端新人接入手册，覆盖 Vue3 工程、路由、权限、接口、通用管理页和开发检查清单 |
| [mybatis.md](mybatis.md) | MyBatis Mapper XML 使用规范和 SQL 维护约定 |

## 业务与接口

| 文档 | 用途 |
| --- | --- |
| [logistics-api.md](logistics-api.md) | 物流业务接口说明 |
| [auth-api.md](auth-api.md) | 登录认证、会话和权限相关接口说明 |
| [role-permission-api.md](role-permission-api.md) | 角色权限、用户特殊权限和菜单权限接口说明 |
| [user-api.md](user-api.md) | 用户与客户账号相关接口说明 |

## 数据库与迁移

| 文档 | 用途 |
| --- | --- |
| [logistics-database.md](logistics-database.md) | 物流业务表、系统表和关键字段说明 |
| [incremental-migration.md](incremental-migration.md) | 保留现有数据的增量迁移脚本执行说明 |

## 安全、日志和审计

| 文档 | 用途 |
| --- | --- |
| [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md) | RBAC 权限、结构化日志和操作审计说明 |
| [trace-context-audit.md](trace-context-audit.md) | `traceId`、`operationId`、`loginSessionId` 链路追踪和会话审计说明 |

## 后续扩展

| 文档 | 用途 |
| --- | --- |
| [ai-assistant-design.md](ai-assistant-design.md) | AI 助手接入可行性、现有组件编排、权限审计和分阶段落地方案 |

## 阅读建议

- 新开发者先看“新人接入”和“系统设计”，搭环境前先确认 [environment-versions.md](environment-versions.md)，前端开发者还应重点阅读 [frontend.md](frontend.md)，再按负责模块阅读接口和数据库文档。
- 做后端业务开发前必须阅读 [development-guide.md](development-guide.md) 和 [mybatis.md](mybatis.md)。
- 做权限、日志、审计相关功能前必须阅读 [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md) 和 [trace-context-audit.md](trace-context-audit.md)。
- 设计 AI 助手或智能排障能力前必须阅读 [ai-assistant-design.md](ai-assistant-design.md)。

## 文档维护约定

- 所有 Markdown 文档使用 UTF-8 编码；如果 PowerShell 控制台显示乱码，以编辑器、Gitee 页面或 UTF-8 读取工具为准。
- 每个专题文档底部保留“相关文档”小节，至少回链到本索引，避免单个文档孤立。
- 新增接口、表结构、权限码、中间件链路或运维脚本时，需要同步更新对应专题文档和本索引。
- 已有 MySQL 数据库不通过应用启动自动重建；涉及字段变更时优先新增 `scripts/sql/*incremental*.sql`，并在 [incremental-migration.md](incremental-migration.md) 中登记。
- 文档示例不写真实生产密码、token、accessToken；本地默认值以配置文件和环境变量为准。

## 最近安全增强

- 客户角色数据隔离已覆盖通用模块列表、看板、订单详情、近期订单和 ES 订单搜索；客户账号缺少 `customer_id` 绑定时禁止查询业务数据。
- 新写入的手机号等敏感字段使用 `ENCGCM:` AES-GCM 密文，旧 `ENC:` 密文继续兼容；`sys_user.mobile_hash` 用于不可逆查重。
- 通用用户管理写入密码时自动 BCrypt，空密码不会覆盖旧密码。
- 通用删除只允许逻辑删除，缺少 `deleted` 字段时要求先执行增量迁移。
- 练习和中间件示例接口已接入 Sa-Token 权限映射，不再只是登录即可访问。

相关说明见 [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md)、[logistics-database.md](logistics-database.md)、[incremental-migration.md](incremental-migration.md) 和 [configuration.md](configuration.md)。
