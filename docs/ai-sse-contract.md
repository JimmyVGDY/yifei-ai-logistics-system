# AI SSE Contract

AI 流式对话使用标准 Server-Sent Events。前端通过 `fetch` 读取 `text/event-stream`，后端必须按事件完整写出，不依赖单个网络 chunk 的边界。

## Events

- `thinking`: Agent 状态说明。常用字段：`message`、`elapsedMs`。
- `tool_start`: 工具开始执行。常用字段：`toolName`、`target`、`toolCallCount`、`maxToolCalls`、`elapsedMs`。
- `tool_result`: 工具执行结果。常用字段：`toolName`、`target`、`result`、`summary`、`columns`、`rows`、`cursorId`、`totalCount`、`returnedCount`、`remainingCount`、`hasMore`、`nextPageHint`、`elapsedMs`。
- `done`: 流式对话正常结束。常用字段：`conversationId`、`answer`、`citations`、`elapsedMs`、`toolCallCount`。
- `error`: 流式对话失败。常用字段：`message`、`elapsedMs`。

## Wire Format

每个事件必须使用标准 SSE 格式：

```text
event: tool_result
data: {"success":true,"rows":[]}

```

约束：

- `data` 必须是合法 JSON。
- 多行 JSON 必须拆成多行 `data:`，前端会按 SSE 标准用 `\n` 合并。
- `event:` 和 `data:` 可能被网络 chunk 任意拆分，前端解析器不得依赖 chunk 边界。
- 后端正常完成时必须发送 `done`。
- 后端发送 `error` 后也应尽量发送 `done`，否则前端会把连接关闭视为异常。
- 前端只把 `done` 作为最终结果落点，`tool_result` 只用于结构化表格和过程展示。

## Tool Result Shape

工具结果建议统一包含：

```json
{
  "success": true,
  "message": "",
  "summary": "",
  "columns": [],
  "rows": [],
  "metadata": {}
}
```

兼容字段：

- `data` 与 `rows` 同义；新代码优先读 `rows`。
- `total` 与 `totalCount` 同义；新代码优先写 `totalCount`。
- 分页结果使用 `cursorId`、`hasMore`、`remainingCount` 和 `nextPageHint`。

## Security Boundary

- SSE 内容不得原文输出 `token`、`password`、`authorization`、`cookie`、`secret`、`accessKey`、`apiKey`、密钥、口令等敏感信息。
- 工具失败时返回面向用户的摘要，不返回 SQL、堆栈、内部 URL、权限码或底层异常 message。
- 权限不足时只返回友好中文提示，由后端做最终权限判断。
