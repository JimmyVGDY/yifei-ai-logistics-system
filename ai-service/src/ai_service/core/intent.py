"""意图分类与幻觉检查 —— 纯规则匹配（对齐原 Java AiConversationIntentClassifier + AiGroundingGuard）"""

import structlog

logger = structlog.get_logger()


class IntentClassifier:
    """对话意图分类器 —— 关键词匹配，不调 LLM。

    对齐原 Java AiConversationIntentClassifier：
    区分"用户在聊天/纠偏/限定范围"和"用户真的要查业务数据"。
    """

    CONTROL_WORDS = {
        "记住", "记住了吗", "以后", "下次",
        "不要再", "不是让你", "你理解错", "不要同时", "别同时",
    }
    CORRECTION_WORDS = {
        "记住", "以后", "下次", "我说的是", "我的意思", "不是让你",
        "你理解错", "不要同时", "别同时", "记住了吗", "明白了吗", "懂了吗", "听懂了吗",
    }
    CHAT_WORDS = {
        "你是谁", "能做什么", "怎么用", "说明一下", "解释一下",
        "你好", "谢谢",
    }
    QUERY_WORDS = {
        "查", "查询", "搜索", "看看", "看一下", "统计", "汇总", "分析", "列出", "给我看",
    }
    AMBIGUOUS_TERMS = {"异常任务", "任务异常", "异常订单", "异常运单"}
    CONTINUATION_WORDS = {
        "只要", "只看", "剩下", "剩余", "继续", "下一页",
        "待处理", "运输中", "已完成",
    }

    def __init__(self):
        pass

    def classify(self, question: str, previous_message: str = "") -> dict:
        """分类用户意图。返回 {intent, direct_answer, reason}。"""
        text = self._normalize(question)
        if not text:
            return {"intent": "CHAT", "direct_answer": "", "reason": "empty"}

        # 1. 纠偏/偏好
        if self._is_control_preference(text):
            scope = self._detect_scope(text)
            return {
                "intent": "CORRECTION",
                "direct_answer": f"明白了，后续我会优先按『{scope}』理解你的查询范围。"
                                 f"等你说具体查询条件时，我再按这个范围执行只读查询。",
                "reason": "用户纠正 AI 行为或限定范围",
            }

        # 2. 普通聊天
        if self._is_chat(text):
            return {"intent": "CHAT", "direct_answer": "", "reason": "聊天/系统问答"}

        # 3. 上下文续查
        if previous_message and self._is_continuation(text):
            return {"intent": "CONTINUATION", "direct_answer": "", "reason": "继承上轮查询状态"}

        # 4. 歧义澄清
        if not previous_message and text in self.AMBIGUOUS_TERMS:
            return {
                "intent": "CLARIFY",
                "direct_answer": "你是想查运输任务中的异常任务、异常管理中的异常记录，"
                                 "还是订单/运单的异常状态？请补充一个范围。",
                "reason": "业务对象存在歧义",
            }

        # 5. 业务查询
        return {"intent": "BUSINESS_QUERY", "direct_answer": "", "reason": "业务查询"}

    def _is_control_preference(self, text: str) -> bool:
        has_control = any(w in text for w in self.CONTROL_WORDS)
        if not has_control:
            return False
        # 只查/只看 但不带纠偏词 → 仍是查询
        if any(w in text for w in ("只查", "只看")):
            explicit = any(w in text for w in self.CORRECTION_WORDS)
            return explicit or not self._has_query_action(text)
        return any(w in text for w in self.CORRECTION_WORDS)

    def _is_chat(self, text: str) -> bool:
        return any(w in text for w in self.CHAT_WORDS)

    def _is_continuation(self, text: str) -> bool:
        return any(w in text for w in self.CONTINUATION_WORDS)

    def _has_query_action(self, text: str) -> bool:
        return any(w in text for w in self.QUERY_WORDS)

    def _detect_scope(self, text: str) -> str:
        if "运输任务" in text: return "运输任务模块"
        if "异常管理" in text: return "异常管理模块"
        if "订单" in text: return "订单管理模块"
        if "运单" in text: return "运单中心"
        return "你刚刚限定的范围"

    @staticmethod
    def _normalize(message: str) -> str:
        if not message:
            return ""
        return message.replace('　', ' ') \
            .replace('​', '').replace('‌', '').replace('‍', '').replace('﻿', '') \
            .replace(' ', '').strip()


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
