"""Agent 编排器：多轮 Tool Calling 循环 + SSE 事件推送。"""

import asyncio
import json
import time
from dataclasses import dataclass, field
from typing import Any, AsyncIterator, Optional

import structlog

from ai_service.config.settings import settings
from ai_service.infrastructure.java_client import java_client
from ai_service.core.model_gateway import ModelGateway
from ai_service.core.prompt_engine import PromptEngine, PromptRenderResult

logger = structlog.get_logger()


@dataclass
class ToolCall:
    """LLM 决定调用的工具。"""
    name: str
    arguments: dict[str, Any]


@dataclass
class ToolResult:
    """工具调用结果。"""
    name: str
    success: bool
    data: Any = None
    total_count: int = 0
    cursor_id: str = ""
    citation: dict = field(default_factory=dict)
    summary: str = ""


@dataclass
class AgentContext:
    """单次 Agent 编排的上下文。"""
    question: str
    conversation_id: str
    user_context: dict
    memory_context: dict
    rag_context: str
    model_policy: str = "API_ALLOWED"

    # Runtime state
    tool_calls: list[ToolCall] = field(default_factory=list)
    tool_results: list[ToolResult] = field(default_factory=list)
    iteration: int = 0
    start_time: float = field(default_factory=time.monotonic)


