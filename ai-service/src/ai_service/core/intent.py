"""意图分类与幻觉检查。"""

from dataclasses import dataclass, field
from typing import Any
import structlog

logger = structlog.get_logger()


@dataclass
class IntentPlan:
    """LLM/规则共同产出的工具规划建议，最终仍由 Java 白名单校验。"""
    # 粗粒度意图，决定是否继续走工具链、澄清、纠偏或普通聊天。
    intent: str = "BUSINESS_QUERY"
    # 规划置信度低于阈值时，Agent 会回退到确定性规则结果。
    confidence: float = 0.0
    # 建议查询的标准模块码，只允许白名单模块，不能放数据库表名。
    modules: list[str] = field(default_factory=list)
    # 从用户话术或上一轮上下文抽取出的业务实体关键词。
    keyword: str = ""
    # 预留给后端统一时间归一化的范围字段，不能由模型直接拼 SQL。
    time_range: dict[str, str] = field(default_factory=dict)
    # detail 表示明细查询，analysis 才允许开放临时 SQL 工具。
    operation: str = "detail"
    # 标记“只看订单/这个客户的费用”这类对上一轮实体的收窄。
    refinement_of_previous: bool = False
    # 宽泛且低置信度的问题应澄清，而不是直接跨模块乱查。
    needs_clarification: bool = False
    # 工具建议只用于 Python 侧过滤和参数纠偏，Java 仍做最终校验。
    tool_hint: str = ""
    # 便于调试和测试确认本次规划来自规则还是 LLM。
    reason: str = ""

    @classmethod
    def from_dict(cls, value: dict[str, Any] | None) -> "IntentPlan":
        """从 LLM JSON 安全构造计划对象，字段异常时使用保守默认值。"""
        if not isinstance(value, dict):
            return cls()
        modules = value.get("modules") or []
        if isinstance(modules, str):
            # 兼容模型偶尔把数组写成单个字符串的情况。
            modules = [modules]
        time_range = value.get("timeRange") or value.get("time_range") or {}
        if not isinstance(time_range, dict):
            time_range = {}
        return cls(
            intent=str(value.get("intent") or "BUSINESS_QUERY"),
            confidence=_float_between(value.get("confidence"), 0.0, 1.0),
            modules=[str(item) for item in modules if item],
            keyword=str(value.get("keyword") or ""),
            time_range={str(k): str(v) for k, v in time_range.items() if v is not None},
            operation=str(value.get("operation") or "detail"),
            refinement_of_previous=bool(value.get("refinementOfPrevious") or value.get("refinement_of_previous")),
            needs_clarification=bool(value.get("needsClarification") or value.get("needs_clarification")),
            tool_hint=str(value.get("toolHint") or value.get("tool_hint") or ""),
            reason=str(value.get("reason") or ""),
        )


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
    MODULE_ALIASES = {
        # 这些别名只做语义规划，不替代 Java 侧 AiQueryNormalizer 和权限校验。
        "orders": ("订单", "订单管理", "运单管理", "LO", "ORD"),
        "waybills": ("运单中心", "运单号", "WB"),
        "customers": ("客户", "客户名", "客户名称", "联系人"),
        "dispatches": ("调度", "派车", "派单"),
        "tasks": ("任务", "运输任务", "配送任务"),
        "tracks": ("轨迹", "物流轨迹", "位置"),
        "drivers": ("司机", "驾驶员"),
        "vehicles": ("车辆", "车牌"),
        "exceptions": ("异常", "报障", "投诉"),
        "fees": ("费用", "结算", "收款"),
        "operationLogs": ("日志", "traceId", "operationId", "loginSessionId"),
        "users": ("用户", "账号", "账户"),
        "roles": ("角色", "权限"),
        "files": ("文件", "附件", "上传记录"),
    }
    SYSTEM_MODULES = {"users", "roles", "files", "operationLogs"}

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

    def deterministic_plan(self, question: str, previous_message: str = "") -> IntentPlan:
        """不依赖模型的规划兜底，用于快路径和 LLM 失败回退。"""
        text = self._normalize(question)
        previous = self._normalize(previous_message)
        intent = self.classify(question, previous_message).get("intent", "BUSINESS_QUERY")
        modules = self._detect_modules(text)
        keyword = self._extract_keyword(text, modules)
        previous_keyword = self._extract_keyword(previous, self._detect_modules(previous))
        # “这个客户的订单”这类追问没有新关键词，需要从上一轮实体继承。
        refinement = bool(previous_keyword and modules and not keyword and self._looks_like_refinement(text))
        if refinement:
            keyword = previous_keyword
        # 只有分析类问题才给 SQL 工具开门，普通明细查询必须走业务模块。
        operation = "analysis" if self._looks_like_sql_analysis(text) else "detail"
        tool_hint = ""
        if operation == "analysis":
            tool_hint = "execute_readonly_sql"
        elif len(modules) == 1:
            tool_hint = "query_business_module"
        elif keyword:
            tool_hint = "global_fuzzy_search"
        needs_clarification = text in {"异常", "数据", "这个月的", "这个月"} and not modules
        return IntentPlan(
            intent=intent,
            confidence=0.7 if tool_hint or refinement else 0.45,
            modules=modules,
            keyword=keyword,
            operation=operation,
            refinement_of_previous=refinement,
            needs_clarification=needs_clarification,
            tool_hint=tool_hint,
            reason="deterministic",
        )

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

    def _detect_modules(self, text: str) -> list[str]:
        """按业务别名识别候选模块；普通业务规划默认剔除系统模块。"""
        if not text:
            return []
        found = []
        lower = text.lower()
        for module, aliases in self.MODULE_ALIASES.items():
            if any(alias.lower() in lower for alias in aliases):
                found.append(module)
        if found and not any(module in self.SYSTEM_MODULES for module in found):
            return [module for module in found if module not in self.SYSTEM_MODULES]
        return found

    def _extract_keyword(self, text: str, modules: list[str]) -> str:
        """从用户话术中剥掉查询动作和模块词，留下更像实体的关键词。"""
        if not text:
            return ""
        cleaned = text
        for word in (
            "我要看", "我想看", "帮我", "查询", "查一下", "查看", "看看", "看一下", "看下", "给我",
            "相关", "有关", "关于", "跟", "的是", "的信息", "信息", "数据", "记录", "列表", "明细",
            "这个客户", "这个人", "他的", "她的", "它的", "只看", "只查",
        ):
            cleaned = cleaned.replace(word, " ")
        for module in modules:
            for alias in self.MODULE_ALIASES.get(module, ()):
                # 明确模块词不应该被当成 keyword，例如“全部订单”不能把“订单”塞给后端搜索。
                cleaned = cleaned.replace(alias, " ")
        cleaned = cleaned.strip(" ，,。；;：:！？?、")
        parts = [part for part in cleaned.split() if len(part) >= 2]
        if not parts:
            return ""
        keyword = parts[0].strip(" ，,。；;：:！？?、")
        if keyword in {"所有", "全部", "剩余", "继续", "这个", "那个"}:
            return ""
        return keyword

    def _looks_like_refinement(self, text: str) -> bool:
        """判断当前句是否在限定上一轮实体，而不是开启一个全新查询。"""
        return any(word in text for word in ("相关", "有关", "关于", "这个", "那个", "他", "她", "它", "只看", "只查", "跟"))

    def _looks_like_sql_analysis(self, text: str) -> bool:
        """统计、聚合、排名、关联类表达才属于 SQL 分析候选。"""
        return any(word in text for word in (
            "sql", "连表", "关联", "统计", "汇总", "数量", "总数", "排名",
            "最多", "最少", "平均", "多少", "占比", "比例", "group", "join",
        ))

    @staticmethod
    def _normalize(message: str) -> str:
        if not message:
            return ""
        return message.replace('　', ' ') \
            .replace('​', '').replace('‌', '').replace('‍', '').replace('﻿', '') \
            .replace(' ', '').strip()


def _float_between(value: Any, lower: float, upper: float) -> float:
    """把模型返回的置信度压到安全范围，非法值按 lower 处理。"""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return lower
    return max(lower, min(upper, number))


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
