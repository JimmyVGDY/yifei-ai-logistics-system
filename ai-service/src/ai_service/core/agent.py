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
    columns: list[str] = field(default_factory=list)
    total_count: int = 0
    returned_count: int = 0
    remaining_count: int = 0
    has_more: bool = False
    next_page_hint: str = ""
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
        tools = await self._fetch_tools(ctx)

        # Step 2: 规则闸门 — 意图分类（对齐 Java AiConversationIntentClassifier）
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        intent_result = classifier.classify(ctx.question)
        intent = intent_result["intent"]

        if intent in ("CORRECTION", "CLARIFY"):
            # 直接回复，不走 LLM
            answer = intent_result.get("direct_answer", "请补充更多信息。")
            yield self._sse("thinking", {"message": "理解问题"})
            for ch in answer:
                yield self._sse("token", {"delta": ch})
            yield self._sse("done", {
                "conversationId": ctx.conversation_id,
                "answer": answer,
                "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
                "citationCount": 0, "toolCallCount": 0,
            })
            return

        # CORRECTION/CLARIFY 之外 → Chat/Continuation/Business 走 LLM
        use_tools = intent != "CHAT"

        # Step 3: 渲染主对话 prompt
        from datetime import date as date_type
        today_str = date_type.today().strftime("%Y-%m-%d")
        rendered = self.prompt_engine.render("assistant-chat", {
            "question": ctx.question,
            "permissions": json.dumps(ctx.user_context.get("permissions", []), ensure_ascii=False),
            "memories": json.dumps(ctx.memory_context, ensure_ascii=False),
            "rag_context": ctx.rag_context,
            "today": today_str,
        })

        # Build conversation messages
        messages = [
            {"role": "system", "content": rendered.system_prompt},
            {"role": "user", "content": rendered.user_prompt},
        ]

        # Step 3: Agent loop
        full_answer = ""  # 跨轮累积全部文本，确保 done.answer 完整
        while ctx.iteration < max_iterations:
            ctx.iteration += 1

            # Send thinking event
            yield self._sse("thinking", {
                "message": f"正在分析... ({ctx.iteration}/{max_iterations})",
                "iteration": ctx.iteration,
            })

            # Call LLM with tool definitions
            tool_calls_in_round = []
            last_output_time = time.monotonic()

            try:
                active_tools = tools if use_tools else None
                async for delta in self._call_llm_with_tools(
                    messages, active_tools, rendered.model, rendered.temperature, api_key,
                ):
                    now = time.monotonic()
                    # 超过 10 秒无输出 → 插入心跳
                    if now - last_output_time > 10:
                        yield self._sse("heartbeat", {
                            "message": "AI 仍在处理，请稍候",
                            "elapsedMs": int((now - ctx.start_time) * 1000),
                        })
                    last_output_time = now
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
                # error 后必须发 done，否则前端报"SSE 连接意外关闭"
                yield self._sse("done", {
                    "conversationId": ctx.conversation_id,
                    "answer": (full_answer or "").strip() or "抱歉，AI 服务暂时不可用，请稍后重试",
                    "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
                    "citationCount": len(ctx.tool_results),
                    "toolCallCount": len(ctx.tool_results),
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

                    # 关键词后处理：剔除 LLM 编造的垃圾 keyword（如 "ing"）
                    tc = self._sanitize_tool_call(tc)

                    result = await self._execute_tool(tc, ctx)
                    ctx.tool_results.append(result)

                    yield self._sse("tool_result", {
                        "toolName": tc.name,
                        "target": str(tc.arguments.get("module", tc.arguments.get("keyword", ""))),
                        "result": result.summary,
                        "success": result.success,
                        "rows": result.data if isinstance(result.data, list) else [],
                        "data": result.data if isinstance(result.data, list) else [],
                        "columns": result.columns,
                        "totalCount": result.total_count,
                        "total": result.total_count,
                        "returnedCount": result.returned_count,
                        "remainingCount": result.remaining_count,
                        "hasMore": result.has_more,
                        "nextPageHint": result.next_page_hint,
                        "cursorId": result.cursor_id,
                        "citation": result.citation,
                        "toolCallCount": len(ctx.tool_results),
                        "maxToolCalls": max_iterations,
                        "elapsedMs": elapsed_ms,
                    })

                    # Append tool result to messages for next LLM round (OpenAI standard format)
                    tc_id = f"call_{ctx.iteration}_{len(ctx.tool_results)}"
                    messages.append({
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [{"id": tc_id, "type": "function",
                                        "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}],
                    })
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc_id,
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
            "answer": full_answer.strip(),
            "elapsedMs": int(total_ms),
            "citationCount": len(ctx.tool_results),
            "toolCallCount": len(ctx.tool_results),
        })

    async def _fetch_tools(self, ctx: AgentContext) -> list[dict]:
        """从 Java 拉取当前用户可用的工具列表。"""
        try:
            tools = await java_client.fetch_tool_registry(user_context=self._java_user_context(ctx))
            return tools
        except Exception:
            logger.warning("tool_registry_unavailable")
            return []

    async def _execute_tool(self, tc: ToolCall, ctx: AgentContext) -> ToolResult:
        """回调 Java 执行业务工具。"""
        try:
            result = await java_client.execute_tool(tc.name, tc.arguments, user_context=self._java_user_context(ctx))
            # Java returns {"success": true/false, "data": [...], "totalCount": N, ...}
            rows = result.get("rows", result.get("data", []))
            total = result.get("totalCount", len(rows))
            if isinstance(total, list):
                total = len(total)
            returned = result.get("returnedCount", len(rows) if isinstance(rows, list) else 0)
            remaining = result.get("remainingCount", 0)

            return ToolResult(
                name=tc.name,
                success=result.get("success", False),
                data=rows,
                columns=result.get("columns", []),
                total_count=int(total) if total else 0,
                returned_count=int(returned) if returned else 0,
                remaining_count=int(remaining) if remaining else 0,
                has_more=result.get("hasMore", False) is True,
                next_page_hint=result.get("nextPageHint", ""),
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

    @staticmethod
    def _java_user_context(ctx: AgentContext) -> dict:
        user_context = dict(ctx.user_context or {})
        user_context.setdefault("conversationId", ctx.conversation_id)
        return user_context

    async def _call_llm_with_tools(
        self,
        messages: list,
        tools: list[dict],
        model: str,
        temperature: float,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str | ToolCall]:
        """调用 LLM（流式），使用完整 messages + OpenAI 标准 function calling。"""
        openai_tools = None
        if tools:
            openai_tools = []
            for t in tools:
                openai_tools.append({
                    "type": "function",
                    "function": {
                        "name": t.get("name", ""),
                        "description": t.get("description", ""),
                        "parameters": t.get("parameters", {}),
                    }
                })

        async for delta in self.gateway.chat_stream_messages(
            messages=messages,
            task_type="chat",
            api_key=api_key,
            temperature=temperature,
            tools=openai_tools,
        ):
            # delta could be text or a JSON-encoded tool_call event from model_gateway
            if isinstance(delta, str):
                if delta.startswith('{"tool_call":'):
                    try:
                        obj = json.loads(delta)
                        tc_data = obj.get("tool_call", {})
                        if "name" in tc_data:
                            yield ToolCall(
                                name=tc_data["name"],
                                arguments=tc_data.get("arguments", {}),
                            )
                    except (json.JSONDecodeError, KeyError):
                        pass
                else:
                    yield delta

    @staticmethod
    def _sanitize_tool_call(tc: ToolCall) -> ToolCall:
        """清洗工具调用参数：剔除 LLM 编造的垃圾 keyword（如截取模块名子串）。"""
        if tc.name == "query_business_module":
            keyword = tc.arguments.get("keyword", "")
            module = tc.arguments.get("module", "")
            if isinstance(keyword, str) and isinstance(module, str):
                # keyword 是 module 的子串 → LLM 瞎编的，清掉
                if len(keyword) <= 5 and keyword.lower() in module.lower():
                    logger.warning("sanitized_keyword", keyword=keyword, module=module)
                    tc.arguments["keyword"] = ""
                # keyword 只有 1-2 个字母 → 明显是垃圾
                elif len(keyword) <= 2 and keyword.isascii() and keyword.isalpha():
                    logger.warning("sanitized_keyword_garbage", keyword=keyword)
                    tc.arguments["keyword"] = ""
        return tc

    @staticmethod
    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
