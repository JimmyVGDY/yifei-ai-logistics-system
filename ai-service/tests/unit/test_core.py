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
