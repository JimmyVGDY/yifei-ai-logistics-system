"""意图分类与幻觉检查 —— 轻量 LLM 调用，结构化输出。"""

import json
from typing import Any, Optional

import structlog

from ai_service.core.model_gateway import ModelGateway
from ai_service.core.prompt_engine import PromptEngine

logger = structlog.get_logger()

# ── 默认回退值 ──
_DEFAULT_INTENT = "CHAT"
_DEFAULT_RISK_LEVEL = "SAFE"


class IntentClassifier:
    """轻量 LLM 意图分类器，调用 intent-classify 模板获取结构化意图。

    当 ModelGateway 不可用或模板缺失时返回安全默认值，
    不会向调用方抛出异常。
    """

    def __init__(self, gateway: ModelGateway, engine: PromptEngine):
        self._gateway = gateway
        self._engine = engine

    async def classify(self, question: str, history: Optional[list[str]] = None) -> dict[str, Any]:
        """分类用户意图。

        Args:
            question: 用户输入的问题文本。
            history:  最近 N 轮对话历史（可选，最多取最后 5 轮）。

        Returns:
            dict with keys: intent, confidence, modules, reason
            - intent:     意图类型枚举值
            - confidence: 置信度 0.0-1.0
            - modules:    涉及的模块列表
            - reason:     分类理由
        """
        # ── 空输入保护 ──
        if not question or not question.strip():
            logger.debug("intent_empty_input")
            return {"intent": _DEFAULT_INTENT, "confidence": 0.0, "modules": [], "reason": "empty_input"}

        # ── 模板缺失保护 ──
        if self._engine.get_meta("intent-classify") is None:
            logger.warning("template_missing", code="intent-classify")
            return {"intent": _DEFAULT_INTENT, "confidence": 0.0, "modules": [], "reason": "template_missing"}

        # ── 构建变量 ──
        variables: dict[str, Any] = {"question": question}
        if history:
            variables["history"] = "\n".join(history[-5:])

        # ── 渲染 Prompt ──
        try:
            rendered = self._engine.render("intent-classify", variables)
        except Exception as exc:
            logger.warning("intent_render_failed", error=str(exc)[:200])
            return {"intent": _DEFAULT_INTENT, "confidence": 0.0, "modules": [], "reason": "render_failed"}

        # ── 调用网关 ──
        try:
            response = await self._gateway.chat(
                system_prompt=rendered.system_prompt,
                user_prompt=rendered.user_prompt,
                task_type="intent-classify",
                temperature=rendered.temperature,
                max_tokens=rendered.max_tokens,
            )
        except Exception as exc:
            logger.warning("intent_gateway_failed", error=str(exc)[:200])
            return {"intent": _DEFAULT_INTENT, "confidence": 0.0, "modules": [], "reason": "gateway_failed"}

        # ── 解析 JSON 输出 ──
        try:
            parsed = json.loads(response.content)
        except json.JSONDecodeError as exc:
            logger.warning("intent_parse_failed", error=str(exc)[:200], raw=response.content[:300])
            return {"intent": _DEFAULT_INTENT, "confidence": 0.0, "modules": [], "reason": "parse_failed"}

        return {
            "intent": parsed.get("intent", _DEFAULT_INTENT),
            "confidence": float(parsed.get("confidence", 0.0)),
            "modules": parsed.get("modules", []),
            "reason": parsed.get("reason", ""),
        }


class GroundingGuard:
    """回答幻觉检查 —— 验证 AI 回答是否有数据支撑。

    检查规则：
    1. 回答中的每个业务数据声明必须有工具返回结果或引用来源支撑
    2. 检测分页数据虚假声称"完整列出"
    3. 无数据支撑的声明 → SUSPICIOUS；严肃的数据造假 → UNSAFE

    当 ModelGateway 不可用时，默认返回 SAFE（不阻塞正常流程）。
    """

    def __init__(self, gateway: ModelGateway, engine: PromptEngine):
        self._gateway = gateway
        self._engine = engine

    async def check(
        self,
        answer_text: str,
        tool_results: list[Any],
        citations: list[Any],
    ) -> dict[str, Any]:
        """检查回答的幻觉风险。

        Args:
            answer_text:  AI 生成的回答文本。
            tool_results: 工具调用返回的原始结果列表。
            citations:   回答中引用的数据来源列表。

        Returns:
            dict with keys: has_ungrounded_claim, has_pagination_misrepresentation, risk_level, issues
            - has_ungrounded_claim:          是否包含没有数据支撑的声明
            - has_pagination_misrepresentation: 是否只有分页数据却声称完整列出
            - risk_level:                    SAFE / SUSPICIOUS / UNSAFE
            - issues:                        具体问题列表 [{claim, reason}, ...]
        """
        # ── 空输入保护 ──
        if not answer_text or not answer_text.strip():
            logger.debug("grounding_empty_input")
            return {
                "has_ungrounded_claim": False,
                "has_pagination_misrepresentation": False,
                "risk_level": _DEFAULT_RISK_LEVEL,
                "issues": [],
            }

        # ── 模板缺失保护 ──
        if self._engine.get_meta("grounding-check") is None:
            logger.warning("template_missing", code="grounding-check")
            return {
                "has_ungrounded_claim": False,
                "has_pagination_misrepresentation": False,
                "risk_level": _DEFAULT_RISK_LEVEL,
                "issues": [],
            }

        # ── 序列化输入，限制长度防止 token 溢出 ──
        variables: dict[str, Any] = {
            "answer_text": answer_text[:8000],
            "tool_results": json.dumps(tool_results, ensure_ascii=False, default=str)[:4000],
            "citations": json.dumps(citations, ensure_ascii=False, default=str)[:2000],
        }

        # ── 渲染 Prompt ──
        try:
            rendered = self._engine.render("grounding-check", variables)
        except Exception as exc:
            logger.warning("grounding_render_failed", error=str(exc)[:200])
            return {
                "has_ungrounded_claim": False,
                "has_pagination_misrepresentation": False,
                "risk_level": _DEFAULT_RISK_LEVEL,
                "issues": [],
            }

        # ── 调用网关 ──
        try:
            response = await self._gateway.chat(
                system_prompt=rendered.system_prompt,
                user_prompt=rendered.user_prompt,
                task_type="grounding-check",
                temperature=rendered.temperature,
                max_tokens=rendered.max_tokens,
            )
        except Exception as exc:
            logger.warning("grounding_gateway_failed", error=str(exc)[:200])
            return {
                "has_ungrounded_claim": False,
                "has_pagination_misrepresentation": False,
                "risk_level": _DEFAULT_RISK_LEVEL,
                "issues": [],
            }

        # ── 解析 JSON 输出 ──
        try:
            parsed = json.loads(response.content)
        except json.JSONDecodeError as exc:
            logger.warning("grounding_parse_failed", error=str(exc)[:200], raw=response.content[:300])
            return {
                "has_ungrounded_claim": False,
                "has_pagination_misrepresentation": False,
                "risk_level": _DEFAULT_RISK_LEVEL,
                "issues": [],
            }

        return {
            "has_ungrounded_claim": parsed.get("has_ungrounded_claim", False),
            "has_pagination_misrepresentation": parsed.get("has_pagination_misrepresentation", False),
            "risk_level": parsed.get("risk_level", _DEFAULT_RISK_LEVEL),
            "issues": parsed.get("issues", []),
        }
