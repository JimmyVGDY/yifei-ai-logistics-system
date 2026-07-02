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
from ai_service.core.intent import IntentPlan

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
    # Java 返回的结构化行，字段应已经经过后端列权限和脱敏处理。
    data: Any = None
    columns: list[str] = field(default_factory=list)
    # total/returned/remaining 三个计数共同驱动前端“继续查看剩余数据”按钮。
    total_count: int = 0
    returned_count: int = 0
    remaining_count: int = 0
    has_more: bool = False
    next_page_hint: str = ""
    cursor_id: str = ""
    citation: dict = field(default_factory=dict)
    summary: str = ""
    display_tool_name: str = ""
    display_target: str = ""
    display_summary: str = ""
    # 多模块查询的分组结果，前端优先用它拆成多张结果卡片。
    data_groups: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class AgentContext:
    """单次 Agent 编排的上下文。"""
    question: str
    conversation_id: str
    user_context: dict
    memory_context: dict
    rag_context: str
    # Java BFF 传入的短期会话历史，来源可以是 MySQL，也可以是前端 SSE 失败时的 clientHistory 兜底。
    history: list[dict] = field(default_factory=list)
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

        # Step 1: 初始化工具列表；真正是否拉取工具由规则意图决定。
        tools = []

        # Step 2: 规则闸门 — 先挡掉纠偏/澄清/闲聊，减少不必要模型与工具调用。
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        previous_user_message = self._previous_user_message(ctx)
        intent_result = classifier.classify(ctx.question, previous_user_message)
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

        tools = await self._fetch_tools(ctx) if use_tools else []
        # IntentPlan 只影响 Python 工具选择建议；Java internal 工具仍是最终安全边界。
        intent_plan = await self._build_intent_plan(ctx, classifier, previous_user_message, api_key) if use_tools else None
        tools = self._filter_tools_for_question(tools, ctx.question, intent_plan)
        if use_tools and not tools:
            answer = (
                "业务查询工具暂时不可用，当前没有真正执行订单、费用或异常等系统查询。"
                "请确认 Java 服务已启动、Python 进程设置了与 Java 一致的 AI_INTERNAL_SHARED_SECRET，"
                "然后重新提问。"
            )
            yield self._sse("error", {
                "code": "TOOLS_UNAVAILABLE",
                "message": answer,
                "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
            })
            for ch in answer:
                yield self._sse("token", {"delta": ch})
            yield self._sse("done", {
                "conversationId": ctx.conversation_id,
                "answer": answer,
                "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
                "citationCount": 0,
                "toolCallCount": 0,
            })
            return

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
        # 历史消息必须放在当前问题之前，否则“没了？”“继续”“那个客户呢？”这类追问无法接上上文。
        messages = [{"role": "system", "content": rendered.system_prompt}]
        if intent_plan:
            # 把结构化规划作为额外 system hint 注入，约束模型选择工具但不让它决定权限。
            messages.append({"role": "system", "content": self._intent_plan_system_hint(intent_plan)})
        messages.extend(self._normalized_history(ctx))
        messages.append({"role": "user", "content": rendered.user_prompt})

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
                    # 工具调用前再做一次确定性清洗，防止模型选择了错误工具或编造 keyword。
                    tc = self._sanitize_tool_call(tc, intent_plan)
                    elapsed_ms = int((time.monotonic() - ctx.start_time) * 1000)
                    yield self._sse("tool_start", {
                        "toolName": self._display_tool_name(tc.name),
                        "target": self._display_target(tc),
                        "toolCallCount": len(ctx.tool_results) + 1,
                        "maxToolCalls": max_iterations,
                        "elapsedMs": elapsed_ms,
                    })

                    result = await self._execute_tool(tc, ctx)
                    ctx.tool_results.append(result)

                    yield self._sse("tool_result", {
                        "toolName": result.display_tool_name or self._display_tool_name(tc.name),
                        "target": result.display_target or self._display_target(tc),
                        "displayToolName": result.display_tool_name or self._display_tool_name(tc.name),
                        "displayTarget": result.display_target or self._display_target(tc),
                        "displaySummary": result.display_summary or self._tool_answer(result),
                        "result": result.display_summary or self._tool_answer(result),
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
                        "dataGroups": result.data_groups,
                        "citation": result.citation,
                        "toolCallCount": len(ctx.tool_results),
                        "maxToolCalls": max_iterations,
                        "elapsedMs": elapsed_ms,
                    })

                    if self._should_finish_after_tool(result):
                        # 业务工具已经返回结构化结果时直接收口，避免二次模型摘要失败影响用户体验。
                        full_answer = self._tool_answer(result)
                        for ch in full_answer:
                            yield self._sse("token", {"delta": ch})
                        yield self._sse("done", {
                            "conversationId": ctx.conversation_id,
                            "answer": full_answer,
                            "elapsedMs": int((time.monotonic() - ctx.start_time) * 1000),
                            "citationCount": len(ctx.tool_results),
                            "toolCallCount": len(ctx.tool_results),
                        })
                        return

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
                            "data_groups": result.data_groups,
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
        except Exception as exc:
            logger.warning(
                "tool_registry_unavailable",
                error=str(exc),
                user_id=(ctx.user_context or {}).get("userId", ""),
                conversation_id=ctx.conversation_id,
            )
            return []

    async def _execute_tool(self, tc: ToolCall, ctx: AgentContext) -> ToolResult:
        """回调 Java 执行业务工具。"""
        try:
            result = await java_client.execute_tool(tc.name, tc.arguments, user_context=self._java_user_context(ctx))
            # Java returns {"success": true/false, "data": [...], "totalCount": N, ...}
            rows = result.get("rows", result.get("data", []))
            total = result.get("totalCount", len(rows))
            if isinstance(total, list):
                # 兼容历史接口偶尔把 totalCount 错写成列表的情况。
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
                display_tool_name=result.get("displayToolName", ""),
                display_target=result.get("displayTarget", ""),
                display_summary=result.get("displaySummary", ""),
                data_groups=self._normalize_data_groups(result.get("dataGroups")),
            )
        except Exception as exc:
            return ToolResult(
                name=tc.name,
                success=False,
                summary=f"工具执行失败: {exc}",
            )

    @staticmethod
    def _should_finish_after_tool(result: ToolResult) -> bool:
        """业务工具已有结构化结果时直接收口，避免二次模型摘要失败影响体验。"""
        if not result.success:
            return False
        return bool(result.summary or result.total_count or result.returned_count or result.data)

    @staticmethod
    def _tool_answer(result: ToolResult) -> str:
        summary = result.display_summary or result.summary
        if summary and not AgentOrchestrator._looks_unsafe_for_user(summary):
            return summary
        if result.total_count:
            # 摘要不安全时只保留数量和分页建议，绝不复述 SQL、字段名或 Markdown 表。
            prefix = "统计分析完成" if result.name == "execute_readonly_sql" else "业务查询完成"
            answer = f"{prefix}，已返回 {result.returned_count or 0} 条结构化记录，共匹配 {result.total_count} 条。"
            if result.remaining_count > 0:
                answer += f"还有 {result.remaining_count} 条记录可通过结果卡片继续分页查看。"
            return answer
        return "已完成业务数据查询，结构化结果已在下方表格中展示。"

    @staticmethod
    def _looks_unsafe_for_user(text: str) -> bool:
        """用户可见文本安全兜底：发现内部工具、SQL 或 snake_case 就降级。"""
        if not text:
            return False
        lowered = text.lower()
        if "execute_readonly_sql" in lowered or "query_business_module" in lowered:
            return True
        if "select " in lowered or " from logistics_" in lowered or " from sys_" in lowered:
            return True
        if "| ---" in text or "\n|" in text:
            return True
        import re
        return re.search(r"\b[a-z]+_[a-z0-9_]+\b", text) is not None

    @classmethod
    def _filter_tools_for_question(cls, tools: list[dict], question: str, intent_plan: IntentPlan | None = None) -> list[dict]:
        """根据问题和 IntentPlan 收窄工具集合，避免普通明细误进 SQL 工具。"""
        if intent_plan and intent_plan.tool_hint == "query_business_module" and len(intent_plan.modules) == 1:
            allowed = {"query_business_module", "continue_cursor"}
            return [tool for tool in tools if cls._tool_name(tool) in allowed]
        if intent_plan and intent_plan.tool_hint == "global_fuzzy_search":
            allowed = {"global_fuzzy_search", "query_business_module", "continue_cursor"}
            return [tool for tool in tools if cls._tool_name(tool) in allowed]
        if cls._allow_readonly_sql(question) or (intent_plan and intent_plan.tool_hint == "execute_readonly_sql"):
            return tools
        return [tool for tool in tools if cls._tool_name(tool) != "execute_readonly_sql"]

    @staticmethod
    def _allow_readonly_sql(question: str) -> bool:
        text = (question or "").lower()
        return any(word in text for word in (
            "sql", "连表", "关联", "统计", "汇总", "数量", "总数", "排名",
            "最多", "最少", "平均", "多少", "占比", "比例", "group", "join",
        ))

    @staticmethod
    def _tool_name(tool: dict) -> str:
        return str(tool.get("name") or (tool.get("function") or {}).get("name") or "")

    @staticmethod
    def _display_tool_name(tool_name: str) -> str:
        return {
            "query_business_module": "业务数据查询",
            "global_fuzzy_search": "全局业务搜索",
            "joined_business_query": "业务联合查询",
            "query_dashboard": "运营看板查询",
            "query_log_analysis": "日志排障分析",
            "execute_readonly_sql": "统计分析",
            "continue_cursor": "继续分页查询",
        }.get(tool_name, "业务数据查询")

    @staticmethod
    def _display_target(tc: ToolCall) -> str:
        if tc.name == "execute_readonly_sql":
            return "统计结果"
        module = str(tc.arguments.get("module", "") or "")
        scene = str(tc.arguments.get("scene", "") or "")
        keyword = str(tc.arguments.get("keyword", "") or "")
        module_names = {
            "orders": "运单管理",
            "waybills": "运单中心",
            "customers": "客户管理",
            "dispatches": "调度管理",
            "tasks": "运输任务",
            "tracks": "物流轨迹",
            "drivers": "司机管理",
            "vehicles": "车辆管理",
            "exceptions": "异常管理",
            "fees": "费用结算",
            "users": "用户管理",
            "roles": "角色管理",
            "files": "上传文件",
            "operationLogs": "操作日志",
        }
        scene_names = {
            "customer": "客户全貌",
            "order": "订单生命周期",
            "driver": "司机任务链",
            "vehicle": "车辆调度链",
            "exception": "异常影响范围",
            "business": "综合业务",
        }
        if module:
            return module_names.get(module, "业务数据")
        if scene:
            return scene_names.get(scene, "业务联合结果")
        return keyword if keyword and not AgentOrchestrator._looks_unsafe_for_user(keyword) else "查询结果"

    @staticmethod
    def _java_user_context(ctx: AgentContext) -> dict:
        user_context = dict(ctx.user_context or {})
        user_context.setdefault("conversationId", ctx.conversation_id)
        return user_context

    @staticmethod
    def _normalized_history(ctx: AgentContext) -> list[dict]:
        """将 Java/MySQL/前端兜底历史转换为 OpenAI messages。"""
        result: list[dict] = []
        history = ctx.history or []
        for item in history[-8:]:
            if not isinstance(item, dict):
                continue
            role = str(item.get("role", "")).strip().lower()
            content = str(item.get("content", "")).strip()
            if not content:
                continue
            if len(content) > 2000:
                content = content[:2000]
            if role in ("user", "assistant"):
                result.append({"role": role, "content": content})
            else:
                # 兼容 Java 当前传入的“纯文本历史摘要”格式：{"content": "用户：...\nAI助手：..."}
                result.append({
                    "role": "user",
                    "content": "以下是上文会话记录，仅用于理解当前追问，不要在回答中复述：\n" + content,
                })
        return result[-8:]

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
    def _sanitize_tool_call(tc: ToolCall, intent_plan: IntentPlan | None = None) -> ToolCall:
        """清洗工具调用参数：剔除 LLM 编造的垃圾 keyword（如截取模块名子串）。"""
        if intent_plan:
            tc = AgentOrchestrator._apply_intent_plan_to_tool_call(tc, intent_plan)
        if tc.name == "query_business_module":
            keyword = tc.arguments.get("keyword", "")
            module = tc.arguments.get("module", "")
            if isinstance(keyword, str) and isinstance(module, str):
                # keyword 是 module 的子串 → LLM 瞎编的，清掉
                if len(keyword) <= 5 and keyword.lower() in module.lower():
                    logger.debug("sanitized_keyword", keyword=keyword, module=module)
                    tc.arguments["keyword"] = ""
                # keyword 只有 1-2 个字母 → 明显是垃圾
                elif len(keyword) <= 2 and keyword.isascii() and keyword.isalpha():
                    logger.debug("sanitized_keyword_garbage", keyword=keyword)
                    tc.arguments["keyword"] = ""
        return tc

    @staticmethod
    def _apply_intent_plan_to_tool_call(tc: ToolCall, plan: IntentPlan) -> ToolCall:
        """把规划结果应用到模型工具调用上，修正 SQL/全局搜索误选。"""
        if plan.tool_hint == "execute_readonly_sql":
            return tc
        if tc.name == "execute_readonly_sql" and plan.operation != "analysis":
            if plan.modules:
                tc = ToolCall("query_business_module", {
                    "module": plan.modules[0],
                    "keyword": plan.keyword,
                })
            elif plan.keyword:
                tc = ToolCall("global_fuzzy_search", {"keyword": plan.keyword})
        if tc.name == "global_fuzzy_search" and plan.modules:
            tc = ToolCall("query_business_module", {
                "module": plan.modules[0],
                "keyword": plan.keyword,
            })
        if tc.name == "query_business_module" and plan.modules:
            tc.arguments["module"] = plan.modules[0]
            if plan.keyword and not str(tc.arguments.get("keyword") or "").strip():
                tc.arguments["keyword"] = plan.keyword
        if plan.time_range and tc.name in {"query_business_module", "global_fuzzy_search"}:
            if plan.time_range.get("startTime"):
                tc.arguments.setdefault("startTime", plan.time_range["startTime"])
            if plan.time_range.get("endTime"):
                tc.arguments.setdefault("endTime", plan.time_range["endTime"])
        return tc

    async def _build_intent_plan(
        self,
        ctx: AgentContext,
        classifier,
        previous_user_message: str,
        api_key: Optional[str],
    ) -> IntentPlan:
        fallback = classifier.deterministic_plan(ctx.question, previous_user_message)
        if not self._needs_llm_intent_plan(ctx.question, previous_user_message, fallback):
            # 明确模块、续页或统计类快路径不需要额外模型规划，减少延迟和失败点。
            return fallback
        try:
            rendered = self.prompt_engine.render("intent-classify", {
                "question": ctx.question,
                "history": json.dumps(self._normalized_history(ctx), ensure_ascii=False),
                "previous_user_message": previous_user_message,
                "available_modules": json.dumps([
                    "orders", "waybills", "customers", "dispatches", "tasks", "tracks",
                    "drivers", "vehicles", "exceptions", "fees"
                ], ensure_ascii=False),
                "tool_boundaries": (
                    "普通明细查询使用 query_business_module 或 global_fuzzy_search；"
                    "统计、聚合、排名、关联分析才使用 execute_readonly_sql。"
                ),
            })
            response = await asyncio.wait_for(
                self.gateway.chat(
                    rendered.system_prompt,
                    rendered.user_prompt,
                    task_type="intent-classify",
                    api_key=api_key,
                    temperature=rendered.temperature,
                    max_tokens=rendered.max_tokens,
                ),
                timeout=3.0,
            )
            payload = self._extract_json_object(response.content)
            plan = IntentPlan.from_dict(payload)
            if plan.confidence < 0.55:
                # 低置信度规划不参与工具选择，使用确定性规则兜底。
                return fallback
            if fallback.refinement_of_previous and not plan.keyword:
                # LLM 识别到模块但漏掉继承关键词时，用规则提取的上一轮实体补齐。
                plan.keyword = fallback.keyword
                plan.refinement_of_previous = True
            return plan
        except Exception as exc:
            logger.debug("intent_plan_fallback", error=str(exc)[:200], question=ctx.question)
            return fallback

    @staticmethod
    def _needs_llm_intent_plan(question: str, previous_user_message: str, fallback: IntentPlan) -> bool:
        """只在语义不稳定的场景调用轻量规划模型。"""
        text = question or ""
        if fallback.tool_hint == "execute_readonly_sql":
            return False
        return bool(
            previous_user_message and any(word in text for word in ("这个", "那个", "他", "她", "它", "相关", "有关", "只看", "只查"))
            or len(text.strip()) <= 8
            or len(fallback.modules) > 1
            or (fallback.modules and fallback.keyword and fallback.confidence < 0.8)
        )

    @staticmethod
    def _intent_plan_system_hint(plan: IntentPlan) -> str:
        return (
            "结构化意图规划建议，仅用于工具选择，不能绕过 Java 权限校验："
            + json.dumps({
                "intent": plan.intent,
                "confidence": plan.confidence,
                "modules": plan.modules,
                "keyword": plan.keyword,
                "timeRange": plan.time_range,
                "operation": plan.operation,
                "refinementOfPrevious": plan.refinement_of_previous,
                "needsClarification": plan.needs_clarification,
                "toolHint": plan.tool_hint,
                "reason": plan.reason,
            }, ensure_ascii=False)
        )

    @staticmethod
    def _extract_json_object(text: str) -> dict[str, Any]:
        raw = (text or "").strip()
        if raw.startswith("```"):
            raw = raw.strip("`")
            raw = raw.replace("json\n", "", 1).replace("JSON\n", "", 1).strip()
        start = raw.find("{")
        end = raw.rfind("}")
        if start >= 0 and end >= start:
            raw = raw[start:end + 1]
        return json.loads(raw)

    @staticmethod
    def _normalize_data_groups(value: Any) -> list[dict[str, Any]]:
        """标准化 Java dataGroups，过滤掉空行组，保证 SSE 只输出可展示卡片。"""
        if not isinstance(value, list):
            return []
        groups = []
        for item in value:
            if not isinstance(item, dict):
                continue
            rows = item.get("rows", item.get("data", []))
            if not isinstance(rows, list) or not rows:
                continue
            groups.append({
                "groupId": item.get("groupId", ""),
                "displayToolName": item.get("displayToolName", ""),
                "displayTarget": item.get("displayTarget", ""),
                "displaySummary": item.get("displaySummary", ""),
                "columns": item.get("columns", []),
                "rows": rows,
                "cursorId": item.get("cursorId", ""),
                "total": item.get("total", item.get("totalCount", 0)),
                "returnedCount": item.get("returnedCount", len(rows)),
                "remainingCount": item.get("remainingCount", 0),
                "hasMore": item.get("hasMore") is True,
                "nextPageHint": item.get("nextPageHint", ""),
            })
        return groups

    @staticmethod
    def _previous_user_message(ctx: AgentContext) -> str:
        """从短期历史中找到最近一条用户消息，用于上下文精化。"""
        for item in reversed(ctx.history or []):
            if not isinstance(item, dict):
                continue
            if str(item.get("role", "")).strip().lower() == "user":
                return str(item.get("content", "")).strip()
        return ""

    @staticmethod
    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
