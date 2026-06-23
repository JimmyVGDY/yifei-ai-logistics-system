"""AI 问答与 SSE 流式端点。"""

import json
from typing import AsyncIterator, Optional

import structlog
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ai_service.core.agent import AgentContext, AgentOrchestrator

logger = structlog.get_logger()


class ChatRequest(BaseModel):
    question: str
    conversation_id: str = ""
    history: list[dict] = Field(default_factory=list)
    user_context: dict = Field(default_factory=dict)
    memory_context: dict = Field(default_factory=dict)
    rag_context: str = ""
    model_policy: str = "API_ALLOWED"


router = APIRouter()

# Set by main.py lifespan
orchestrator: Optional[AgentOrchestrator] = None


@router.post("/stream")
async def chat_stream(request: Request, body: ChatRequest):
    """SSE 流式对话端点。Java SSE Proxy 透传到前端。"""

    async def event_generator() -> AsyncIterator[str]:
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
            return

        try:
            # API Key 优先级：Java 注入的 apiKey > 环境变量 SPRING_AI_OPENAI_API_KEY
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
