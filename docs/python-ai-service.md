# Python AI 服务开发指南

> Python FastAPI 服务 (`ai-service/`)，负责模型网关、Agent 编排、RAG 和记忆智能。只监听 `127.0.0.1:8001`，不直接暴露给前端。

## 快速开始

```bash
# 安装依赖
cd ai-service
uv sync

# 安装开发依赖（含 pytest）
uv sync --extra dev

# 启动开发服务器（热重载）
uv run uvicorn ai_service.main:app --host 127.0.0.1 --port 8001 --reload

# 运行测试
uv run pytest tests/unit/ -v
```

## Docker Compose AI

Use the optional compose override when Python AI Service and Qdrant are required:

```bash
cp .env.example .env
# Edit .env and set AI_INTERNAL_SHARED_SECRET first.
docker compose -f docker-compose.yml -f docker-compose.ai.yml up -d
```

- `ai-service` calls Java through the Docker network at `app:8080`.
- Java and Python internal calls must use `X-Internal-Secret`.
- `ai-service` and `qdrant` host ports bind to `127.0.0.1` by default.
- Use only `docker-compose.yml` when AI services are not required.

## 项目结构

```
ai-service/
├── pyproject.toml            ← 依赖清单（uv 管理）
├── Dockerfile
├── src/ai_service/
│   ├── main.py               ← FastAPI 入口 + OTel + Prometheus 集成
│   ├── api/                   ← HTTP 路由
│   │   ├── health.py         ←    /health, 基础设施连通性检查
│   │   ├── chat.py           ←    POST /chat/stream (SSE)
│   │   ├── tools.py          ←    工具注册表 + 执行回调
│   │   ├── memory.py         ←    记忆召回/同步/提炼
│   │   ├── rag.py            ←    文档检索/重建索引
│   │   └── tasks.py          ←    异步任务提交/查询/取消
│   ├── core/                  ← 核心 AI 能力（Agent/模型网关/Prompt/意图/幻觉检查）
│   ├── memory/                ← 长期记忆智能（提炼/召回/编码）
│   ├── rag/                   ← 文档检索（检索/分块/索引）
│   ├── validation/            ← 结构化输出校验
│   └── infrastructure/        ← 基础设施客户端
│       ├── redis_client.py   ←   Redis（任务状态 + PipelineContext）
│       ├── qdrant_client.py  ←   Qdrant（RAG + 记忆向量）
│       └── java_client.py    ←   Java Backend HTTP 调用
├── prompts/                   ← Prompt 模板（YAML + Mustache，Git 版本化）
│   ├── chat/                  ←   问答/意图分类/幻觉检查
│   ├── sql/                   ←   SQL 生成
│   ├── memory/                ←   记忆提炼
│   └── analysis/              ←   每日简报
├── config/
│   └── providers.yml          ← 模型 Provider 注册表
└── tests/unit/                ← 单元测试
```

## API 端点

### 健康检查

```
GET /health
→ { "status": "ok|degraded", "checks": { "redis": "ok", "qdrant": "ok", "java": "ok" } }

GET /metrics
→ Prometheus 格式指标
```

### SSE 对话（核心）

```
POST /chat/stream
Content-Type: application/json
{ "question": "...", "conversation_id": "conv_123", "history": [...], ... }

→ text/event-stream:
  event: thinking     → {"message": "正在分析..."}
  event: tool_start   → {"toolName": "query_orders", "arguments": {...}}
  event: tool_result  → {"toolName": "query_orders", "resultSummary": "...", "citation": {...}}
  event: token        → {"delta": "您好"}
  event: done         → {"messageId": "...", "conversationId": "...", "usage": {...}}
  event: error        → {"code": "MODEL_TIMEOUT", "message": "..."}
```

### 内部端点（仅供 Java 调用）

```
GET  /internal/tools/registry        ← 当前用户可用的工具列表
POST /internal/tool/execute          ← 执行业务查询
POST /internal/memory/recall         ← 召回长期记忆
POST /internal/memory/sync           ← Java 批准/删除记忆后同步 Qdrant
POST /internal/memory/extract        ← 从对话提炼候选记忆
POST /internal/rag/search            ← 文档语义搜索
POST /internal/rag/reindex           ← 重建文档索引
POST /internal/tasks/submit          ← 提交异步任务
GET  /internal/tasks/{id}/status     ← 查询任务进度
POST /internal/tasks/{id}/cancel     ← 取消进行中任务
```

## OpenTelemetry 链路追踪

```bash
# 启动 Jaeger（桌面开发工具文件夹或 F:\Development\Middleware\scripts）
powershell -File middleware-manager.ps1 -Service jaeger -Action start

# Java + Python 自动发送 trace 到 localhost:4318
# Jaeger UI: http://localhost:16686

# 配置 OTel endpoint（无 Jaeger 时自动退到 console 输出 traceId）
set OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

## Prompt 模板

模板用 YAML + Mustache，在 `prompts/` 目录按场景分文件夹。

新增模板的契约：
1. 必须声明 `required_variables` 和 `optional_variables`
2. 需要结构化输出的必须绑定 `output_schema` (JSON Schema)
3. `system` prompt 中使用的 `{{变量}}` 必须在变量声明中存在
4. 变更后 Git 提交，`git log prompts/` 即变更历史

测试自动校验所有模板：
```bash
uv run pytest tests/unit/test_prompt_engine.py -v
```

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `JAVA_INTERNAL_URL` | `http://localhost:8080` | Java Backend 内部地址 |
| `REDIS_URL` | `redis://localhost:6379` | Redis 地址 |
| `QDRANT_URL` | `http://localhost:6333` | Qdrant REST 地址 |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama 地址（预留） |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | （空） | OTLP trace 接收端，不设则退 console |
| `LOG_LEVEL` | `INFO` | 日志级别 |

## 相关文档

- [项目文档索引](README.md)
- [架构说明](architecture.md)
- [ADR 0001 — Java+Python 混合架构](adr/0001-java-python-hybrid-architecture.md)
- [CONTEXT-MAP](../CONTEXT-MAP.md)
- [AI 助手设计](ai-assistant-design.md)
