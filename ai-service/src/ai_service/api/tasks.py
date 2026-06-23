"""内部端点：异步任务提交、查询、取消。"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ai_service.infrastructure.redis_client import redis_client, RedisNotConnectedError

router = APIRouter()


class SubmitTaskRequest(BaseModel):
    task_type: str
    params: dict = Field(default_factory=dict)


@router.post("/submit")
async def submit_task(body: SubmitTaskRequest):
    """提交异步 AI 任务（每日简报、异常检测、深度分析等）。"""
    import uuid
    task_id = f"task_{uuid.uuid4().hex[:12]}"
    try:
        await redis_client.client.hset(f"task:{task_id}:status", mapping={
            "status": "PENDING",
            "progress": "0",
            "current_step": "",
        })
    except RedisNotConnectedError:
        raise HTTPException(status_code=503, detail="Redis 不可用，无法提交任务")

    # TODO: 根据 body.task_type 启动后台执行，写完进度到 Redis
    return {"taskId": task_id, "status": "PENDING"}


@router.get("/{task_id}/status")
async def get_task_status(task_id: str):
    """查询异步任务状态与进度。"""
    try:
        status = await redis_client.client.hgetall(f"task:{task_id}:status")
    except RedisNotConnectedError:
        raise HTTPException(status_code=503, detail="Redis 不可用")
    if not status:
        return {"taskId": task_id, "status": "NOT_FOUND"}
    return {"taskId": task_id, **status}


@router.post("/{task_id}/cancel")
async def cancel_task(task_id: str):
    """取消进行中的异步任务。"""
    try:
        await redis_client.client.hset(f"task:{task_id}:status", "status", "CANCELLED")
    except RedisNotConnectedError:
        raise HTTPException(status_code=503, detail="Redis 不可用")
    return {"taskId": task_id, "status": "CANCELLED"}
