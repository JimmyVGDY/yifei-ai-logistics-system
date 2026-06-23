# 物流管理系统 — Python AI Service

物流管理系统的 AI 能力服务，负责模型推理、Agent 编排、RAG 检索和长期记忆智能。只通过 HTTP (127.0.0.1:8001) 接受 Java Backend 的内部调用，不直接对前端暴露。

## 核心能力

**AgentOrchestrator**:
Python 侧的多轮 Agent 循环引擎。接收 Java 传入的用户问题、对话历史、记忆上下文和 RAG 结果，驱动 LLM 自主决定调用哪些业务工具（Tool Calling）。每轮 Tool Call 通过 HTTP 回调 Java Tool Executor 执行。最多 5 轮迭代。SSE 事件实时推送思考过程、工具调用和流式 token。
_Avoid_: Agent 编排器, 多轮对话引擎

**IntentClassifier**:
LLM 驱动的意图分类器。判断用户输入属于：普通问答、业务查询、上下文续查、日志排障、临时 SQL 分析、纠偏/偏好反馈、确认理解、普通聊天。只有前四类才开放业务工具调用。
_Avoid_: 意图解析器, 问题分类器

**GroundingGuard**:
回答幻觉检查器。检查 LLM 输出是否有引用来源、工具结果或结构化数据支撑。没有证据却声称已查到数据 → 改成安全提示。只有分页数据却声称完整列出 → 追加分页说明。
_Avoid_: 幻觉检测, 事实核查

## 记忆智能

**MemoryExtractor**:
LLM 驱动的记忆提炼器。每次对话结束后判断是否暴露了用户偏好/习惯/格式要求。LLM 判断"不值得记忆"时不写入任何内容。LLM 不可用时降级为关键词匹配（仅抓取明确的长期偏好表达）。普通一次性业务查询不触发记忆写入。
_Avoid_: 记忆提取器, 偏好学习

**MemoryMergeService**:
双层记忆召回与合并器。并行召回全局记忆和当前会话相关的模块级记忆，按 memory_type 分组消解冲突（IMMUTABLE / OVERRIDE / MERGE），按 priority_weight 排序返回合并后的记忆上下文。
_Avoid_: 记忆融合, 记忆组合

**MemoryVectorEncoder**:
文本向量化编码器。优先使用 Ollama bge-m3 生成 1024 维向量。不可用时降级为确定性哈希向量并补齐到 1024 维。Java 侧不直接调用 Embedding 模型。
_Avoid_: Embedding 服务, 向量生成器

## 知识检索

**KnowledgeRetriever**:
RAG 文档检索器。跨 Qdrant `logistics_docs` 集合执行语义搜索，支持分层并行召回 + RRF 融合 + BM25 关键词降级（Embedding 不可用时自动切换）。
_Avoid_: 文档搜索, 知识库查询

**DocumentChunker**:
文档分块器。按标题层级切分 Markdown 文档，SHA-256 哈希去重避免重复索引。分块后的文本脱敏存入 Qdrant payload。
_Avoid_: 文档切片, 文本分割器

## 模型网关

**Provider**:
模型供应商抽象。当前支持 `deepseek` 和 `qwen-cloud` 云端 API，预留 `ollama-local` 本地模型插槽（默认关闭）。每个 Provider 声明 API base URL、可用模型列表、优先级和降级链位置。
_Avoid_: 模型源, LLM 供应商

**PromptEngine**:
Prompt 模板渲染引擎。从 `prompts/` 目录加载 YAML 模板，校验必填变量，Mustache 渲染，自动注入 JSON Schema。拒绝未声明的变量（防止注入）。
_Avoid_: 模板引擎, Prompt 构建器

## 协议

**SSE Event**:
Python 流式输出的事件协议。事件类型：`thinking`（思考中）、`tool_start`（工具调用开始）、`tool_result`（工具返回）、`token`（流式文本增量）、`done`（完成，含 messageId + token 用量 + 候选记忆）、`error`（异常）。Java 逐事件透传到前端。
_Avoid_: 流式事件, Stream Event

**Tool Callback**:
Python Agent 回调 Java 执行业务工具的内部协议。端点 `POST /ai/internal/tool/execute`，请求体包含 `toolName` 和 `arguments`（LLM 原始参数）。返回体包含已脱敏的 `data`、`totalCount`、`cursorId` 分页游标和 `citation` 引用来源。
_Avoid_: 工具执行回调, 业务查询接口

**PipelineContext**:
Agent 循环的中间状态快照。持久化到 Redis，支持断连恢复。包含当前步骤、已完成步骤、部分结果、进度百分比。SSE 连接断开时自动保存，避免中间推理结果丢失。
_Avoid_: 管道上下文, 任务快照
