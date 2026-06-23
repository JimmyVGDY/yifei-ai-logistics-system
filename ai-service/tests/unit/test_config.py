"""配置与 Provider 注册表测试。"""

import pytest
import yaml
from pathlib import Path

from ai_service.config.settings import Settings


CONFIG_DIR = Path(__file__).parent.parent.parent / "config"


def test_settings_defaults():
    """Settings 使用正确的默认值。"""
    s = Settings()
    assert s.host == "127.0.0.1"
    assert s.port == 8001
    assert "localhost" in s.java_internal_url
    assert "localhost" in s.redis_url
    assert s.max_agent_iterations == 5
    assert s.model_timeout_seconds == 60.0


def test_providers_yml_valid():
    """providers.yml 合法且包含必须的 Provider。"""
    path = CONFIG_DIR / "providers.yml"
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    assert "providers" in data
    assert len(data["providers"]) >= 2  # 至少 deepseek + qwen-cloud

    names = {p["name"] for p in data["providers"]}
    assert "deepseek" in names
    assert "ollama-local" in names  # 预留

    # ollama-local 默认关闭
    ollama = [p for p in data["providers"] if p["name"] == "ollama-local"][0]
    assert ollama["enabled"] is False

    assert "fallback_chains" in data
    assert "chat" in data["fallback_chains"]
    assert len(data["fallback_chains"]["chat"]) >= 2  # 至少两级降级


def test_providers_have_priority():
    """每个 Provider 声明了 priority。"""
    path = CONFIG_DIR / "providers.yml"
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    for p in data["providers"]:
        assert "priority" in p, f"{p['name']} 缺少 priority"
        assert isinstance(p["priority"], int)


def test_fallback_chains_point_to_valid_providers():
    """所有降级链引用的模型属于已声明的 Provider。"""
    path = CONFIG_DIR / "providers.yml"
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    valid_models: set[str] = set()
    for p in data["providers"]:
        for m in p.get("models", []):
            valid_models.add(m)

    for chain_name, models in data["fallback_chains"].items():
        for m in models:
            assert m in valid_models, f"降级链 {chain_name} 引用未注册模型: {m}"
