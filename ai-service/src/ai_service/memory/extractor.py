"""长期记忆提炼器 — LLM 判断 + 关键词降级兜底。"""

import re
from dataclasses import dataclass, field
from typing import Optional

import structlog

from ai_service.core.model_gateway import ModelGateway
from ai_service.core.prompt_engine import PromptEngine

logger = structlog.get_logger()

# 关键词降级规则：仅匹配明确的长期偏好表达
PREFERENCE_PATTERNS = [
    (r"以后(都|就|按|用|要|只).{1,20}", "FORMAT_PREFERENCE"),
    (r"我(主要|一般|通常|习惯|喜欢).{1,20}", "QUERY_PREFERENCE"),
    (r"(不要|别再|别再给我).{1,15}", "QUERY_PREFERENCE"),
    (r"记住了?吗?", "FORMAT_PREFERENCE"),
]


@dataclass
class ExtractionResult:
    worth_remembering: bool = False
    candidates: list[dict] = field(default_factory=list)


class MemoryExtractor:
    """从对话中提炼长期记忆。

    LLM 优先：使用 memory-extract 模板让模型判断是否值得记忆。
    LLM 不可用时降级为关键词匹配（仅抓取明确的长期偏好表达）。
    """

    def __init__(self, gateway: Optional[ModelGateway], engine: Optional[PromptEngine]):
        self.gateway = gateway
        self.engine = engine

    async def extract(self, conversation_text: str) -> ExtractionResult:
        """从对话文本中提炼候选记忆。"""
        if not conversation_text or len(conversation_text.strip()) < 5:
            return ExtractionResult()

        # 尝试 LLM 提炼
        if self.gateway and self.engine:
            try:
                return await self._extract_with_llm(conversation_text)
            except Exception as exc:
                logger.debug("memory_extract_llm_failed", error=str(exc)[:100])

        # 降级：关键词匹配
        return self._extract_with_keywords(conversation_text)

    async def _extract_with_llm(self, text: str) -> ExtractionResult:
        rendered = self.engine.render("memory-extract", {
            "user_message": text,
            "assistant_message": "",
            "tool_targets": "",
        })
        response = await self.gateway.chat(
            system_prompt=rendered.system_prompt,
            user_prompt=rendered.user_prompt,
            task_type="memory-extract",
            temperature=0.1,
            max_tokens=512,
        )
        import json
        try:
            data = json.loads(response.content)
            worth = data.get("worth_remembering", False)
            candidates = data.get("candidates", [])
            return ExtractionResult(worth_remembering=worth, candidates=candidates)
        except (json.JSONDecodeError, KeyError):
            return ExtractionResult()

    def _extract_with_keywords(self, text: str) -> ExtractionResult:
        """关键词降级：仅抓取明确的长期偏好表达。"""
        candidates = []
        for pattern, mem_type in PREFERENCE_PATTERNS:
            match = re.search(pattern, text)
            if match:
                snippet = text[max(0, match.start() - 5):min(len(text), match.end() + 15)]
                candidates.append({
                    "content": snippet.strip(),
                    "memory_type": mem_type,
                    "scope": "GLOBAL",
                    "confidence": 0.4,
                })
        worth = len(candidates) > 0
        return ExtractionResult(worth_remembering=worth, candidates=candidates)
