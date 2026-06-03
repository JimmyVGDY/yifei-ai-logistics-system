# 项目文档索引

本目录存放物流管理系统的架构、开发、接口、数据库、权限、安全、日志、运维和后续扩展设计文档。新开发者建议按下列顺序阅读。

## 新人接入

| 文档 | 用途 |
| --- | --- |
| [getting-started.md](getting-started.md) | 从拉取代码到启动前后端的快速上手指南 |
| [development-guide.md](development-guide.md) | 编码规范、Git 提交规范、SQL 规范、测试要求和代码审查清单 |
| [local-development.md](local-development.md) | 本地开发环境、中间件、IDE 和启动参数说明 |
| [configuration.md](configuration.md) | Spring Boot、Nacos、Redis、RabbitMQ、ES、XXL-Job 等配置说明 |

## 系统设计

| 文档 | 用途 |
| --- | --- |
| [architecture.md](architecture.md) | 项目目录、后端分层、前端结构和组件说明 |
| [requirements-mapping.md](requirements-mapping.md) | 物流管理需求与当前实现模块的对应关系 |
| [frontend.md](frontend.md) | Vue3 前端结构、路由、权限和页面组件说明 |
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

- 新开发者先看“新人接入”和“系统设计”，再按负责模块阅读接口和数据库文档。
- 做后端业务开发前必须阅读 [development-guide.md](development-guide.md) 和 [mybatis.md](mybatis.md)。
- 做权限、日志、审计相关功能前必须阅读 [logistics-rbac-structured-log.md](logistics-rbac-structured-log.md) 和 [trace-context-audit.md](trace-context-audit.md)。
- 设计 AI 助手或智能排障能力前必须阅读 [ai-assistant-design.md](ai-assistant-design.md)。
