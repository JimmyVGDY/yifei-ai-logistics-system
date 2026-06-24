"""AI 问答与 SSE 流式端点。"""

import json
from typing import AsyncIterator, Optional

import structlog
from fastapi import APIRouter, Request, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, ValidationError

from ai_service.core.agent import AgentContext, AgentOrchestrator

logger = structlog.get_logger()


class ChatRequest(BaseModel):
    model_config = {"populate_by_name": True}  # 允许 camelCase 和 snake_case 双向兼容

    question: str
    conversation_id: str = Field(default="", alias="conversationId")
    history: list[dict] = Field(default_factory=list)
    user_context: dict = Field(default_factory=dict, alias="userContext")
    memory_context: dict = Field(default_factory=dict, alias="memoryContext")
    rag_context: str = Field(default="", alias="ragContext")
    model_policy: str = Field(default="API_ALLOWED", alias="modelPolicy")


router = APIRouter()

# Set by main.py lifespan
orchestrator: Optional[AgentOrchestrator] = None


@router.post("/stream")
async def chat_stream(request: Request, body: ChatRequest):
    """SSE 流式对话端点。Java SSE Proxy 透传到前端。"""
    logger.info("chat_stream_request", question=body.question[:50], conversation_id=body.conversation_id)

    try:
        return _build_sse_response(request, body)
    except Exception as exc:
        logger.error("chat_stream_fatal", error=str(exc), exc_info=True)
        # 如果 generator 都没来得及创建，返回一个能立马发 SSE error 的响应
        async def error_gen():
            yield _sse("error", {"code": "FATAL", "message": str(exc)[:500]})
            yield _sse("done", {"conversationId": body.conversation_id or "", "answer": "",
                                "elapsedMs": 0, "citationCount": 0, "toolCallCount": 0})
        return StreamingResponse(error_gen(), media_type="text/event-stream",
                                  headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


def _build_sse_response(request: Request, body: ChatRequest) -> StreamingResponse:
    async def event_generator() -> AsyncIterator[str]:
        try:
            ctx = AgentContext(
                question=body.question,
                conversation_id=body.conversation_id or "",
                user_context=body.user_context,
                memory_context=body.memory_context,
                rag_context=body.rag_context,
                model_policy=body.model_policy,
            )

            if orchestrator is None:
                yield _sse("error", {"code": "NOT_INITIALIZED", "message": "Agent 编排器未初始化"})
                yield _sse("done", {"conversationId": body.conversation_id or "", "answer": "",
                                    "elapsedMs": 0, "citationCount": 0, "toolCallCount": 0})
                return

            import os
            api_key = None
            if body.model_policy == "API_ALLOWED":
                api_key = body.user_context.get("apiKey") or os.getenv("SPRING_AI_OPENAI_API_KEY")
            async for sse_event in orchestrator.run(ctx, api_key=api_key):
                if await request.is_disconnected():
                    logger.info("sse_client_disconnected")
                    break
                yield sse_event
        except Exception as exc:
            logger.error("event_generator_fatal", error=str(exc), exc_info=True)
            yield _sse("error", {"code": "FATAL", "message": str(exc)[:500]})
            yield _sse("done", {"conversationId": body.conversation_id or "", "answer": "",
                                "elapsedMs": 0, "citationCount": 0, "toolCallCount": 0})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
