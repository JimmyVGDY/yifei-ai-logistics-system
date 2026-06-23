---
status: accepted
---

# Java + Python 混合架构：AI 模块迁移到独立 Python 服务

物流管理系统的 AI 模块从 Java（Spring AI）代码迁移到独立的 Python（FastAPI）服务，形成 `Java 业务系统 + Python AI 引擎` 的混合架构。前端仍只调 Java，Java 作为策略决策层决定是否调 Python 以及如何调。Python 只监听 `127.0.0.1`，不直接接受外部请求。

## 边界

**Python 负责**：模型网关、Agent 多轮编排、Prompt 渲染、意图分类、RAG 文档检索、长期记忆智能（提炼 + 向量召回 + Embedding）、文档分块与索引、结构化输出校验、回答幻觉检查。

**Java 负责**：用户认证 (Sa-Token)、权限校验 (RBAC)、业务 Tool Calling 执行、受控 SQL 安全校验、会话与消息存储 (MySQL)、记忆治理（状态机 + 用户确认）、查询游标、敏感数据脱敏、审计日志、文件上传校验、SSE 代理透传、API Key 持有与注入。

**共用基础设施**：MySQL（Java 独占）、Qdrant（Python 独占）、Redis（共享——Java 读任务状态，Python 写进度/PipelineContext）、Ollama（Python 独占，预留）。

## 关键非决定

**不引入 gRPC**：维护成本高（.proto 双端同步）、抓包不可读、SSE 不兼容、一个人为主的项目 HTTP REST 足够。Python 服务只监听 `127.0.0.1:8001`，localhost 延迟 < 1ms。

**不引入 Celery**：当前无 GPU 长耗时任务，不需要优先级队列和并发控制。定时任务复用已有 XXL-Job，长任务走 `POST /ai/internal/tasks/submit` + Redis 状态轮询。当满足以下任一条件时再评估 Celery：(1) 长耗时任务 > 5 种，(2) 需要并发控制，(3) 需要优先级队列，(4) 用户频繁取消进行中任务。

**Qdrant 归 Python 独占**：避免向量维度变更时双端同步、Embedding 调用重复、连接池竞争。Java 通过 HTTP API 调用 Python 的 `/internal/memory/*` 和 `/internal/rag/*` 间接操作 Qdrant。

**Prompt 模板用 Git 文件管理**：不在 MySQL 存储模板。模板放 Python 侧 `prompts/` 目录，与代码同步版本化。安全性更好（不进数据库备份链路、不走 binlog），Git 原生支持回滚和变更审核。