class AgentOrchestrator:
    """多轮 Agent 循环：LLM 推理 → Tool Calling → 工具执行 → 继续推理。"""

    def __init__(self, gateway: ModelGateway, prompt_engine: PromptEngine):
        self.gateway = gateway
        self.prompt_engine = prompt_engine

    async def run(
        self,
        ctx: AgentContext,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        """
        执行 Agent 循环，yield SSE 格式的事件字符串。

        事件类型：
        - event: thinking
        - event: tool_start
        - event: tool_result
        - event: token
        - event: done
        - event: error
        """
        max_iterations = settings.max_agent_iterations

        # Step 1: 加载工具注册表
        tools = await self._fetch_tools()

        # Step 2: 渲染主对话 prompt
        rendered = self.prompt_engine.render("assistant-chat", {
            "question": ctx.question,
            "permissions": json.dumps(ctx.user_context.get("permissions", []), ensure_ascii=False),
            "memories": json.dumps(ctx.memory_context, ensure_ascii=False),
            "rag_context": ctx.rag_context,
        })

        # Build conversation messages
        messages = [
            {"role": "system", "content": rendered.system_prompt},
            {"role": "user", "content": rendered.user_prompt},
        ]

        # Step 3: Agent loop
        while ctx.iteration < max_iterations:
            ctx.iteration += 1

            # Send thinking event
            yield self._sse("thinking", {
                "message": f"正在分析... ({ctx.iteration}/{max_iterations})",
                "iteration": ctx.iteration,
            })

            # Call LLM with tool definitions
            full_answer = ""
            tool_calls_in_round = []

            try:
                async for delta in self._call_llm_with_tools(
                    messages, tools, rendered.model, rendered.temperature, api_key,
                ):
                    if isinstance(delta, ToolCall):
                        tool_calls_in_round.append(delta)
                    elif isinstance(delta, str):
                        full_answer += delta
                        yield self._sse("token", {"delta": delta})
            except Exception as exc:
                yield self._sse("error", {
                    "code": "LLM_ERROR",
                    "message": f"模型调用失败: {exc}",
                    "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
                })
                return

            # If LLM wants to call tools, execute them
            if tool_calls_in_round:
                for tc in tool_calls_in_round:
                    elapsed_ms = int((time.monotonic() - ctx.start_time) * 1000)
                    yield self._sse("tool_start", {
                        "toolName": tc.name,
                        "target": str(tc.arguments.get("module", tc.arguments.get("keyword", ""))),
                        "toolCallCount": len(ctx.tool_results) + 1,
                        "maxToolCalls": max_iterations,
                        "elapsedMs": elapsed_ms,
                    })

                    result = await self._execute_tool(tc)
                    ctx.tool_results.append(result)

                    yield self._sse("tool_result", {
                        "toolName": tc.name,
                        "target": str(tc.arguments.get("module", tc.arguments.get("keyword", ""))),
                        "result": result.summary,
                        "totalCount": result.total_count,
                        "cursorId": result.cursor_id,
                        "citation": result.citation,
                        "toolCallCount": len(ctx.tool_results),
                        "maxToolCalls": max_iterations,
                        "elapsedMs": elapsed_ms,
                    })

                    # Append tool result to messages for next LLM round
                    messages.append({
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [{"function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}],
                    })
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc.name,
                        "content": json.dumps({
                            "success": result.success,
                            "data_summary": result.summary,
                            "total_count": result.total_count,
                        }, ensure_ascii=False),
                    })
                continue  # go back to LLM for another round

            # No tool calls = final answer
            break

        # Done event — 字段名对齐前端 AiAssistantView.vue 的期望
        total_ms = (time.monotonic() - ctx.start_time) * 1000
        yield self._sse("done", {
            "conversationId": ctx.conversation_id,
            "answer": full_answer,
            "elapsedMs": int(total_ms),
            "citationCount": len(ctx.tool_results),
            "toolCallCount": len(ctx.tool_results),
        })

    async def _fetch_tools(self) -> list[dict]:
        """从 Java 拉取当前用户可用的工具列表。"""
        try:
            tools = await java_client.fetch_tool_registry()
            return tools
        except Exception:
            logger.warning("tool_registry_unavailable")
            return []

    async def _execute_tool(self, tc: ToolCall) -> ToolResult:
        """回调 Java 执行业务工具。"""
        try:
            result = await java_client.execute_tool(tc.name, tc.arguments)
            # Java returns {"success": true/false, "data": [...], "totalCount": N, ...}
            rows = result.get("data", [])
            total = result.get("totalCount", len(rows))
            if isinstance(total, list):
                total = len(total)

            return ToolResult(
                name=tc.name,
                success=result.get("success", False),
                data=rows,
                total_count=int(total) if total else 0,
                cursor_id=result.get("cursorId", ""),
                citation=result.get("citation", {}),
                summary=result.get("summary", f"返回 {total} 条数据"),
            )
        except Exception as exc:
            return ToolResult(
                name=tc.name,
                success=False,
                summary=f"工具执行失败: {exc}",
            )

    async def _call_llm_with_tools(
        self,
        messages: list,
        tools: list[dict],
        model: str,
        temperature: float,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str | ToolCall]:
        """调用 LLM（流式），同时解析可能出现的 tool_calls。

        This is a simplified implementation. A production version would use
        OpenAI-compatible function calling format. For now, we do a simple
        text-based approach: the LLM outputs a special format for tool calls.
        """
        # For now, use chat_stream from gateway
        system = messages[0]["content"] if messages else ""
        user = messages[-1]["content"] if messages else ""

        # Build tool descriptions for the prompt
        tool_descriptions = ""
        if tools:
            tool_descriptions = "\n\n## 可用工具\n"
            for tool in tools:
                name = tool.get("name", "")
                desc = tool.get("description", "")
                params = tool.get("parameters", {})
                tool_descriptions += f"\n- **{name}**: {desc}\n"
                tool_descriptions += f"  参数: {json.dumps(params, ensure_ascii=False)}\n"

        enhanced_system = system + tool_descriptions + """

## 工具调用格式
如果需要查询业务数据，请严格使用以下 JSON 格式：
```
{"tool_call": {"name": "工具名", "arguments": {"参数名": "参数值"}}}
```
如果不需要工具，直接以自然语言回答。
"""

        # Stream from model
        buffer = ""
        async for delta in self.gateway.chat_stream(
            system_prompt=enhanced_system,
            user_prompt=user,
            task_type="chat",
            api_key=api_key,
            temperature=temperature,
        ):
            buffer += delta
            yield delta

        # Check buffer for tool call JSON
        tool_calls = self._parse_tool_calls_from_text(buffer)
        for tc in tool_calls:
            yield tc  # Yield ToolCall objects for the orchestrator loop

    def _parse_tool_calls_from_text(self, text: str) -> list[ToolCall]:
        """从 LLM 响应文本中提取 tool_call JSON（支持嵌套对象）。"""
        results = []
        prefix = '{"tool_call":'
        idx = 0
        while True:
            pos = text.find(prefix, idx)
            if pos == -1:
                break
            # 用括号匹配找到完整 JSON 对象
            start = pos
            depth = 0
            end = -1
            for i in range(pos, len(text)):
                ch = text[i]
                if ch == '{':
                    depth += 1
                elif ch == '}':
                    depth -= 1
                    if depth == 0:
                        end = i + 1
                        break
            if end == -1:
                break
            candidate = text[start:end]
            try:
                obj = json.loads(candidate)
                tc_data = obj.get("tool_call", {})
                if "name" in tc_data:
                    results.append(ToolCall(
                        name=tc_data["name"],
                        arguments=tc_data.get("arguments", {}),
                    ))
            except (json.JSONDecodeError, KeyError):
                pass
            idx = end
        return results

    @staticmethod
    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
