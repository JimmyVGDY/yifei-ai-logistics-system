"""AI 问答与 SSE 流式端点。"""

import json
from typing import AsyncIterator

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    question: str
    conversation_id: str = ""
    history: list[dict] = Field(default_factory=list)
    user_context: dict = Field(default_factory=dict)
    memory_context: dict = Field(default_factory=dict)
    rag_context: str = ""
    model_policy: str = "API_ALLOWED"


router = APIRouter()


@router.post("/stream")
async def chat_stream(request: Request, body: ChatRequest):
    """SSE 流式对话端点。Java SSE Proxy 透传到前端。"""

    async def event_generator() -> AsyncIterator[str]:
        try:
            # TODO: 接入 AgentOrchestrator 循环
            # async for event in orchestrator.run(body):
            #     if await request.is_disconnected():
            #         break
            #     yield event.to_sse()

            # 占位兜底
            yield _sse("thinking", {"message": "AI 服务正在初始化..."})
            yield _sse("token", {"delta": "你好！AI 服务即将上线。"})
            yield _sse("done", {"messageId": "", "conversationId": body.conversation_id, "usage": {}})
        except Exception as exc:
            yield _sse("error", {"code": "INTERNAL_ERROR", "message": str(exc)})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
