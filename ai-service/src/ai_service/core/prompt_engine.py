"""Prompt 模板引擎：加载 YAML 模板、校验变量、Mustache 渲染。"""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

import chevron
import structlog
import yaml

logger = structlog.get_logger()


@dataclass
class PromptTemplateMeta:
    """模板的运行时配置元数据。"""
    template_code: str
    model: str
    temperature: float = 0.3
    max_tokens: int = 4096
    required_variables: list[str] = field(default_factory=list)
    optional_variables: list[str] = field(default_factory=list)
    output_schema: Optional[dict] = None


@dataclass
class PromptRenderResult:
    """渲染完成的 Prompt。"""
    system_prompt: str
    user_prompt: str
    model: str
    temperature: float
    max_tokens: int
    output_schema: Optional[dict] = None


class PromptEngine:
    """从 prompts/ 目录加载 YAML 模板，Mustache 渲染。"""

    _templates: dict[str, tuple[PromptTemplateMeta, str, str]]  # code -> (meta, system_template, user_template)

    def __init__(self, prompts_dir: Path):
        self._templates = {}
        self._load_all(prompts_dir)

    def _load_all(self, root: Path) -> None:
        for yml_path in root.rglob("*.yml"):
            try:
                with open(yml_path, encoding="utf-8") as f:
                    data = yaml.safe_load(f)

                code = yml_path.stem  # e.g., "assistant-chat"
                meta = PromptTemplateMeta(
                    template_code=code,
                    model=data["model"],
                    temperature=float(data.get("temperature", 0.3)),
                    max_tokens=int(data.get("max_tokens", 4096)),
                    required_variables=data.get("required_variables", []),
                    optional_variables=data.get("optional_variables", []),
                    output_schema=data.get("output_schema"),
                )
                system_tpl = data.get("system", "")
                user_tpl = data.get("user", "")

                self._templates[code] = (meta, system_tpl, user_tpl)
                logger.debug("template_loaded", code=code, model=meta.model)
            except Exception:
                logger.warning("template_load_failed", path=str(yml_path), exc_info=True)

    def render(self, template_code: str, variables: dict[str, Any]) -> PromptRenderResult:
        """加载模板、校验变量、Mustache 渲染、注入 JSON Schema。"""
        if template_code not in self._templates:
            raise ValueError(f"模板不存在: {template_code}")

        meta, system_tpl, user_tpl = self._templates[template_code]

        # 校验必填变量
        missing = set(meta.required_variables) - set(variables.keys())
        if missing:
            raise ValueError(f"缺少必填变量: {missing}")

        # 只接受模板声明的变量（防止变量注入）
        allowed = set(meta.required_variables) | set(meta.optional_variables)
        filtered = {k: v for k, v in variables.items() if k in allowed}

        # 自动注入 JSON Schema
        if meta.output_schema:
            import json
            filtered["json_schema"] = json.dumps(meta.output_schema, ensure_ascii=False)

        # Mustache 渲染
        system = chevron.render(system_tpl, filtered)
        user = chevron.render(user_tpl, filtered)

        return PromptRenderResult(
            system_prompt=system,
            user_prompt=user,
            model=meta.model,
            temperature=meta.temperature,
            max_tokens=meta.max_tokens,
            output_schema=meta.output_schema,
        )

    def get_meta(self, template_code: str) -> Optional[PromptTemplateMeta]:
        entry = self._templates.get(template_code)
        return entry[0] if entry else None


# Module-level singleton — set by lifespan in main.py
prompt_engine: Optional[PromptEngine] = None
