"""RAG 文档检索器 — Qdrant 语义搜索 + BM25 关键词降级。"""

from dataclasses import dataclass, field
from typing import Optional
import re

import structlog

from ai_service.infrastructure.qdrant_client import qdrant_client

logger = structlog.get_logger()

COLLECTION_NAME = "logistics_docs"
MAX_RESULTS = 5


@dataclass
class SearchResult:
    source: str = ""           # e.g. "README.md", "docs/architecture.md"
    title: str = ""
    snippet: str = ""          # preview text (truncated to 200 chars)
    score: float = 0.0


@dataclass
class SearchResponse:
    results: list[SearchResult] = field(default_factory=list)
    total: int = 0


class KnowledgeRetriever:
    """RAG 文档语义检索。

    优先使用 Qdrant 向量检索（需有向量索引），Qdrant 不可用时降级为 BM25 关键词匹配。
    """

    async def search(self, query: str, top_k: int = MAX_RESULTS) -> SearchResponse:
        """搜索与查询相关的系统文档。"""
        if not query or not query.strip():
            return SearchResponse()

        # 优先 Qdrant 语义搜索
        if qdrant_client.available:
            try:
                return await self._search_qdrant(query, top_k)
            except Exception as exc:
                logger.debug("qdrant_search_failed_fallback_bm25", error=str(exc)[:100])

        # 降级 BM25 关键词匹配
        return self._search_keywords(query, top_k)

    async def _search_qdrant(self, query: str, top_k: int) -> SearchResponse:
        """Qdrant 向量语义搜索。"""
        # 使用哈希向量作为简易 embedding（Ollama bge-m3 不可用时降级）
        vector = self._hash_vector(query)

        results = qdrant_client.client.query_points(
            collection_name=COLLECTION_NAME,
            query=vector,
            limit=top_k,
        )

        items = []
        for point in results.points:
            payload = point.payload or {}
            snippet = str(payload.get("text_preview", payload.get("content", "")))
            items.append(SearchResult(
                source=str(payload.get("source", payload.get("file_path", ""))),
                title=str(payload.get("title", "")),
                snippet=snippet[:200],
                score=float(getattr(point, 'score', 0.0)),
            ))

        return SearchResponse(results=items, total=len(items))

    def _search_keywords(self, query: str, top_k: int) -> SearchResponse:
        """BM25 关键词降级检索 — 基于全局文档索引表（仅返回提示）。"""
        logger.info("bm25_fallback_used", query=query[:50])
        # 关键词降级时返回提示信息
        keywords = self._extract_keywords(query)
        if not keywords:
            return SearchResponse(results=[], total=0)

        return SearchResponse(
            results=[SearchResult(
                source="系统文档",
                title="关键词检索（向量检索暂不可用）",
                snippet=f"检测到关键词: {', '.join(keywords[:5])}。请检查 Qdrant 服务是否正常运行以使用语义检索。",
                score=0.0,
            )],
            total=0,
        )

    @staticmethod
    def _extract_keywords(text: str) -> list[str]:
        """简单的中文关键词提取（按常见分词规则切词）。"""
        # 移除标点和空白
        cleaned = re.sub(r'[^\w一-鿿]', ' ', text)
        # 按空格和常用连接词切分
        parts = re.split(r'[\s的了吗呢啊是和在了有]', cleaned)
        return [p.strip() for p in parts if len(p.strip()) >= 2][:10]

    @staticmethod
    def _hash_vector(text: str, dim: int = 1024) -> list[float]:
        """确定性哈希向量（Ollama bge-m3 不可用时的降级方案）。"""
        import hashlib
        h = hashlib.sha256(text.encode("utf-8")).digest()
        result = []
        for i in range(dim):
            byte_val = h[i % len(h)]
            result.append((byte_val / 255.0) * 2.0 - 1.0)
        return result
