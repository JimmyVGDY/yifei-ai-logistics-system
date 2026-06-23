# 物流管理系统 — Java Backend

物流业务系统的 Java 后端，作为前端唯一入口（BFF），负责用户认证、权限校验、业务数据管理、AI 策略决策和代理转发。

## 架构角色

**Python AI Service**:
Java 后端通过 HTTP (127.0.0.1) 调用的独立 Python 服务，负责模型调用、Agent 编排、RAG 文档检索、长期记忆智能和 Prompt 渲染。只接受 Java 侧的内部请求，不直接对前端暴露。
_Avoid_: AI 模块, AI 层, Python 端

**ModelPolicyDecider**:
Java 侧的策略决策组件。根据任务类型、用户配额、模型健康状态决定本次 AI 请求使用哪个模型提供商和模型。结果注入 HTTP Header 传给 Python AI Service。Python 侧不持有 API Key——Key 由 Java 在策略允许时注入。
_Avoid_: 策略层, 路由决策器

**Tool Executor**:
Java 侧统一的业务工具执行端点 (`/ai/internal/tool/execute`)。Agent 循环中每轮 Tool Call 由 Python 通过此端点回调 Java 执行。返回的每条数据都已经过 Sa-Token 权限校验和数据范围过滤。Python 不直接访问数据库。
_Avoid_: 工具回调, 业务回调接口

**SSE Proxy**:
Java 透传 Python SSE 事件流到前端的机制。Java 不做事件解析，只逐事件转发。断连时级联关闭 Python 侧连接。OTel `traceparent` 自动在 Java→Python HTTP Header 中传播，无需手动设置 X-Trace-Id。
_Avoid_: 流式转发, EventStream 代理

## 记忆系统

**Memory Governance (Java)**:
记忆的治理层——MySQL 存储记忆真值 (`ai_user_memory`)，管理记忆状态机（CANDIDATE → ACTIVE / REJECTED / SUPERSEDED / ARCHIVED），处理用户批准/拒绝/删除/清空操作。记忆必须人工确认后生效。
_Avoid_: 记忆管理, 记忆CRUD

**Memory Intelligence (Python)**:
记忆的智能层——LLM 从对话中提炼候选记忆 (`MemoryExtractor`)，双层记忆召回与冲突消解 (`MemoryMergeService`)，向量 Embedding 生成与 Qdrant 写入 (`MemoryVectorEncoder`)。
_Avoid_: 记忆自动提取, 记忆召回

## 模型层

**ModelGateway (Python)**:
Python 侧统一模型调用入口。封装 Provider 选择、降级链、重试、超时、结构化输出校验。根据 Java 传入的 `X-Model-Policy` Header 决定走哪个 Provider，不持有 API Key。
_Avoid_: 模型客户端, LLM 调用层

**Prompt Template**:
Prompt 模板以 YAML 文件形式存储在 Python 侧的 `prompts/` 目录下，Git 版本化。每个模板声明 `required_variables`、`optional_variables`、`output_schema`、推荐模型和温度。Mustache 语法渲染。Java 侧不持有模板正文。
_Avoid_: 提示词, Prompt 配置

**Provider**:
模型供应商配置（如 deepseek、qwen-cloud、ollama-local），记录在 Python 侧的 `config/providers.yml`。每个 Provider 声明 API base URL、可用模型列表、优先级和启用状态。本地模型 Provider 预留但默认关闭。
_Avoid_: 模型源, 模型供应商

## 数据

**Readable Schema Registry (Java)**:
集中维护 AI 临时 SQL 可读的表、字段、模块、权限要求和是否允许模型直查。模型提示词和安全校验共用同一份注册表。Python 不持有此注册表，只在 Tool Call 时从 Java 获取当前用户可见部分。
_Avoid_: 模型可见表, 可读字段表

**Query Cursor (Java)**:
保存当前会话中一次业务查询的脱敏快照，让用户的多轮追问（"继续看""下一页"）可以稳定复用上一轮查询状态。Java 侧 `ai_query_cursor` 表持久化。
_Avoid_: 分页游标, 查询上下文
