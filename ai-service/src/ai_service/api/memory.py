"""内部端点：长期记忆向量写入、召回、删除。"""

from fastapi import APIRouter, Query
from pydantic import BaseModel, Field

router = APIRouter()


class MemorySyncRequest(BaseModel):
    action: str                    # upsert | delete
    memory_id: int
    content: str = ""
    metadata: dict = Field(default_factory=dict)


class RecallRequest(BaseModel):
    user_id: str
    question: str
    limit: int = 5


class ExtractRequest(BaseModel):
    conversation_text: str
    existing_memories: list[str] = Field(default_factory=list)


@router.post("/recall")
async def recall_memories(body: RecallRequest):
    """召回用户长期记忆（向量检索 + 冲突消解）。"""
    # TODO: MemoryMergeService.recall_merged(body.user_id, body.question)
    return {"memories": []}


@router.post("/sync")
async def sync_memory(request: MemorySyncRequest):
    """Java 批准/删除记忆后同步到 Qdrant。"""
    # TODO:
    #   action=upsert → vectorize → write Qdrant → return point_id
    #   action=delete → delete from Qdrant
    return {"ok": True}


@router.post("/extract")
async def extract_memories(body: ExtractRequest):
    """从对话中提炼候选记忆（对话结束时调用）。"""
    # TODO: MemoryExtractor.extract(body.conversation_text, body.existing_memories)
    return {"candidates": []}
