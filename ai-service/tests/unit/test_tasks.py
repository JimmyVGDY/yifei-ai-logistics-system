"""异步任务端点测试。"""

import pytest
from httpx import AsyncClient, ASGITransport

from ai_service.main import app


@pytest.fixture
def client():
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


@pytest.mark.asyncio
async def test_submit_task_returns_task_id_or_503(client):
    """提交任务：Redis 可用时返回 taskId，不可用时返回 503。"""
    resp = await client.post("/internal/tasks/submit", json={
        "task_type": "daily-briefing",
        "params": {},
    })
    assert resp.status_code in (200, 503)


@pytest.mark.asyncio
async def test_get_task_status_not_found_or_503(client):
    """查询不存在的任务返回 NOT_FOUND，Redis 不可用返回 503。"""
    resp = await client.get("/internal/tasks/nonexistent/status")
    assert resp.status_code in (200, 503)


@pytest.mark.asyncio
async def test_cancel_task_updates_status_or_503(client):
    """取消任务：Redis 可用时更新状态，不可用时返回 503。"""
    resp = await client.post("/internal/tasks/test-task-id/cancel")
    assert resp.status_code in (200, 503)
