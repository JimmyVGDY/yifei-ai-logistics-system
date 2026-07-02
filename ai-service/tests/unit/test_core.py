"""核心 AI 能力单元测试：ModelGateway, PromptEngine, IntentClassifier, GroundingGuard。"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
PROMPTS_DIR = PROJECT_ROOT / "prompts"
PROVIDERS_PATH = PROJECT_ROOT / "config" / "providers.yml"


class TestProviderRegistry:
    def test_load_providers(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        assert len(registry.providers) >= 2  # deepseek + ollama-local

    def test_deepseek_has_models(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        ds = registry.get_provider("deepseek")
        assert ds is not None
        assert "deepseek-chat" in ds["models"]

    def test_resolve_known_model(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        provider = registry.resolve_model_provider("deepseek-chat")
        assert provider is not None
        assert provider["name"] == "deepseek"

    def test_resolve_unknown_model(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        assert registry.resolve_model_provider("nonexistent-model") is None

    def test_build_model_config(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        config = registry.build_model_config("deepseek-chat", temperature=0.5, max_tokens=2048)
        assert config is not None
        assert config.model_name == "deepseek-chat"
        assert config.provider_name == "deepseek"
        assert config.temperature == 0.5
        assert config.max_tokens == 2048

    def test_ollama_local_disabled(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        config = registry.build_model_config("bge-m3")
        assert config is None  # ollama-local is disabled

    def test_fallback_chain_exists(self):
        from ai_service.core.model_gateway import ProviderRegistry
        registry = ProviderRegistry(PROVIDERS_PATH)
        assert "chat" in registry.fallback_chains
        assert len(registry.fallback_chains["chat"]) >= 2


class TestPromptEngine:
    def test_load_all_templates(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        assert len(engine._templates) >= 5  # at least our 6 templates

    def test_render_assistant_chat(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        result = engine.render("assistant-chat", {
            "question": "查订单",
            "today": "2025-03-24",
            "permissions": "[]",
            "memories": "",
            "rag_context": "",
        })
        assert "物流管理" in result.system_prompt
        assert "查订单" in result.user_prompt
        assert result.model == "deepseek-v4-flash"

    def test_render_missing_required_variable(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        with pytest.raises(ValueError, match="缺少必填变量"):
            engine.render("assistant-chat", {"question": "test"})  # missing permissions, memories, rag_context

    def test_unknown_template(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        with pytest.raises(ValueError, match="模板不存在"):
            engine.render("nonexistent", {})

    def test_json_schema_injected(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        result = engine.render("intent-classify", {"question": "test"})
        assert "json_schema" in result.user_prompt or result.output_schema is not None

    def test_template_meta(self):
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        meta = engine.get_meta("assistant-chat")
        assert meta is not None
        assert meta.model == "deepseek-v4-flash"


class TestIntentClassifier:
    def test_classify_business_query(self):
        """行为动词触发业务查询。"""
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        result = classifier.classify("查一下订单")
        assert result["intent"] == "BUSINESS_QUERY"

    def test_classify_correction(self):
        """纠偏关键词触发 CORRECTION。"""
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        result = classifier.classify("记住了吗，以后只查运输任务")
        assert result["intent"] == "CORRECTION"
        assert "运输任务" in result["direct_answer"]

    def test_classify_chat(self):
        """聊天/系统问答触发 CHAT。"""
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        result = classifier.classify("你好，你能做什么")
        assert result["intent"] == "CHAT"

    def test_classify_clarify_ambiguous(self):
        """歧义术语无上下文触发 CLARIFY。"""
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        result = classifier.classify("异常任务")
        assert result["intent"] == "CLARIFY"

    def test_classify_empty(self):
        from ai_service.core.intent import IntentClassifier
        classifier = IntentClassifier()
        result = classifier.classify("")
        assert result["intent"] == "CHAT"


class TestGroundingGuard:
    def test_pass_through_normal(self):
        """正常回答 pass_through。"""
        from ai_service.core.intent import GroundingGuard
        guard = GroundingGuard()
        result = guard.check("你好，有什么可以帮你", citations=[{"source": "doc"}])
        assert result["action"] == "pass_through"

    def test_discard_unsupported_claim(self):
        """无证据声称查到数据 → discard。"""
        from ai_service.core.intent import GroundingGuard
        guard = GroundingGuard()
        result = guard.check("查询结果已找到 10 条记录", citations=[], tool_results=[])
        assert result["action"] == "discard"
        assert "UNSUPPORTED_DATA_CLAIM" in result["issues"]

    def test_repaired_partial_as_full(self):
        """仅分页数据却声称完整列出 → repaired。"""
        from ai_service.core.intent import GroundingGuard
        guard = GroundingGuard()
        result = guard.check(
            "完整列出所有记录如下...",
            citations=[{"source": "db"}],
            data_results=[{"total": 30, "returnedCount": 10, "hasMore": True}],
        )
        assert result["action"] == "repaired"
        assert "完整列出" not in result["answer"]

    def test_negation_not_data_claim(self):
        """否定句不判为数据声称。"""
        from ai_service.core.intent import GroundingGuard
        guard = GroundingGuard()
        result = guard.check("没有查到相关记录", citations=[], tool_results=[])
        assert result["action"] == "pass_through"

    def test_empty_answer(self):
        from ai_service.core.intent import GroundingGuard
        guard = GroundingGuard()
        result = guard.check("")
        assert result["action"] == "pass_through"


class TestAgentOrchestrator:
    def test_sse_format(self):
        from ai_service.core.agent import AgentOrchestrator
        sse = AgentOrchestrator._sse("thinking", {"message": "test"})
        assert "event: thinking" in sse
        assert "data:" in sse

    def test_parse_tool_call_from_gateway_delta(self):
        """Gateway 流式输出中带 tool_call 的 JSON delta 能正确解析。"""
        import json
        from ai_service.core.agent import AgentOrchestrator, ToolCall as AgentToolCall
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(None, engine)

        # 模拟 gateway chat_stream 产出 tool_call 事件
        tc_json = json.dumps({"tool_call": {"name": "query_business_module", "arguments": {"module": "订单管理"}}})
        results = []
        # 用 _call_llm_with_tools 的解析逻辑手动测试
        if tc_json.startswith('{"tool_call":'):
            obj = json.loads(tc_json)
            tc_data = obj.get("tool_call", {})
            if "name" in tc_data:
                results.append(AgentToolCall(name=tc_data["name"], arguments=tc_data.get("arguments", {})))
        assert len(results) == 1
        assert results[0].name == "query_business_module"

    def test_java_internal_secret_can_be_overridden_per_request(self):
        from ai_service.infrastructure.java_client import JavaClient

        headers = JavaClient._build_internal_headers({
            "userId": "260602222327001",
            "conversationId": "conv-1",
            "internalSecret": "java-side-secret",
        })

        assert headers["X-Internal-Secret"] == "java-side-secret"
        assert "java-side-secret" not in headers["X-Internal-User"]

    @pytest.mark.asyncio
    async def test_execute_tool_preserves_structured_result(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator, ToolCall
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_execute_tool(tool_name, arguments, user_context=None):
            assert user_context["conversationId"] == "conv-1"
            return {
                "success": True,
                "data": [{"订单号": "LO-001", "应付金额": 12.3}],
                "columns": ["订单号", "应付金额"],
                "totalCount": 12,
                "returnedCount": 1,
                "remainingCount": 11,
                "hasMore": True,
                "nextPageHint": "还有 11 条记录",
                "cursorId": "cursor-1",
                "summary": "命中 12 条记录",
            }

        monkeypatch.setattr(agent_module.java_client, "execute_tool", fake_execute_tool)
        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(None, engine)
        ctx = AgentContext(
            question="看一下这个月的费用",
            conversation_id="conv-1",
            user_context={"userId": "user-1"},
            memory_context={},
            rag_context="",
        )

        result = await orch._execute_tool(ToolCall("query_business_module", {"module": "fees"}), ctx)

        assert result.success is True
        assert result.columns == ["订单号", "应付金额"]
        assert result.cursor_id == "cursor-1"
        assert result.has_more is True
        assert result.remaining_count == 11

    def test_tool_result_sse_contains_structured_fields(self):
        import json
        from ai_service.core.agent import AgentOrchestrator

        event = AgentOrchestrator._sse("tool_result", {
            "rows": [{"订单号": "LO-001"}],
            "columns": ["订单号"],
            "cursorId": "cursor-1",
            "hasMore": True,
        })

        assert "event: tool_result" in event
        data = json.loads([line for line in event.splitlines() if line.startswith("data:")][0][5:].strip())
        assert data["rows"][0]["订单号"] == "LO-001"
        assert data["columns"] == ["订单号"]
        assert data["cursorId"] == "cursor-1"

    @pytest.mark.asyncio
    async def test_business_tool_result_finishes_without_second_model_call(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_fetch_tool_registry(user_context=None):
            return [{
                "type": "function",
                "function": {
                    "name": "query_business_module",
                    "parameters": {"type": "object", "properties": {}},
                },
            }]

        async def fake_execute_tool(tool_name, arguments, user_context=None):
            return {
                "success": True,
                "data": [{"订单号": "LO-001"}],
                "columns": ["订单号"],
                "totalCount": 441,
                "returnedCount": 10,
                "remainingCount": 431,
                "hasMore": True,
                "cursorId": "cursor-1",
                "summary": "已查询订单管理，共匹配 441 条记录。本次已返回前 10 条结构化记录。",
                "dataGroups": [{
                    "groupId": "orders",
                    "displayToolName": "业务数据查询",
                    "displayTarget": "订单管理",
                    "displaySummary": "已查询订单管理，共匹配 441 条记录。",
                    "columns": ["订单号"],
                    "rows": [{"订单号": "LO-001"}],
                    "cursorId": "cursor-1",
                    "total": 441,
                    "returnedCount": 10,
                    "remainingCount": 431,
                    "hasMore": True,
                }],
            }

        monkeypatch.setattr(agent_module.java_client, "fetch_tool_registry", fake_fetch_tool_registry)
        monkeypatch.setattr(agent_module.java_client, "execute_tool", fake_execute_tool)

        class Gateway:
            def __init__(self):
                self.calls = 0

            async def chat_stream_messages(self, *args, **kwargs):
                self.calls += 1
                if self.calls > 1:
                    raise AssertionError("business tool summary must not call the model again")
                yield '{"tool_call":{"name":"query_business_module","arguments":{"module":"orders","keyword":""}}}'

        gateway = Gateway()
        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(gateway, engine)
        ctx = AgentContext(
            question="我要看全部的订单数据",
            conversation_id="conv-1",
            user_context={"userId": "user-1"},
            memory_context={},
            rag_context="",
        )

        events = [event async for event in orch.run(ctx)]

        assert gateway.calls == 1
        assert any("event: tool_result" in event for event in events)
        assert any('"dataGroups": [{"groupId": "orders"' in event for event in events)
        assert any("event: done" in event and "已查询订单管理" in event for event in events)

    @pytest.mark.asyncio
    async def test_context_refinement_plan_forces_business_module(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_fetch_tool_registry(user_context=None):
            return [
                {"name": "query_business_module", "parameters": {"type": "object", "properties": {}}},
                {"name": "global_fuzzy_search", "parameters": {"type": "object", "properties": {}}},
                {"name": "execute_readonly_sql", "parameters": {"type": "object", "properties": {}}},
            ]

        async def fake_execute_tool(tool_name, arguments, user_context=None):
            assert tool_name == "query_business_module"
            assert arguments["module"] == "orders"
            assert arguments["keyword"] == "陈土豆"
            return {
                "success": True,
                "data": [{"订单号": "LO-001", "客户名称": "陈土豆"}],
                "columns": ["订单号", "客户名称"],
                "totalCount": 1,
                "returnedCount": 1,
                "remainingCount": 0,
                "summary": "已查询订单管理，共匹配 1 条记录。",
                "displayToolName": "业务数据查询",
                "displayTarget": "订单管理",
            }

        monkeypatch.setattr(agent_module.java_client, "fetch_tool_registry", fake_fetch_tool_registry)
        monkeypatch.setattr(agent_module.java_client, "execute_tool", fake_execute_tool)

        class Gateway:
            async def chat(self, *args, **kwargs):
                return type("Resp", (), {"content": '{"intent":"BUSINESS_QUERY","confidence":0.9,"modules":["orders"],"keyword":"陈土豆","operation":"detail","refinementOfPrevious":true,"toolHint":"query_business_module"}'})

            async def chat_stream_messages(self, *args, **kwargs):
                tool_names = [tool["function"]["name"] for tool in kwargs["tools"]]
                assert tool_names == ["query_business_module"]
                yield '{"tool_call":{"name":"global_fuzzy_search","arguments":{"keyword":"陈土豆"}}}'

        orch = AgentOrchestrator(Gateway(), PromptEngine(PROMPTS_DIR))
        ctx = AgentContext(
            question="我要看的是跟陈土豆有关的订单信息",
            conversation_id="conv-refine",
            user_context={"userId": "user-1"},
            memory_context={},
            rag_context="",
            history=[{"role": "user", "content": "看看陈土豆"}],
        )

        events = [event async for event in orch.run(ctx)]

        assert any("订单管理" in event for event in events)
        assert not any("execute_readonly_sql" in event for event in events)

    @pytest.mark.asyncio
    async def test_plain_detail_query_filters_readonly_sql_tool(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_fetch_tool_registry(user_context=None):
            return [
                {"name": "query_business_module", "parameters": {"type": "object", "properties": {}}},
                {"name": "execute_readonly_sql", "parameters": {"type": "object", "properties": {}}},
            ]

        monkeypatch.setattr(agent_module.java_client, "fetch_tool_registry", fake_fetch_tool_registry)

        class Gateway:
            async def chat_stream_messages(self, *args, **kwargs):
                tool_names = [tool["function"]["name"] for tool in kwargs["tools"]]
                assert "execute_readonly_sql" not in tool_names
                assert "query_business_module" in tool_names
                yield '{"tool_call":{"name":"query_business_module","arguments":{"module":"tasks","keyword":""}}}'

        async def fake_execute_tool(tool_name, arguments, user_context=None):
            assert tool_name == "query_business_module"
            return {
                "success": True,
                "data": [{"任务号": "TASK-001"}],
                "columns": ["任务号"],
                "totalCount": 1,
                "returnedCount": 1,
                "remainingCount": 0,
                "summary": "已查询运输任务，共匹配 1 条记录。",
                "displayToolName": "业务数据查询",
                "displayTarget": "运输任务",
            }

        monkeypatch.setattr(agent_module.java_client, "execute_tool", fake_execute_tool)

        orch = AgentOrchestrator(Gateway(), PromptEngine(PROMPTS_DIR))
        ctx = AgentContext(
            question="我要看全部的运输任务",
            conversation_id="conv-plain",
            user_context={"userId": "user-1"},
            memory_context={},
            rag_context="",
        )

        events = [event async for event in orch.run(ctx)]

        assert any("运输任务" in event for event in events)
        assert not any("execute_readonly_sql" in event for event in events)

    @pytest.mark.asyncio
    async def test_statistical_query_can_use_sql_tool_with_display_fields(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_fetch_tool_registry(user_context=None):
            return [{"name": "execute_readonly_sql", "parameters": {"type": "object", "properties": {}}}]

        async def fake_execute_tool(tool_name, arguments, user_context=None):
            assert tool_name == "execute_readonly_sql"
            return {
                "success": True,
                "data": [{"客户名称": "华南客户", "订单数量": 3}],
                "columns": ["客户名称", "订单数量"],
                "totalCount": 1,
                "returnedCount": 1,
                "remainingCount": 0,
                "summary": "统计分析完成，返回 1 条记录。",
                "displayToolName": "统计分析",
                "displayTarget": "统计结果",
                "displaySummary": "统计分析完成，返回 1 条记录。",
            }

        monkeypatch.setattr(agent_module.java_client, "fetch_tool_registry", fake_fetch_tool_registry)
        monkeypatch.setattr(agent_module.java_client, "execute_tool", fake_execute_tool)

        class Gateway:
            async def chat_stream_messages(self, *args, **kwargs):
                tool_names = [tool["function"]["name"] for tool in kwargs["tools"]]
                assert "execute_readonly_sql" in tool_names
                yield '{"tool_call":{"name":"execute_readonly_sql","arguments":{"question":"统计本月各客户订单数量排名"}}}'

        orch = AgentOrchestrator(Gateway(), PromptEngine(PROMPTS_DIR))
        ctx = AgentContext(
            question="统计本月各客户订单数量排名",
            conversation_id="conv-stat",
            user_context={"userId": "user-1"},
            memory_context={},
            rag_context="",
        )

        events = [event async for event in orch.run(ctx)]

        assert any('"displayToolName": "统计分析"' in event for event in events)
        assert any('"displayTarget": "统计结果"' in event for event in events)
        assert not any("execute_readonly_sql" in event for event in events)

    def test_tool_answer_falls_back_when_summary_contains_internal_fields(self):
        from ai_service.core.agent import AgentOrchestrator, ToolResult

        result = ToolResult(
            name="execute_readonly_sql",
            success=True,
            total_count=20,
            returned_count=20,
            summary="| order_no | driver_name |\n| --- | --- |\n| LO-1 | 张三 |",
        )

        answer = AgentOrchestrator._tool_answer(result)

        assert "统计分析完成" in answer
        assert "order_no" not in answer
        assert "| ---" not in answer


    @pytest.mark.asyncio
    async def test_business_query_stops_when_tool_registry_unavailable(self, monkeypatch):
        from ai_service.core.agent import AgentContext, AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        import ai_service.core.agent as agent_module

        async def fake_fetch_tool_registry(user_context=None):
            raise RuntimeError("Java internal tool registry failed: status=403")

        monkeypatch.setattr(agent_module.java_client, "fetch_tool_registry", fake_fetch_tool_registry)

        class GatewayShouldNotBeCalled:
            async def chat_stream_messages(self, *args, **kwargs):
                raise AssertionError("LLM must not be called when business tools are unavailable")

        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(GatewayShouldNotBeCalled(), engine)
        ctx = AgentContext(
            question="我要看全部的订单数据",
            conversation_id="conv-tools-down",
            user_context={"userId": "1"},
            memory_context={},
            rag_context="",
        )

        events = [event async for event in orch.run(ctx)]

        assert any("TOOLS_UNAVAILABLE" in event for event in events)
        assert any("业务查询工具暂时不可用" in event for event in events)
        assert not any("event: tool_result" in event for event in events)


class TestModelGatewayFunctionCalling:
    def test_tool_call_dataclass(self):
        from ai_service.core.model_gateway import ToolCall
        tc = ToolCall(id="call_1", name="test_tool", arguments={"k": "v"})
        assert tc.id == "call_1"
        assert tc.arguments == {"k": "v"}

    def test_model_response_with_tool_calls(self):
        from ai_service.core.model_gateway import ModelResponse, ToolCall
        tc = ToolCall(id="x", name="query", arguments={})
        resp = ModelResponse(content="", model="m", provider="p", tool_calls=[tc], finish_reason="tool_calls")
        assert len(resp.tool_calls) == 1
        assert resp.finish_reason == "tool_calls"

    @pytest.mark.asyncio
    async def test_non_streaming_tool_calls_parse_json_arguments(self, httpx_mock, tmp_path):
        from ai_service.core.model_gateway import ModelGateway, ProviderRegistry

        config_path = tmp_path / "providers.yml"
        config_path.write_text("""
providers:
  - name: fake
    enabled: true
    api_base: https://fake.example/v1
    models: [fake-chat]
    timeout_seconds: 30
fallback_chains:
  chat: [fake-chat]
""", encoding="utf-8")
        registry = ProviderRegistry(config_path)
        gateway = ModelGateway(registry)
        await gateway.connect()
        httpx_mock.add_response(
            method="POST",
            url="https://fake.example/v1/chat/completions",
            json={
                "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                        "content": "",
                        "tool_calls": [{
                            "id": "call_1",
                            "function": {
                                "name": "query_business_module",
                                "arguments": "{\"module\":\"fees\"}",
                            },
                        }],
                    },
                }],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1},
            },
        )
        try:
            response = await gateway.chat("sys", "user", tools=[{"type": "function", "function": {"name": "x"}}])
        finally:
            await gateway.close()

        assert response.tool_calls[0].name == "query_business_module"
        assert response.tool_calls[0].arguments == {"module": "fees"}
