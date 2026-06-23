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
    def test_check_no_gateway(self):
        """幻觉检查在 ModelGateway 不可用时返回安全默认值。"""
        from ai_service.core.intent import GroundingGuard
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        guard = GroundingGuard(None, engine)
        import asyncio
        result = asyncio.run(guard.check("answer", [], []))
        assert result["risk_level"] == "SAFE"
        assert result["has_ungrounded_claim"] is False

    def test_check_empty_answer(self):
        from ai_service.core.intent import GroundingGuard
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        guard = GroundingGuard(None, engine)
        import asyncio
        result = asyncio.run(guard.check("", [], []))
        assert result["risk_level"] == "SAFE"


class TestAgentOrchestrator:
    def test_parse_tool_calls_from_text(self):
        from ai_service.core.agent import AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(None, engine)

        text = 'some text {"tool_call": {"name": "query_business_module", "arguments": {"module": "订单管理"}}} more text'
        calls = orch._parse_tool_calls_from_text(text)
        assert len(calls) >= 1
        assert calls[0].name == "query_business_module"
        assert calls[0].arguments == {"module": "订单管理"}

    def test_parse_no_tool_calls(self):
        from ai_service.core.agent import AgentOrchestrator
        from ai_service.core.prompt_engine import PromptEngine
        engine = PromptEngine(PROMPTS_DIR)
        orch = AgentOrchestrator(None, engine)

        text = "只是一段普通的回答文本"
        calls = orch._parse_tool_calls_from_text(text)
        assert len(calls) == 0

    def test_sse_format(self):
        from ai_service.core.agent import AgentOrchestrator
        sse = AgentOrchestrator._sse("thinking", {"message": "test"})
        assert "event: thinking" in sse
        assert "data:" in sse
