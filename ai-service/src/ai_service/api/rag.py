"""内部端点：RAG 文档检索与索引。"""

from fastapi import APIRouter
from pydantic import BaseModel, Field

router = APIRouter()


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5


class ReindexRequest(BaseModel):
    paths: list[str] = Field(default_factory=list)


@router.post("/search")
async def search_knowledge(body: SearchRequest):
    """RAG 文档语义搜索。"""
    # TODO: KnowledgeRetriever.search(body.query, body.top_k)
    return {"results": []}


@router.post("/reindex")
async def reindex_documents(body: ReindexRequest):
    """重建文档索引（Java 启动或文档变更时调用）。"""
    # TODO: DocumentIndexer.reindex(body.paths)
    return {"indexed": 0, "skipped": 0}
