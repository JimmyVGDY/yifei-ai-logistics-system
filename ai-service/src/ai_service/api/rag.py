"""内部端点：RAG 文档检索与索引。"""

from fastapi import APIRouter
from pydantic import BaseModel, Field

from ai_service.rag.retriever import KnowledgeRetriever

router = APIRouter()

retriever = KnowledgeRetriever()


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5


class ReindexRequest(BaseModel):
    paths: list[str] = Field(default_factory=list)


@router.post("/search")
async def search_knowledge(body: SearchRequest):
    """RAG 文档语义搜索。"""
    response = await retriever.search(body.query, body.top_k)
    return {
        "results": [
            {"source": r.source, "title": r.title, "snippet": r.snippet, "score": r.score}
            for r in response.results
        ],
        "total": response.total,
    }


@router.post("/reindex")
async def reindex_documents(body: ReindexRequest):
    """重建文档索引（Java 启动或文档变更时调用）。"""
    # 文档索引当前由 Java 侧 AiDocumentIndexer 管理，Python 侧仅做检索。
    # 后续可迁移到 Python 侧统一管理。
    return {"indexed": 0, "skipped": 0, "message": "文档索引由 Java 侧管理，Python 负责检索"}
