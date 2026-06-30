"""长期记忆合并与召回服务。"""

from dataclasses import dataclass, field
from typing import Optional
import asyncio

import structlog

from ai_service.infrastructure.qdrant_client import qdrant_client

logger = structlog.get_logger()

COLLECTION_NAME = "logistics_ai_user_memory"
MAX_RECALL = 5


@dataclass
class MemoryItem:
    point_id: str
    content: str
    memory_type: str = ""
    scope: str = "GLOBAL"
    priority_weight: float = 1.0
    confidence: float = 0.5


@dataclass
class MemoryRecallResult:
    memories: list[MemoryItem] = field(default_factory=list)
    hit_count: int = 0
    context: str = ""


class MemoryMergeService:
    """长期记忆合并与召回。

    从 Qdrant 按 userId 过滤召回向量最近邻记忆，按优先级排序合并。
    Embedding 由调用方通过哈希降级提供（当 Ollama bge-m3 不可用时）。
    """

    async def recall(self, user_id: str, question: str, limit: int = MAX_RECALL) -> MemoryRecallResult:
        """召回与问题相关的用户长期记忆。"""
        if not qdrant_client.available:
            logger.debug("qdrant_unavailable_skip_memory_recall")
            return MemoryRecallResult()

        try:
            # 使用确定性哈希作为简易向量（bge-m3 不可用时的降级方案）
            from qdrant_client.models import Filter, FieldCondition, MatchValue
            vector = self._hash_vector(question)

            results = await asyncio.to_thread(
                qdrant_client.client.query_points,
                collection_name=COLLECTION_NAME,
                query=vector,
                query_filter=Filter(must=[
                    FieldCondition(key="userId", match=MatchValue(value=user_id))
                ]),
                limit=limit,
            )

            memories = []
            for point in results.points:
                payload = point.payload or {}
                content = payload.get("content", payload.get("text", ""))
                if not content:
                    continue
                memories.append(MemoryItem(
                    point_id=str(point.id),
                    content=str(content)[:500],
                    memory_type=payload.get("memoryType", payload.get("memory_type", "")),
                    scope=payload.get("scope", "GLOBAL"),
                    priority_weight=float(payload.get("priorityWeight", payload.get("priority_weight", 1.0))),
                    confidence=float(payload.get("confidence", 0.5)),
                ))

            # 按优先级排序
            memories.sort(key=lambda m: (m.priority_weight, m.confidence), reverse=True)

            # 构建上下文文本
            context_parts = []
            for m in memories:
                context_parts.append(f"- {m.content}")
            context = "\n".join(context_parts) if context_parts else ""

            return MemoryRecallResult(
                memories=memories,
                hit_count=len(memories),
                context=context,
            )
        except Exception as exc:
            logger.warning("memory_recall_failed", error=str(exc)[:200])
            return MemoryRecallResult()

    @staticmethod
    def _hash_vector(text: str, dim: int = 1024) -> list[float]:
        """确定性哈希向量（Ollama bge-m3 不可用时的降级方案）。"""
        import hashlib
        h = hashlib.sha256(text.encode("utf-8")).digest()
        # 循环扩展到目标维度
        result = []
        for i in range(dim):
            byte_val = h[i % len(h)]
            result.append((byte_val / 255.0) * 2.0 - 1.0)
        return result
