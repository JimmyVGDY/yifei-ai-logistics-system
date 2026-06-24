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
    """回答幻觉检查 —— 纯规则匹配，不调 LLM（对齐原 Java AiGroundingGuard）。

    不做用户可见的提示，只返回修复指令：
    - 没有证据却声称查到数据 → discard，管线用兜底回答替代
    - 只有分页数据却声称完整列出 → repaired，静默替换完整性措辞
    - 正常回答 → pass_through，原样放行
    """

    DATA_CLAIM_KEYWORDS = [
        "已查询", "查询结果", "系统中有", "共有", "命中", "记录",
        "数据库", "已找到", "查询到", "查到",
    ]
    NEGATION_PREFIXES = [
        "没有查到", "未找到", "不存在相关", "无相关记录", "未查询到",
        "没有查询到", "没有找到",
    ]
    FULL_CLAIM_KEYWORDS = [
        "完整列出", "全部列出", "所有记录", "不要省略", "全部数据", "完整明细",
    ]
    REPLACEMENTS = {
        "完整列出": "列出",
        "全部列出": "列出",
        "所有记录": "查询到的记录",
        "全部数据": "当前数据",
        "完整明细": "明细",
    }

    def check(self, answer: str,
              tool_results: list = None,
              citations: list = None,
              data_results: list = None) -> dict:
        """校验回答的证据支撑，返回修复指令。"""
        if not answer or not answer.strip():
            return {"action": "pass_through", "answer": answer, "issues": []}

        has_evidence = self._has_evidence(citations, tool_results, data_results)
        has_partial = self._has_partial_data(data_results)
        is_data_claim = self._looks_like_data_claim(answer)
        is_full_claim = self._claims_full_result(answer)

        issues = []
        result = answer

        # 无证据但声称查到数据 → discard
        if not has_evidence and is_data_claim:
            return {"action": "discard", "answer": answer,
                    "issues": ["UNSUPPORTED_DATA_CLAIM"]}

        # 仅分页数据却声称完整列出 → repaired
        if has_partial and is_full_claim:
            issues.append("PARTIAL_DATA_AS_FULL")
            result = self._tone_down_completeness(answer)

        if not issues:
            return {"action": "pass_through", "answer": result, "issues": []}
        return {"action": "repaired", "answer": result, "issues": issues}

    def _has_evidence(self, citations, tool_results, data_results) -> bool:
        if citations and len(citations) > 0:
            return True
        for tr in (tool_results or []):
            if isinstance(tr, dict) and tr.get("success"):
                return True
        for dr in (data_results or []):
            if isinstance(dr, dict) and dr.get("rows") and len(dr["rows"]) > 0:
                return True
        return False

    def _has_partial_data(self, data_results) -> bool:
        for dr in (data_results or []):
            if not isinstance(dr, dict):
                continue
            if dr.get("hasMore"):
                return True
            total = dr.get("total", 0)
            returned = dr.get("returnedCount", 0)
            if total > 0 and returned > 0 and returned < total:
                return True
        return False

    def _looks_like_data_claim(self, answer: str) -> bool:
        for neg in self.NEGATION_PREFIXES:
            if neg in answer:
                return False
        for kw in self.DATA_CLAIM_KEYWORDS:
            if kw in answer:
                return True
        return False

    def _claims_full_result(self, answer: str) -> bool:
        return any(kw in answer for kw in self.FULL_CLAIM_KEYWORDS)

    def _tone_down_completeness(self, answer: str) -> str:
        result = answer
        for old, new in self.REPLACEMENTS.items():
            result = result.replace(old, new)
        return result
