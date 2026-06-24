"""内部端点：长期记忆向量写入、召回、删除。"""

from fastapi import APIRouter
from pydantic import BaseModel, Field

from ai_service.memory.merger import MemoryMergeService
from ai_service.memory.extractor import MemoryExtractor
from ai_service.core.model_gateway import gateway as model_gateway
from ai_service.core.prompt_engine import prompt_engine as pe

router = APIRouter()

# 由 main.py lifespan 初始化
merger: MemoryMergeService = MemoryMergeService()
extractor: MemoryExtractor = MemoryExtractor(model_gateway, pe) if model_gateway and pe else MemoryExtractor(None, None)


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
    result = await merger.recall(body.user_id, body.question, body.limit)
    return {
        "memories": [
            {"content": m.content, "memoryType": m.memory_type, "scope": m.scope}
            for m in result.memories
        ],
        "hitCount": result.hit_count,
        "context": result.context,
    }


@router.post("/sync")
async def sync_memory(request: MemorySyncRequest):
    """Java 批准/删除记忆后同步到 Qdrant。"""
    # Qdrant 写入/删除由 Java 侧管理，Python 侧仅做向量检索。
    # 当前 Qdrant 的写入仍由 Java AiMemoryService 通过 MySQL 真值 + Qdrant 向量副本的方式管理。
    return {"ok": True}


@router.post("/extract")
async def extract_memories(body: ExtractRequest):
    """从对话中提炼候选记忆（对话结束时调用）。"""
    result = await extractor.extract(body.conversation_text)
    return {
        "worthRemembering": result.worth_remembering,
        "candidates": result.candidates,
    }
