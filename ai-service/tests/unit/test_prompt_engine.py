"""Prompt 模板渲染引擎测试。"""

import pytest
import yaml
from pathlib import Path


PROMPTS_DIR = Path(__file__).parent.parent.parent / "prompts"


@pytest.mark.parametrize("template_path", sorted(PROMPTS_DIR.rglob("*.yml")))
def test_template_is_valid_yaml(template_path):
    """所有模板文件是合法 YAML。"""
    with open(template_path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    assert data is not None, f"{template_path} 为空"


@pytest.mark.parametrize("template_path", sorted(PROMPTS_DIR.rglob("*.yml")))
def test_template_has_required_fields(template_path):
    """所有模板包含必填字段。"""
    with open(template_path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    assert "model" in data, f"{template_path} 缺少 model"
    assert isinstance(data["model"], str)
    assert "required_variables" in data, f"{template_path} 缺少 required_variables"
    assert isinstance(data["required_variables"], list)
    assert "system" in data, f"{template_path} 缺少 system prompt"
    assert isinstance(data["system"], str)
    assert len(data["system"]) > 10, f"{template_path} system prompt 过短"


@pytest.mark.parametrize("template_path", sorted(PROMPTS_DIR.rglob("*.yml")))
def test_template_system_uses_declared_variables(template_path):
    """system prompt 中 {{xxx}} 变量必须在 required_variables 或 optional_variables 中声明。"""
    import re
    with open(template_path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    required = set(data.get("required_variables", []))
    optional = set(data.get("optional_variables", []))
    declared = required | optional

    all_text = data["system"] + (data.get("user", ""))
    used_vars = set(re.findall(r'\{\{(\w+)\}\}', all_text))

    undeclared = used_vars - declared
    assert not undeclared, f"{template_path.name}: 未声明变量 {undeclared}"


def test_intent_classify_has_output_schema():
    """意图分类模板必须包含 output_schema（结构化输出）。"""
    path = PROMPTS_DIR / "chat" / "intent-classify.yml"
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    assert "output_schema" in data, "意图分类模板必须有 output_schema"
    schema = data["output_schema"]
    assert "properties" in schema
    assert "intent" in schema["properties"]
    assert "confidence" in schema["properties"]


def test_grounding_check_has_no_temperature():
    """幻觉检查模板 temperature 应为 0（确定性输出）。"""
    path = PROMPTS_DIR / "chat" / "grounding-check.yml"
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    assert data["temperature"] == 0.0, "幻觉检查应使用 temperature=0"
