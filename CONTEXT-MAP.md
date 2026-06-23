# Context Map

## Contexts

- [Java Backend](./CONTEXT.md) — 物流业务系统 + 鉴权 + 数据安全网关 + BFF
- [Python AI Service](./ai-service/CONTEXT.md) — 模型网关 + Agent 编排 + RAG + 记忆智能

## Relationships

- **前端 → Java Backend**: 唯一用户入口，Java 做登录鉴权、权限校验、策略决策
- **Java Backend → Python AI Service**: HTTP (127.0.0.1:8001)，同步调 AI 能力；SSE 流式代理；Tool Call 回调
- **Python AI Service → Java Backend**: HTTP 回调 `/ai/internal/tool/execute` 执行业务查询
- **权限**: Java 是权限真值来源，Python 不持有用户/权限概念
- **数据**: MySQL (业务+会话+记忆真值) 归 Java，Qdrant (向量) 归 Python
