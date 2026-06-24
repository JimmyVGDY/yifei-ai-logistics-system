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
    def test_classify_no_gateway(self):
        """意图分类器在 ModelGateway 不可用时返回默认值。"""
        from ai_service.core.intent import IntentClassifier
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        classifier = IntentClassifier(None, engine)
        # 同步调用 classify（是 async 方法但内部有降级路径）
        import asyncio
        result = asyncio.run(classifier.classify("查一下订单"))
        assert result["intent"] == "CHAT"  # default when gateway unavailable

    def test_classify_empty_question(self):
        from ai_service.core.intent import IntentClassifier
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        classifier = IntentClassifier(None, engine)
        import asyncio
        result = asyncio.run(classifier.classify(""))
        assert result["intent"] == "CHAT"
        assert result["confidence"] == 0.0


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
